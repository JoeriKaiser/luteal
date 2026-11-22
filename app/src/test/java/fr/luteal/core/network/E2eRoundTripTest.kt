package fr.luteal.core.network

import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.crypto.AccountCodeV2
import fr.luteal.core.network.crypto.RecordCrypto
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.sync.AccountUpgradeManager
import fr.luteal.core.network.sync.AccountUpgradeOutcome
import fr.luteal.core.network.sync.CycleSyncEngine
import fr.luteal.core.network.sync.FakeBiomarkerDao
import fr.luteal.core.network.sync.FakeCycleRepository
import fr.luteal.core.network.sync.FakeDailyEntryDao
import fr.luteal.core.network.sync.FakeCursorStore
import fr.luteal.core.network.sync.FakeSymptomDao
import fr.luteal.core.network.sync.FakeSyncStateDao
import fr.luteal.core.network.sync.FolicularApiClientFactory
import fr.luteal.core.network.sync.SyncLock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end trial against a REAL folicular server.
 *
 * Skipped unless a server URL is supplied:
 *
 *   ./gradlew testDebugUnitTest --tests '*E2eRoundTripTest' \
 *       -Dfolicular.e2e.url=http://127.0.0.1:8099
 */
class E2eRoundTripTest {

    private val baseUrl: String? = System.getProperty("folicular.e2e.url")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private class MutableCredentialStore : SyncCredentialStore {
        private var credentials: SyncCredentials? = null
        private var pending: String? = null
        override fun load() = credentials
        override fun save(credentials: SyncCredentials) {
            this.credentials = credentials
        }
        override fun clear() {
            credentials = null
            pending = null
        }
        override fun savePendingUpgradeCode(code: String) {
            pending = code
        }
        override fun loadPendingUpgradeCode(): String? = pending
        override fun clearPendingUpgradeCode() {
            pending = null
        }
    }

    private fun cycle(id: UUID, rev: UUID, start: LocalDate, notes: String): CycleData {
        val ts = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)
        return CycleData(
            id = id,
            clientRev = rev,
            createdAt = ts,
            updatedAt = ts,
            deletedAt = null,
            startDate = start,
            endDate = null,
            lengthDays = null,
            bleedingDays = 4,
            certainty = Certainty.RECORDED,
            source = RecordSource.MANUAL,
            notes = notes
        )
    }

    @Test
    fun `legacy v1 record survives real register, seal, push, pull and decrypt`() = runBlocking {
        assumeTrue("set -Dfolicular.e2e.url to run", baseUrl != null)

        val client = OkHttpFolicularApiClient(baseUrl!!, http)
        val store = MutableCredentialStore()
        val sealer = RecordSealer(store)

        val registration = client.register("e2e-trial-v1")
        val v1Code = registration.account.code!!
        store.save(
            SyncCredentials(
                accountId = registration.account.id.toString(),
                accountCode = v1Code,
                deviceToken = registration.device.token,
                authVersion = AuthVersion.V1
            )
        )
        val token = registration.device.token
        assertTrue(v1Code.startsWith("LTL-"))

        val id = UUID.randomUUID()
        val rev = UUID.randomUUID()
        val secretNote = "note privee e2e v1 ${UUID.randomUUID()}"
        val change = cycle(id, rev, LocalDate.of(2026, 6, 30), secretNote).toPushChange(sealer)

        assertNotNull(change.ciphertext)
        assertTrue(!change.ciphertext!!.contains("note privee"))

        val pushed = client.syncPush(token, listOf(change))
        assertEquals(0, pushed.rejected.size)
        assertEquals(1, pushed.applied.size)

        val pulled = client.syncPull(token, since = 0L)
        val change0 = pulled.changes.firstOrNull { it.entityId == id }
        assertNotNull(change0)
        val opened = change0!!.openPayload(sealer)!!.toCycleData()
        assertEquals(secretNote, opened.notes)
    }

    @Test
    fun `operator-blind v2 register, 0x02 push, pull, and second-device recovery round trip`() = runBlocking {
        assumeTrue("set -Dfolicular.e2e.url to run", baseUrl != null)

        val client = OkHttpFolicularApiClient(baseUrl!!, http)
        val recoveryCode = AccountCodeV2.generate()
        val authKeyHex = RecordCrypto.deriveV2AuthKeyHex(recoveryCode)

        // Device A registers without ever sending recoveryCode to the server.
        val reg = client.registerV2(authKeyHex, "e2e-v2-device-a")
        assertEquals(AuthVersion.V2, reg.authVersion)

        val storeA = MutableCredentialStore()
        storeA.save(SyncCredentials(reg.accountId, recoveryCode, reg.deviceToken, AuthVersion.V2))
        val sealerA = RecordSealer(storeA)

        val id = UUID.randomUUID()
        val secretNote = "note privee v2 ${UUID.randomUUID()}"
        val change = cycle(id, UUID.randomUUID(), LocalDate.of(2026, 7, 10), secretNote).toPushChange(sealerA)
        val rawBytes = Base64.getDecoder().decode(change.ciphertext!!)
        assertEquals(RecordCrypto.VERSION_V2, rawBytes[0])

        val pushed = client.syncPush(reg.deviceToken, listOf(change))
        assertEquals("server rejected v2 record: ${pushed.rejected}", 0, pushed.rejected.size)
        assertEquals(1, pushed.applied.size)

        // Device B recovers using only recoveryCode -> derives K_auth -> addDeviceV2.
        val recovered = client.addDeviceV2(RecordCrypto.deriveV2AuthKeyHex(recoveryCode), "e2e-v2-device-b")
        assertEquals(reg.accountId, recovered.accountId)
        assertEquals(AuthVersion.V2, recovered.authVersion)

        val storeB = MutableCredentialStore()
        storeB.save(SyncCredentials(recovered.accountId, recoveryCode, recovered.deviceToken, AuthVersion.V2))
        val sealerB = RecordSealer(storeB)

        val pulled = client.syncPull(recovered.deviceToken, since = 0L)
        val pulledChange = pulled.changes.firstOrNull { it.entityId == id }
        assertNotNull("recovered v2 device could not pull record", pulledChange)
        val opened = pulledChange!!.openPayload(sealerB)!!.toCycleData()
        assertEquals(secretNote, opened.notes)
        assertEquals(LocalDate.of(2026, 7, 10), opened.startDate)
    }

    @Test
    fun `live v1 account upgrades in place to v2 via AccountUpgradeManager and preserves data`() = runBlocking {
        assumeTrue("set -Dfolicular.e2e.url to run", baseUrl != null)

        val client = OkHttpFolicularApiClient(baseUrl!!, http)
        val reg = client.register("e2e-upgrade-primary")
        val accountId = reg.account.id.toString()
        val v1Code = reg.account.code!!

        // Attach a second v1 device to verify it gets revoked on upgrade.
        val secV1 = client.addDevice(v1Code, "e2e-upgrade-secondary")

        val store = MutableCredentialStore()
        store.save(SyncCredentials(accountId, v1Code, reg.device.token, AuthVersion.V1))
        val sealer = RecordSealer(store)

        val id = UUID.randomUUID()
        val secretNote = "note migree v1 vers v2 ${UUID.randomUUID()}"
        val v1Change = cycle(id, UUID.randomUUID(), LocalDate.of(2026, 4, 15), secretNote).toPushChange(sealer)
        val pushed = client.syncPush(reg.device.token, listOf(v1Change))
        assertEquals(1, pushed.applied.size)

        val cursorStore = FakeCursorStore(
            cursor = pushed.cursor,
            baseUrl = baseUrl,
            datasetAccountId = accountId,
            boundBaseUrl = baseUrl
        )
        val lock = SyncLock()
        val factory = FolicularApiClientFactory { client }
        val engine = CycleSyncEngine(
            cycleRepository = FakeCycleRepository(),
            syncStateDao = FakeSyncStateDao(),
            dailyEntryDao = FakeDailyEntryDao(),
            symptomDao = FakeSymptomDao(),
            biomarkerDao = FakeBiomarkerDao(),
            credentialStore = store,
            apiClientFactory = factory,
            cursorStore = cursorStore,
            recordSealer = sealer,
            syncLock = lock
        )
        val upgradeManager = AccountUpgradeManager(
            syncEngine = engine,
            credentialStore = store,
            apiClientFactory = factory,
            cursorStore = cursorStore,
            recordSealer = sealer,
            syncLock = lock
        )

        val prepared = upgradeManager.prepareUpgrade()
        assertEquals(1, prepared.otherActiveDeviceCount)

        val outcome = upgradeManager.executeUpgrade(prepared.newRecoveryCode)
        assertEquals(AccountUpgradeOutcome.Success(recordsMigrated = 1, revokedDevices = 1L), outcome)

        // Old secondary device token must now get 401.
        val secErr = runCatching { client.getMe(secV1.deviceToken) }.exceptionOrNull()
        assertTrue(secErr is FolicularApiException && secErr.status == 401)

        // New device recovers with the upgraded LTL2 recovery code and decrypts the migrated record.
        val newDev = client.addDeviceV2(
            RecordCrypto.deriveV2AuthKeyHex(prepared.newRecoveryCode),
            "e2e-upgrade-new-secondary"
        )
        assertEquals(accountId, newDev.accountId)
        assertEquals(AuthVersion.V2, newDev.authVersion)

        val newStore = MutableCredentialStore().apply {
            save(SyncCredentials(accountId, prepared.newRecoveryCode, newDev.deviceToken, AuthVersion.V2))
        }
        val newSealer = RecordSealer(newStore)
        val pulled = client.syncPull(newDev.deviceToken, since = 0L)
        val migrated = pulled.changes.firstOrNull { it.entityId == id }
        assertNotNull("migrated record missing from v2 pull", migrated)
        assertEquals(RecordCrypto.VERSION_V2, Base64.getDecoder().decode(migrated!!.ciphertext!!)[0])
        assertEquals(secretNote, migrated.openPayload(newSealer)!!.toCycleData().notes)
    }
}

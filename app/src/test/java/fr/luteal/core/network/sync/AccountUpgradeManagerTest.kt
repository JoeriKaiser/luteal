package fr.luteal.core.network.sync

import fr.luteal.core.network.AccountMeResult
import fr.luteal.core.network.AccountUpgradeResultWire
import fr.luteal.core.network.ExportedRecordWire
import fr.luteal.core.network.FolicularApiException
import fr.luteal.core.network.PullResultWire
import fr.luteal.core.network.RemoteDevice
import fr.luteal.core.network.SealedExportResult
import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.crypto.AccountCodeV2
import fr.luteal.core.network.crypto.RecordCrypto
import fr.luteal.core.network.crypto.RecordSealer
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUpgradeManagerTest {

    private val accountId = "019832e0-6c14-7000-8000-0000000000aa"
    private val v1Code = "LTL-8K3FQ-Z2WNT-7HJMC-4XRDB"
    private val v1Creds = SyncCredentials(accountId, v1Code, "ltok_primary", AuthVersion.V1)
    private val newV2Code = AccountCodeV2.encode(ByteArray(16) { (it + 11).toByte() })

    private fun buildSubject(
        creds: FakeCredentialStore,
        cursor: FakeCursorStore,
        api: FakeApiClient
    ): Pair<AccountUpgradeManager, RecordSealer> {
        val lock = SyncLock()
        val sealer = RecordSealer(creds)
        val engine = CycleSyncEngine(
            cycleRepository = FakeCycleRepository(),
            syncStateDao = FakeSyncStateDao(),
            dailyEntryDao = FakeDailyEntryDao(),
            symptomDao = FakeSymptomDao(),
            biomarkerDao = FakeBiomarkerDao(),
            credentialStore = creds,
            apiClientFactory = FolicularApiClientFactory { api },
            cursorStore = cursor,
            recordSealer = sealer,
            syncLock = lock
        )
        val manager = AccountUpgradeManager(
            syncEngine = engine,
            credentialStore = creds,
            apiClientFactory = FolicularApiClientFactory { api },
            cursorStore = cursor,
            recordSealer = sealer,
            syncLock = lock
        )
        return manager to sealer
    }

    @Test
    fun `happy path re-seals exported v1 records into v2 and promotes credentials`() = runTest {
        val creds = FakeCredentialStore(v1Creds)
        val cursor = FakeCursorStore(cursor = 12L, datasetAccountId = accountId, boundBaseUrl = "http://test.local:8080")
        val api = FakeApiClient()
        val (manager, sealer) = buildSubject(creds, cursor, api)

        val payload = buildJsonObject { put("notes", JsonPrimitive("secret observation")) }
        val v1Cipher = sealer.seal("cycle", "019832e0-6c14-7000-8000-000000000001", "019832e0-6c14-7000-8000-000000000002", payload)

        api.remoteDevices = listOf(
            RemoteDevice("d1", "Phone", revoked = false, current = true),
            RemoteDevice("d2", "Tablet", revoked = false, current = false),
            RemoteDevice("d3", "Old", revoked = true, current = false)
        )
        api.pullResults += PullResultWire(emptyList(), 12L, false)
        api.pullResults += PullResultWire(emptyList(), 12L, false)
        api.exportResult = SealedExportResult(
            accountId = accountId,
            authVersion = AuthVersion.V1,
            settingsCiphertext = null,
            records = listOf(
                ExportedRecordWire(
                    entityId = "019832e0-6c14-7000-8000-000000000001",
                    entityType = "cycle",
                    clientRev = "019832e0-6c14-7000-8000-000000000002",
                    deleted = false,
                    updatedAt = "2026-07-01T08:00:00Z",
                    ciphertext = v1Cipher
                )
            )
        )
        api.upgradeHandler = { req ->
            // Write-ahead code MUST be persisted in Keystore before HTTP request fires.
            assertEquals(newV2Code, creds.loadPendingUpgradeCode())
            assertEquals(12L, req.expectedCursor)
            assertEquals(RecordCrypto.deriveV2AuthKeyHex(newV2Code), req.authKey)
            assertEquals(1, req.records.size)
            val rawBytes = Base64.getDecoder().decode(req.records[0].ciphertext!!)
            assertEquals(RecordCrypto.VERSION_V2, rawBytes[0])
            AccountUpgradeResultWire(accountId, 2, 25L, 1, 1L)
        }

        val prepared = manager.prepareUpgrade()
        assertEquals(1, prepared.otherActiveDeviceCount)
        assertTrue(prepared.newRecoveryCode.startsWith("LTL2-"))

        val outcome = manager.executeUpgrade(newV2Code)
        assertEquals(AccountUpgradeOutcome.Success(recordsMigrated = 1, revokedDevices = 1L), outcome)

        // Credentials promoted to v2, pending cleared, cursor updated, sealer opens v2 ciphertext.
        val updated = creds.load()!!
        assertEquals(AuthVersion.V2, updated.authVersion)
        assertEquals(newV2Code, updated.accountCode)
        assertNull(creds.loadPendingUpgradeCode())
        assertEquals(25L, cursor.getCursor())
        val v2CipherSent = api.upgradeRequests.single().records.single().ciphertext!!
        assertEquals(payload, sealer.open("cycle", "019832e0-6c14-7000-8000-000000000001", "019832e0-6c14-7000-8000-000000000002", v2CipherSent))
    }

    @Test
    fun `409 conflict clears pending code and stays safely on v1`() = runTest {
        val creds = FakeCredentialStore(v1Creds)
        val cursor = FakeCursorStore(cursor = 5L, datasetAccountId = accountId, boundBaseUrl = "http://test.local:8080")
        val api = FakeApiClient().apply {
            pullResults += PullResultWire(emptyList(), 5L, false)
            exportResult = SealedExportResult(accountId, AuthVersion.V1, null, emptyList())
            upgradeHandler = { throw FolicularApiException(409, "about:blank", "stale cursor") }
        }
        val (manager, _) = buildSubject(creds, cursor, api)

        val outcome = manager.executeUpgrade(newV2Code)
        assertEquals(AccountUpgradeOutcome.ConcurrentChangeConflict, outcome)
        assertEquals(AuthVersion.V1, creds.load()!!.authVersion)
        assertNull(creds.loadPendingUpgradeCode())
    }

    @Test
    fun `network drop after server commit reconciles cleanly via GET me`() = runTest {
        val creds = FakeCredentialStore(v1Creds)
        val cursor = FakeCursorStore(cursor = 10L, datasetAccountId = accountId, boundBaseUrl = "http://test.local:8080")
        val api = FakeApiClient().apply {
            pullResults += PullResultWire(emptyList(), 10L, false)
            exportResult = SealedExportResult(accountId, AuthVersion.V1, null, emptyList())
            upgradeHandler = {
                // Server committed, then TCP connection dropped before 200 OK reached client
                meResult = AccountMeResult(accountId, AuthVersion.V2, "active")
                throw IOException("connection reset")
            }
        }
        val (manager, _) = buildSubject(creds, cursor, api)

        val outcome = manager.executeUpgrade(newV2Code)
        assertTrue(outcome is AccountUpgradeOutcome.Success)
        assertEquals(AuthVersion.V2, creds.load()!!.authVersion)
        assertEquals(newV2Code, creds.load()!!.accountCode)
        assertNull(creds.loadPendingUpgradeCode())
        assertEquals(0L, cursor.getCursor())
    }

    @Test
    fun `crash before server commit rolls back pending code on next reconcile`() = runTest {
        val creds = FakeCredentialStore(v1Creds).apply {
            savePendingUpgradeCode(newV2Code)
        }
        val cursor = FakeCursorStore(cursor = 10L, datasetAccountId = accountId, boundBaseUrl = "http://test.local:8080")
        val api = FakeApiClient().apply {
            meResult = AccountMeResult(accountId, AuthVersion.V1, "active")
        }
        val (manager, _) = buildSubject(creds, cursor, api)

        val promoted = manager.reconcilePendingUpgradeIfNeeded()
        assertEquals(false, promoted)
        assertEquals(AuthVersion.V1, creds.load()!!.authVersion)
        assertEquals(v1Code, creds.load()!!.accountCode)
        assertNull(creds.loadPendingUpgradeCode())
    }
}

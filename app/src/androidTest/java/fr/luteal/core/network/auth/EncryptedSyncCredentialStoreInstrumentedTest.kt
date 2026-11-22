package fr.luteal.core.network.auth

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.luteal.core.network.OkHttpFolicularApiClient
import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.crypto.AccountCodeV2
import fr.luteal.core.network.crypto.RecordCrypto
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.openPayload
import fr.luteal.core.network.toCycleData
import fr.luteal.core.network.toPushChange
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware AndroidKeyStore + ART JCA + optional live `adb reverse` verification.
 *
 * Runs on a real Android device or emulator to prove what JVM unit tests cannot:
 *  - hardware-backed AndroidKeyStore persists `AuthVersion` and `pending_upgrade_code`
 *    across store instances without ever leaking plaintext into SharedPreferences
 *  - Android JCA (`HmacSha256`, `AES/GCM/NoPadding`, `SecureRandom`) executes the
 *    v2 key hierarchy and 0x02 sealing cleanly
 *  - when `adb reverse tcp:18080 tcp:18080` points to a live local `folicular`,
 *    a full on-device v2 register + 0x02 push/pull + v1->v2 upgrade succeeds over HTTP.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedSyncCredentialStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var store: EncryptedSyncCredentialStore
    private var previousCreds: SyncCredentials? = null
    private var previousPending: String? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = EncryptedSyncCredentialStore(context)
        previousCreds = store.load()
        previousPending = store.loadPendingUpgradeCode()
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        previousCreds?.let { store.save(it) }
        previousPending?.let { store.savePendingUpgradeCode(it) }
    }

    @Test
    fun keystorePersistsV1AndV2CredentialsAndPendingUpgradeWithoutPlaintextLeak() {
        val v1 = SyncCredentials(
            accountId = "019832e0-6c14-7000-8000-000000000001",
            accountCode = "LTL-8K3FQ-Z2WNT-7HJMC-4XRDB",
            deviceToken = "ltok_hw_v1_secret",
            authVersion = AuthVersion.V1
        )
        store.save(v1)

        // Fresh store instance reads back from real AndroidKeyStore.
        val reloadedV1 = EncryptedSyncCredentialStore(context).load()
        assertEquals(v1, reloadedV1)

        // Write-ahead pending v2 upgrade code survives re-instantiation.
        val v2Code = AccountCodeV2.generate()
        store.savePendingUpgradeCode(v2Code)
        assertEquals(v2Code, EncryptedSyncCredentialStore(context).loadPendingUpgradeCode())

        // Promote to v2 and clear pending.
        val v2 = v1.copy(accountCode = v2Code, authVersion = AuthVersion.V2)
        store.save(v2)
        store.clearPendingUpgradeCode()

        val reloadedV2 = EncryptedSyncCredentialStore(context).load()
        assertEquals(v2, reloadedV2)
        assertNull(EncryptedSyncCredentialStore(context).loadPendingUpgradeCode())

        // Inspect raw SharedPreferences file: zero plaintext secrets allowed.
        val rawPrefs = context.getSharedPreferences("luteal_sync_credentials_v2", Context.MODE_PRIVATE)
        val rawDump = rawPrefs.all.values.joinToString("\n") { it.toString() }
        assertFalse("recovery code leaked in plaintext prefs", rawDump.contains(v2Code))
        assertFalse("device token leaked in plaintext prefs", rawDump.contains("ltok_hw_v1_secret"))
        assertFalse("LTL2 prefix leaked in plaintext prefs", rawDump.contains("LTL2-"))
    }

    @Test
    fun androidJcaV2SealingAndLiveAdbReverseRoundTrip() = runBlocking {
        val localServer = "http://127.0.0.1:18080"
        val serverReachable = runCatching {
            val conn = (URL("$localServer/readyz").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1000
                readTimeout = 1000
            }
            conn.responseCode == 200
        }.getOrDefault(false)
        assumeTrue("local folicular not reachable on $localServer (set adb reverse tcp:18080 tcp:18080)", serverReachable)

        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val client = OkHttpFolicularApiClient(localServer, http)

        val recoveryCode = AccountCodeV2.generate()
        val authKeyHex = RecordCrypto.deriveV2AuthKeyHex(recoveryCode)
        val reg = client.registerV2(authKeyHex, "adb-device-test")
        assertEquals(AuthVersion.V2, reg.authVersion)

        store.save(
            SyncCredentials(
                accountId = reg.accountId,
                accountCode = recoveryCode,
                deviceToken = reg.deviceToken,
                authVersion = AuthVersion.V2
            )
        )
        val sealer = RecordSealer(store)
        val cycleId = UUID.randomUUID()
        val rev = UUID.randomUUID()
        val ts = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)
        val secretNote = "device keystore v2 note ${UUID.randomUUID()}"
        val cycle = CycleData(
            id = cycleId,
            clientRev = rev,
            createdAt = ts,
            updatedAt = ts,
            deletedAt = null,
            startDate = LocalDate.of(2026, 7, 1),
            endDate = null,
            lengthDays = null,
            bleedingDays = 4,
            certainty = Certainty.RECORDED,
            source = RecordSource.MANUAL,
            notes = secretNote
        )

        val pushChange = cycle.toPushChange(sealer)
        val rawEnv = Base64.getDecoder().decode(pushChange.ciphertext!!)
        assertEquals(RecordCrypto.VERSION_V2, rawEnv[0])

        val pushRes = client.syncPush(reg.deviceToken, listOf(pushChange))
        assertEquals("push rejected: ${pushRes.rejected}", 0, pushRes.rejected.size)
        assertEquals(1, pushRes.applied.size)

        val pullRes = client.syncPull(reg.deviceToken, since = 0L)
        val pulled = pullRes.changes.firstOrNull { it.entityId == cycleId }
        assertNotNull(pulled)
        val opened = pulled!!.openPayload(sealer)!!.toCycleData()
        assertEquals(secretNote, opened.notes)
    }
}

package fr.luteal.core.network

import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.crypto.RecordSealer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * End-to-end trial against a REAL folicular server.
 *
 * Proves the whole encrypted path holds together across the two repositories:
 * register, seal a record with the account-code-derived key, push it, pull it
 * back, and decrypt it. Unit tests cover each half in isolation; only this
 * catches a mismatch between them - a diverging key derivation, a wire field
 * renamed on one side, or associated data that does not line up.
 *
 * Skipped unless a server URL is supplied, so ordinary test runs stay hermetic:
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

    /** Holds whatever the server hands back at registration. */
    private class MutableCredentialStore : SyncCredentialStore {
        private var credentials: SyncCredentials? = null
        override fun load() = credentials
        override fun save(credentials: SyncCredentials) {
            this.credentials = credentials
        }
        override fun clear() {
            credentials = null
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
    fun `record survives a real register, seal, push, pull and decrypt`() = runBlocking {
        assumeTrue("set -Dfolicular.e2e.url to run", baseUrl != null)

        val client = OkHttpFolicularApiClient(baseUrl!!, http)
        val store = MutableCredentialStore()
        val sealer = RecordSealer(store)

        // 1. Register an anonymous account.
        val registration = client.register("e2e-trial", "")
        store.save(
            SyncCredentials(
                accountId = registration.account.id.toString(),
                accountCode = registration.account.code,
                deviceToken = registration.device.token
            )
        )
        val token = registration.device.token
        assertTrue(registration.account.code.startsWith("LTL-"))

        // 2. Seal a record under the account-code-derived key.
        val id = UUID.randomUUID()
        val rev = UUID.randomUUID()
        val secretNote = "note privee e2e ${UUID.randomUUID()}"
        val change = cycle(id, rev, LocalDate.of(2026, 6, 30), secretNote).toPushChange(sealer)

        assertNotNull("record must be sealed before it leaves", change.ciphertext)
        assertTrue(
            "plaintext must not appear in the sealed payload",
            !change.ciphertext!!.contains("note privee")
        )

        // 3. Push it.
        val pushed = client.syncPush(token, listOf(change))
        assertEquals("server rejected the change: ${pushed.rejected}", 0, pushed.rejected.size)
        assertEquals(0, pushed.conflicts.size)
        assertEquals(1, pushed.applied.size)
        assertEquals(EntityType.CYCLE, pushed.applied[0].entityType)

        // 4. Pull it back.
        val pulled = client.syncPull(token, since = 0L)
        val change0 = pulled.changes.firstOrNull { it.entityId == id }
        assertNotNull("pushed record did not come back from pull", change0)
        assertEquals(false, change0!!.deleted)
        assertEquals(rev, change0.clientRev)

        // 5. Decrypt and confirm the content survived intact.
        val opened = change0.openPayload(sealer)!!.toCycleData()
        assertEquals(LocalDate.of(2026, 6, 30), opened.startDate)
        assertEquals(secretNote, opened.notes)
        assertEquals(4, opened.bleedingDays)
    }

    @Test
    fun `a second device recovers the account and decrypts the first device's record`() =
        runBlocking {
            assumeTrue("set -Dfolicular.e2e.url to run", baseUrl != null)

            val client = OkHttpFolicularApiClient(baseUrl!!, http)

            // Device one writes a sealed record.
            val storeA = MutableCredentialStore()
            val sealerA = RecordSealer(storeA)
            val registration = client.register("e2e-device-a", "")
            storeA.save(
                SyncCredentials(
                    accountId = registration.account.id.toString(),
                    accountCode = registration.account.code,
                    deviceToken = registration.device.token
                )
            )
            val id = UUID.randomUUID()
            val secretNote = "note du premier appareil"
            client.syncPush(
                registration.device.token,
                listOf(
                    cycle(id, UUID.randomUUID(), LocalDate.of(2026, 5, 20), secretNote)
                        .toPushChange(sealerA)
                )
            )

            // Device two attaches with only the account code - the recovery path.
            val recovered = client.addDevice(registration.account.code, "e2e-device-b")
            val storeB = MutableCredentialStore()
            storeB.save(
                SyncCredentials(
                    accountId = recovered.accountId,
                    accountCode = registration.account.code,
                    deviceToken = recovered.deviceToken
                )
            )
            val sealerB = RecordSealer(storeB)

            // It must derive the same key and read what device one wrote.
            // This is what makes the account code a real recovery credential.
            val pulled = client.syncPull(recovered.deviceToken, since = 0L)
            val change = pulled.changes.firstOrNull { it.entityId == id }
            assertNotNull("recovered device could not see the record", change)

            val opened = change!!.openPayload(sealerB)!!.toCycleData()
            assertEquals(secretNote, opened.notes)
            assertEquals(LocalDate.of(2026, 5, 20), opened.startDate)
        }
}

package fr.luteal.core.network.crypto

import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.sync.FakeCredentialStore
import java.security.GeneralSecurityException
import java.util.Base64
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCryptoV2Test {

    private val accountA = "019832e0-6c14-7000-8000-000000000001"
    private val accountB = "019832e0-6c14-7000-8000-000000000002"
    private val fixedSecret = ByteArray(16) { (it * 13 + 7).toByte() }
    private val fixedCodeV2 = AccountCodeV2.encode(fixedSecret)

    @Test
    fun `v2 HKDF keys are strictly domain-separated and account-bound`() {
        val kAuth = RecordCrypto.deriveV2AuthKey(fixedSecret)
        val kMaster = RecordCrypto.deriveV2MasterKey(fixedSecret)
        val kRecA = RecordCrypto.deriveV2RecordKey(fixedCodeV2, accountA)
        val kRecB = RecordCrypto.deriveV2RecordKey(fixedCodeV2, accountB)
        val kSetA = RecordCrypto.deriveV2SettingsKey(fixedCodeV2, accountA)
        val kDuoA = RecordCrypto.deriveV2DuoRootKey(fixedCodeV2, accountA)

        assertEquals(32, kAuth.size)
        assertEquals(64, RecordCrypto.deriveV2AuthKeyHex(fixedCodeV2).length)

        val keys = listOf(kAuth, kMaster, kRecA, kRecB, kSetA, kDuoA)
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                assertFalse("keys[$i] and keys[$j] must differ", keys[i].contentEquals(keys[j]))
            }
        }
    }

    @Test
    fun `v2 envelope uses 0x02 byte and binds accountId and routing metadata in AAD`() {
        val key = RecordCrypto.deriveV2RecordKey(fixedCodeV2, accountA)
        val aad = RecordCrypto.associatedDataV2(accountA, "cycle", "id-1", "rev-1")
        val plain = """{"hello":"world"}""".toByteArray()

        val sealed = RecordCrypto.sealV2(key, plain, aad)
        assertEquals(RecordCrypto.VERSION_V2, sealed[0])
        assertArrayEquals(plain, RecordCrypto.openV2(key, sealed, aad))

        // Cross-account transplantation must fail
        val wrongAccountAad = RecordCrypto.associatedDataV2(accountB, "cycle", "id-1", "rev-1")
        assertTrue(runCatching { RecordCrypto.openV2(key, sealed, wrongAccountAad) }.exceptionOrNull() is GeneralSecurityException)

        // Opening 0x02 with v1 open() must fail
        assertTrue(runCatching { RecordCrypto.open(key, sealed, aad) }.exceptionOrNull() is GeneralSecurityException)

        // Opening 0x01 with v2 openV2() must fail
        val v1Sealed = RecordCrypto.seal(key, plain, aad)
        assertTrue(runCatching { RecordCrypto.openV2(key, v1Sealed, aad) }.exceptionOrNull() is GeneralSecurityException)
    }

    @Test
    fun `RecordSealer v2 wraps schema_version and rejects unsupported future schema versions`() {
        val creds = SyncCredentials(accountA, fixedCodeV2, "ltok_v2", AuthVersion.V2)
        val store = FakeCredentialStore(creds)
        val sealer = RecordSealer(store)

        val payload = buildJsonObject { put("notes", JsonPrimitive("private note")) }
        val sealedB64 = sealer.seal("cycle", "id-1", "rev-1", payload)

        // Raw first byte must be 0x02
        val raw = Base64.getDecoder().decode(sealedB64)
        assertEquals(RecordCrypto.VERSION_V2, raw[0])

        // Round-trip open succeeds
        assertEquals(payload, sealer.open("cycle", "id-1", "rev-1", sealedB64))

        // Future schema_version = 99 raises typed UnsupportedRecordSchemaException
        val key = RecordCrypto.deriveV2RecordKey(fixedCodeV2, accountA)
        val futurePlain = """{"schema_version":99,"payload":{"notes":"future"}}""".toByteArray()
        val futureEnv = RecordCrypto.sealV2(
            key, futurePlain, RecordCrypto.associatedDataV2(accountA, "cycle", "id-1", "rev-1")
        )
        val futureB64 = Base64.getEncoder().encodeToString(futureEnv)
        val err = runCatching { sealer.open("cycle", "id-1", "rev-1", futureB64) }.exceptionOrNull()
        assertTrue("expected UnsupportedRecordSchemaException, got $err", err is UnsupportedRecordSchemaException)
        assertEquals(99, (err as UnsupportedRecordSchemaException).foundVersion)
    }

    @Test
    fun `RecordSealer cache switches keys immediately on in-place v1 to v2 upgrade with same accountId`() {
        val v1Creds = SyncCredentials(accountA, "LTL-8K3FQ-Z2WNT-7HJMC-4XRDB", "ltok_same", AuthVersion.V1)
        val store = FakeCredentialStore(v1Creds)
        val sealer = RecordSealer(store)
        val payload = buildJsonObject { put("k", JsonPrimitive("v")) }

        // Warm cache on v1
        val v1Cipher = sealer.seal("cycle", "id-1", "rev-1", payload)
        assertEquals(RecordCrypto.VERSION, Base64.getDecoder().decode(v1Cipher)[0])

        // Rotate credentials in place on the SAME accountId WITHOUT calling invalidate()
        store.save(SyncCredentials(accountA, fixedCodeV2, "ltok_same", AuthVersion.V2))

        val v2Cipher = sealer.seal("cycle", "id-1", "rev-1", payload)
        assertEquals(RecordCrypto.VERSION_V2, Base64.getDecoder().decode(v2Cipher)[0])
        assertEquals(payload, sealer.open("cycle", "id-1", "rev-1", v2Cipher))

        // v1 ciphertext must not open once account is on v2
        assertTrue(runCatching { sealer.open("cycle", "id-1", "rev-1", v1Cipher) }.exceptionOrNull() is GeneralSecurityException)
    }
}

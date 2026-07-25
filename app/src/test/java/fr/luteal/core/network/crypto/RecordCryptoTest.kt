package fr.luteal.core.network.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class RecordCryptoTest {

    private val accountCode = "LTL-A1B2C-D3E4F-G5H6J-K7M8N"
    private val accountId = "0192f3a4-5b6c-7d8e-9f01-234567890abc"

    private fun recordKey() =
        RecordCrypto.deriveRecordKey(RecordCrypto.deriveMasterKey(accountCode, accountId))

    private val aad = RecordCrypto.associatedData(
        entityType = "cycle",
        entityId = "0192f3a4-0000-7000-8000-000000000001",
        clientRev = "0192f3a4-0000-7000-8000-000000000002"
    )

    // --- Normalisation must match the server's auth.NormalizeCode ----------

    @Test
    fun `normalises account codes exactly as the server does`() {
        val canonical = "A1B2CD3E4FG5H6JK7M8N"
        assertEquals(canonical, RecordCrypto.normalizeAccountCode("LTL-A1B2C-D3E4F-G5H6J-K7M8N"))
        assertEquals(canonical, RecordCrypto.normalizeAccountCode("ltl-a1b2c-d3e4f-g5h6j-k7m8n"))
        assertEquals(canonical, RecordCrypto.normalizeAccountCode("  A1B2C D3E4F G5H6J K7M8N  "))
        assertEquals(canonical, RecordCrypto.normalizeAccountCode("A1B2CD3E4FG5H6JK7M8N"))
    }

    // --- Key hierarchy ------------------------------------------------------

    @Test
    fun `master key is deterministic for the same account`() {
        assertArrayEquals(
            RecordCrypto.deriveMasterKey(accountCode, accountId),
            RecordCrypto.deriveMasterKey(accountCode, accountId)
        )
    }

    @Test
    fun `master key is 32 bytes and differs per account and per code`() {
        val base = RecordCrypto.deriveMasterKey(accountCode, accountId)
        assertEquals(32, base.size)

        val otherAccount = RecordCrypto.deriveMasterKey(accountCode, "different-account-id")
        val otherCode = RecordCrypto.deriveMasterKey("LTL-ZZZZZ-ZZZZZ-ZZZZZ-ZZZZZ", accountId)

        assertNotEquals(base.toList(), otherAccount.toList())
        assertNotEquals(base.toList(), otherCode.toList())
    }

    @Test
    fun `record and duo keys are independent of each other and of the master key`() {
        val master = RecordCrypto.deriveMasterKey(accountCode, accountId)
        val record = RecordCrypto.deriveRecordKey(master)
        val duo = RecordCrypto.deriveDuoRootKey(master)

        assertNotEquals(master.toList(), record.toList())
        assertNotEquals(master.toList(), duo.toList())
        assertNotEquals(record.toList(), duo.toList())
    }

    @Test
    fun `stored auth hash does not reveal the master key`() {
        // The server stores SHA-256(normalized code). Confirm the master key is
        // not that value, so a database leak does not hand over decryption.
        val normalized = RecordCrypto.normalizeAccountCode(accountCode)
        val authHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
        val master = RecordCrypto.deriveMasterKey(accountCode, accountId)

        assertNotEquals(authHash.toList(), master.toList())
    }

    // --- Sealing ------------------------------------------------------------

    @Test
    fun `round trips record content`() {
        val key = recordKey()
        val plaintext = """{"start_date":"2026-07-20","notes":"note privee"}""".toByteArray()

        val sealed = RecordCrypto.seal(key, plaintext, aad)
        assertArrayEquals(plaintext, RecordCrypto.open(key, sealed, aad))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val key = recordKey()
        val plaintext = """{"notes":"douleur forte"}""".toByteArray()

        val sealed = RecordCrypto.seal(key, plaintext, aad)

        assertTrue(
            "Sealed record must not leak plaintext bytes",
            !String(sealed, Charsets.ISO_8859_1).contains("douleur")
        )
    }

    @Test
    fun `same plaintext seals differently each time`() {
        val key = recordKey()
        val plaintext = "identique".toByteArray()

        val first = RecordCrypto.seal(key, plaintext, aad)
        val second = RecordCrypto.seal(key, plaintext, aad)

        // Random nonce per record: identical content must not be linkable by
        // comparing ciphertexts server-side.
        assertNotEquals(first.toList(), second.toList())
        assertArrayEquals(plaintext, RecordCrypto.open(key, first, aad))
        assertArrayEquals(plaintext, RecordCrypto.open(key, second, aad))
    }

    @Test
    fun `envelope carries the version marker and a fresh nonce`() {
        val sealed = RecordCrypto.seal(recordKey(), "x".toByteArray(), aad)
        assertEquals(RecordCrypto.VERSION, sealed[0])
        // version + nonce + tag, at minimum.
        assertTrue(sealed.size >= 1 + 12 + 16)
    }

    // --- Tamper resistance --------------------------------------------------

    @Test
    fun `rejects a wrong key`() {
        val sealed = RecordCrypto.seal(recordKey(), "secret".toByteArray(), aad)
        val wrongKey = RecordCrypto.deriveRecordKey(
            RecordCrypto.deriveMasterKey("LTL-ZZZZZ-ZZZZZ-ZZZZZ-ZZZZZ", accountId)
        )

        assertThrows(GeneralSecurityException::class.java) {
            RecordCrypto.open(wrongKey, sealed, aad)
        }
    }

    @Test
    fun `rejects modified ciphertext`() {
        val key = recordKey()
        val sealed = RecordCrypto.seal(key, "secret".toByteArray(), aad)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            RecordCrypto.open(key, sealed, aad)
        }
    }

    @Test
    fun `rejects a payload moved to a different record`() {
        // The server must not be able to swap one record's payload onto
        // another entity id without detection.
        val key = recordKey()
        val sealed = RecordCrypto.seal(key, "secret".toByteArray(), aad)

        val otherAad = RecordCrypto.associatedData(
            entityType = "cycle",
            entityId = "0192f3a4-0000-7000-8000-00000000ffff",
            clientRev = "0192f3a4-0000-7000-8000-000000000002"
        )

        assertThrows(GeneralSecurityException::class.java) {
            RecordCrypto.open(key, sealed, otherAad)
        }
    }

    @Test
    fun `rejects a payload relabelled as a different entity type`() {
        val key = recordKey()
        val sealed = RecordCrypto.seal(key, "secret".toByteArray(), aad)

        val otherType = RecordCrypto.associatedData(
            entityType = "daily_entry",
            entityId = "0192f3a4-0000-7000-8000-000000000001",
            clientRev = "0192f3a4-0000-7000-8000-000000000002"
        )

        assertThrows(GeneralSecurityException::class.java) {
            RecordCrypto.open(key, sealed, otherType)
        }
    }

    @Test
    fun `rejects a truncated or unversioned envelope`() {
        val key = recordKey()

        assertThrows(GeneralSecurityException::class.java) {
            RecordCrypto.open(key, ByteArray(5), aad)
        }

        val sealed = RecordCrypto.seal(key, "secret".toByteArray(), aad)
        sealed[0] = 0x09
        assertThrows(GeneralSecurityException::class.java) {
            RecordCrypto.open(key, sealed, aad)
        }
    }

    @Test
    fun `handles empty and large payloads`() {
        val key = recordKey()

        assertArrayEquals(
            ByteArray(0),
            RecordCrypto.open(key, RecordCrypto.seal(key, ByteArray(0), aad), aad)
        )

        val large = ByteArray(64 * 1024) { (it % 251).toByte() }
        assertArrayEquals(large, RecordCrypto.open(key, RecordCrypto.seal(key, large, aad), aad))
    }
}

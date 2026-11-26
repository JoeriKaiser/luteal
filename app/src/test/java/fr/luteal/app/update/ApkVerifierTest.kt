package fr.luteal.app.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerifierTest {

    private val installed = ApkIdentity("fr.luteal.app", 18, setOf("key-a"))
    private val goodHash = "c".repeat(64)

    private fun verify(candidate: ApkIdentity?, expected: String? = goodHash, actual: String = goodHash) =
        ApkVerifier.verify(expected, actual, candidate, installed)

    @Test
    fun `sha256 matches the known test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkVerifier.sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ApkVerifier.sha256Hex(ByteArray(0))
        )
    }

    @Test
    fun `newer apk from the same package and key passes`() {
        assertEquals(ApkVerdict.OK, verify(ApkIdentity("fr.luteal.app", 19, setOf("key-a"))))
        assertEquals(
            ApkVerdict.OK,
            verify(ApkIdentity("fr.luteal.app", 19, setOf("key-a")), expected = goodHash.uppercase())
        )
    }

    @Test
    fun `each check fails on its own`() {
        val good = ApkIdentity("fr.luteal.app", 19, setOf("key-a"))
        assertEquals(ApkVerdict.MISSING_DIGEST, verify(good, expected = null))
        assertEquals(ApkVerdict.HASH_MISMATCH, verify(good, actual = "d".repeat(64)))
        assertEquals(ApkVerdict.UNREADABLE, verify(null))
        assertEquals(ApkVerdict.WRONG_PACKAGE, verify(good.copy(packageName = "fr.luteal.app.evil")))
        assertEquals(ApkVerdict.NOT_NEWER, verify(good.copy(versionCode = 18)))
        assertEquals(ApkVerdict.NOT_NEWER, verify(good.copy(versionCode = 17)))
        assertEquals(ApkVerdict.SIGNER_MISMATCH, verify(good.copy(signers = setOf("key-b"), signingHistory = setOf("key-b"))))
    }

    @Test
    fun `signers must be present on both sides`() {
        assertFalse(ApkVerifier.signersMatch(installed, ApkIdentity("fr.luteal.app", 19, emptySet())))
        assertFalse(ApkVerifier.signersMatch(installed.copy(signers = emptySet()), installed))
    }

    @Test
    fun `rotated key is accepted only when its lineage carries the installed key`() {
        val rotated = ApkIdentity("fr.luteal.app", 19, setOf("key-b"), signingHistory = setOf("key-a", "key-b"))
        assertTrue(ApkVerifier.signersMatch(installed, rotated))
        val unrelated = ApkIdentity("fr.luteal.app", 19, setOf("key-b"), signingHistory = setOf("key-c", "key-b"))
        assertFalse(ApkVerifier.signersMatch(installed, unrelated))
    }

    @Test
    fun `multiple signers must match exactly`() {
        val multi = ApkIdentity("fr.luteal.app", 18, setOf("key-a", "key-b"))
        assertTrue(ApkVerifier.signersMatch(multi, multi.copy(versionCode = 19)))
        assertFalse(ApkVerifier.signersMatch(multi, ApkIdentity("fr.luteal.app", 19, setOf("key-a"))))
        assertFalse(ApkVerifier.signersMatch(installed, ApkIdentity("fr.luteal.app", 19, setOf("key-a", "key-b"))))
    }
}

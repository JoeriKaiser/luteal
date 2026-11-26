package fr.luteal.app.update

import java.io.InputStream
import java.security.MessageDigest

data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    /** Lower-case SHA-256 of each signing certificate. */
    val signers: Set<String>,
    /** Includes earlier certificates when the key was rotated (APK Signature Scheme v3). */
    val signingHistory: Set<String> = signers
)

enum class ApkVerdict {
    OK,
    MISSING_DIGEST,
    HASH_MISMATCH,
    UNREADABLE,
    WRONG_PACKAGE,
    NOT_NEWER,
    SIGNER_MISMATCH
}

/** Android would reject a wrong signer anyway; checking first gives a clear message. */
object ApkVerifier {

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun verify(
        expectedSha256: String?,
        actualSha256: String,
        candidate: ApkIdentity?,
        installed: ApkIdentity
    ): ApkVerdict = when {
        expectedSha256 == null -> ApkVerdict.MISSING_DIGEST
        !expectedSha256.equals(actualSha256, ignoreCase = true) -> ApkVerdict.HASH_MISMATCH
        candidate == null -> ApkVerdict.UNREADABLE
        candidate.packageName != installed.packageName -> ApkVerdict.WRONG_PACKAGE
        candidate.versionCode <= installed.versionCode -> ApkVerdict.NOT_NEWER
        !signersMatch(installed, candidate) -> ApkVerdict.SIGNER_MISMATCH
        else -> ApkVerdict.OK
    }

    /** Same certificates, or a single installed one still in the candidate's rotation history. */
    fun signersMatch(installed: ApkIdentity, candidate: ApkIdentity): Boolean {
        if (installed.signers.isEmpty() || candidate.signers.isEmpty()) return false
        if (installed.signers == candidate.signers) return true
        return installed.signers.size == 1 &&
            candidate.signers.size == 1 &&
            candidate.signingHistory.containsAll(installed.signers)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

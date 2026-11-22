package fr.luteal.core.network.crypto

import fr.luteal.core.network.auth.AuthVersion
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Client-generated v2 recovery code (`LTL2-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`).
 *
 * Layout:
 *  - 16 random bytes (128 bits of entropy) generated on-device via [SecureRandom]
 *  - 12-bit checksum: top 12 bits of SHA-256("luteal/v2/code-checksum\u0000" || root_secret)
 *  - Total 140 bits encoded as 28 Crockford Base32 symbols in 7 groups of 4.
 *
 * The recovery code never leaves the device. The server receives only the
 * HKDF-derived `K_auth` key (and hashes it server-side before storing).
 */
object AccountCodeV2 {

    const val PREFIX = "LTL2-"
    const val ROOT_SECRET_BYTES = 16
    private const val CHECKSUM_BITS = 12
    private const val TOTAL_BITS = ROOT_SECRET_BYTES * 8 + CHECKSUM_BITS // 140
    private const val SYMBOL_COUNT = TOTAL_BITS / 5 // 28
    private const val GROUP_SIZE = 4

    private const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val CHECKSUM_DOMAIN = "luteal/v2/code-checksum\u0000".toByteArray(Charsets.UTF_8)

    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        CROCKFORD_ALPHABET.forEachIndexed { idx, ch ->
            this[ch.code] = idx
        }
        // Crockford human-transcription confusion mappings:
        this['O'.code] = 0
        this['I'.code] = 1
        this['L'.code] = 1
    }

    enum class ErrorReason {
        INVALID_FORMAT,
        CHECKSUM_MISMATCH
    }

    class InvalidAccountCodeException(
        val reason: ErrorReason,
        message: String
    ) : IllegalArgumentException(message)

    /** Generates a fresh 128-bit operator-blind v2 recovery code. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val secret = ByteArray(ROOT_SECRET_BYTES)
        try {
            random.nextBytes(secret)
            return encode(secret)
        } finally {
            secret.fill(0)
        }
    }

    /** Encodes a 16-byte root secret into the canonical `LTL2-...` display string. */
    fun encode(rootSecret: ByteArray): String {
        require(rootSecret.size == ROOT_SECRET_BYTES) {
            "v2 root secret must be $ROOT_SECRET_BYTES bytes"
        }
        val checksum = computeChecksum12(rootSecret)
        val symbols = CharArray(SYMBOL_COUNT)

        var bitPos = 0
        for (i in 0 until SYMBOL_COUNT) {
            var value = 0
            for (b in 0 until 5) {
                val bit = readBit(rootSecret, checksum, bitPos++)
                value = (value shl 1) or bit
            }
            symbols[i] = CROCKFORD_ALPHABET[value]
        }

        return buildString(PREFIX.length + SYMBOL_COUNT + (SYMBOL_COUNT / GROUP_SIZE) - 1) {
            append(PREFIX)
            for (i in symbols.indices) {
                if (i > 0 && i % GROUP_SIZE == 0) append('-')
                append(symbols[i])
            }
        }
    }

    /**
     * Parses, normalizes, and checksum-validates a v2 recovery code.
     *
     * Returns the 16-byte raw root secret; callers should zeroize it after
     * deriving keys.
     *
     * @throws InvalidAccountCodeException on malformed input or typo caught by the 12-bit checksum.
     */
    fun parseAndValidate(input: String): ByteArray {
        val cleaned = stripPrefixAndSeparators(input)
        if (cleaned.length != SYMBOL_COUNT) {
            throw InvalidAccountCodeException(
                ErrorReason.INVALID_FORMAT,
                "Expected $SYMBOL_COUNT symbols in v2 recovery code, got ${cleaned.length}"
            )
        }

        val secret = ByteArray(ROOT_SECRET_BYTES)
        var parsedChecksum = 0
        var bitPos = 0

        for (ch in cleaned) {
            val code = ch.code
            val sym = if (code in 0..127) DECODE_TABLE[code] else -1
            if (sym < 0) {
                secret.fill(0)
                throw InvalidAccountCodeException(
                    ErrorReason.INVALID_FORMAT,
                    "Invalid character in v2 recovery code"
                )
            }
            for (shift in 4 downTo 0) {
                val bit = (sym ushr shift) and 1
                if (bitPos < ROOT_SECRET_BYTES * 8) {
                    val byteIdx = bitPos ushr 3
                    val bitInByte = 7 - (bitPos and 7)
                    if (bit == 1) {
                        secret[byteIdx] = (secret[byteIdx].toInt() or (1 shl bitInByte)).toByte()
                    }
                } else {
                    parsedChecksum = (parsedChecksum shl 1) or bit
                }
                bitPos++
            }
        }

        val expectedChecksum = computeChecksum12(secret)
        if (parsedChecksum != expectedChecksum) {
            secret.fill(0)
            throw InvalidAccountCodeException(
                ErrorReason.CHECKSUM_MISMATCH,
                "Recovery code checksum mismatch"
            )
        }
        return secret
    }

    /** Validates and returns the canonical display form (`LTL2-XXXX-...`). */
    fun normalizeDisplay(input: String): String {
        val secret = parseAndValidate(input)
        return try {
            encode(secret)
        } finally {
            secret.fill(0)
        }
    }

    /** Returns true when [input] carries the `LTL2` version prefix. */
    fun isV2Code(input: String): Boolean {
        val trimmed = input.trim().uppercase()
        return trimmed.startsWith("LTL2-") || (trimmed.startsWith("LTL2") && trimmed.length > 4)
    }

    /** Detects whether a user-entered account code targets v2 or legacy v1. */
    fun detectVersion(input: String): AuthVersion =
        if (isV2Code(input)) AuthVersion.V2 else AuthVersion.V1

    private fun stripPrefixAndSeparators(input: String): String {
        var s = input.trim().uppercase()
        if (s.startsWith("LTL2-")) {
            s = s.removePrefix("LTL2-")
        } else if (s.startsWith("LTL2")) {
            s = s.removePrefix("LTL2")
        }
        return s.replace("-", "").replace(" ", "").replace("\t", "").replace("\n", "")
    }

    private fun computeChecksum12(rootSecret: ByteArray): Int {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(CHECKSUM_DOMAIN)
            update(rootSecret)
        }.digest()
        return ((digest[0].toInt() and 0xFF) shl 4) or ((digest[1].toInt() and 0xF0) ushr 4)
    }

    private fun readBit(secret: ByteArray, checksum12: Int, bitPos: Int): Int {
        return if (bitPos < ROOT_SECRET_BYTES * 8) {
            val byteIdx = bitPos ushr 3
            val bitInByte = 7 - (bitPos and 7)
            (secret[byteIdx].toInt() ushr bitInByte) and 1
        } else {
            val chkIdx = bitPos - ROOT_SECRET_BYTES * 8
            val shift = (CHECKSUM_BITS - 1) - chkIdx
            (checksum12 ushr shift) and 1
        }
    }
}

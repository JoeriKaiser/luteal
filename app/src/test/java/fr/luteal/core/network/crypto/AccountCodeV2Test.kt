package fr.luteal.core.network.crypto

import fr.luteal.core.network.auth.AuthVersion
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCodeV2Test {

    @Test
    fun `generated v2 code matches LTL2 7x4 Crockford format and round-trips`() {
        val code = AccountCodeV2.generate()
        assertTrue("expected LTL2- prefix, got $code", code.startsWith("LTL2-"))
        val segments = code.split("-")
        assertEquals(8, segments.size) // LTL2 + 7 groups of 4
        assertEquals("LTL2", segments[0])
        for (i in 1..7) {
            assertEquals(4, segments[i].length)
        }

        val secret = AccountCodeV2.parseAndValidate(code)
        assertEquals(16, secret.size)
        assertEquals(code, AccountCodeV2.encode(secret))
    }

    @Test
    fun `normalization handles lowercase whitespace missing dashes and Crockford confusion chars`() {
        val fixedSecret = ByteArray(16) { (it * 17 + 5).toByte() }
        val canonical = AccountCodeV2.encode(fixedSecret)

        // Replace 0->O, 1->I and 1->L, lowercase, remove dashes, add spaces.
        val body = canonical.removePrefix("LTL2-").replace("-", "")
        val confused = "  ltl2 " + body.replace('0', 'o').replace('1', 'l').lowercase() + "  "

        assertEquals(canonical, AccountCodeV2.normalizeDisplay(confused))
        assertArrayEquals(fixedSecret, AccountCodeV2.parseAndValidate(confused))
    }

    @Test
    fun `12-bit checksum catches single-character typo at every position`() {
        val fixedSecret = ByteArray(16) { (it + 1).toByte() }
        val canonical = AccountCodeV2.encode(fixedSecret)
        val rawBody = canonical.removePrefix("LTL2-").replace("-", "")

        for (pos in rawBody.indices) {
            val mutatedChar = if (rawBody[pos] == 'A') 'B' else 'A'
            val mutated = "LTL2-" + rawBody.substring(0, pos) + mutatedChar + rawBody.substring(pos + 1)
            val err = runCatching { AccountCodeV2.parseAndValidate(mutated) }.exceptionOrNull()
            assertTrue("expected InvalidAccountCodeException at pos $pos", err is AccountCodeV2.InvalidAccountCodeException)
            assertEquals(
                "expected CHECKSUM_MISMATCH at pos $pos",
                AccountCodeV2.ErrorReason.CHECKSUM_MISMATCH,
                (err as AccountCodeV2.InvalidAccountCodeException).reason
            )
        }
    }

    @Test
    fun `invalid length or invalid alphabet returns INVALID_FORMAT`() {
        val tooShort = runCatching { AccountCodeV2.parseAndValidate("LTL2-ABCD-EFGH") }.exceptionOrNull()
        assertTrue(tooShort is AccountCodeV2.InvalidAccountCodeException)
        assertEquals(
            AccountCodeV2.ErrorReason.INVALID_FORMAT,
            (tooShort as AccountCodeV2.InvalidAccountCodeException).reason
        )

        val badChar = runCatching {
            AccountCodeV2.parseAndValidate("LTL2-UUUU-UUUU-UUUU-UUUU-UUUU-UUUU-UUUU")
        }.exceptionOrNull()
        assertTrue(badChar is AccountCodeV2.InvalidAccountCodeException)
        assertEquals(
            AccountCodeV2.ErrorReason.INVALID_FORMAT,
            (badChar as AccountCodeV2.InvalidAccountCodeException).reason
        )
    }

    @Test
    fun `detectVersion distinguishes LTL2 from legacy LTL`() {
        assertEquals(AuthVersion.V2, AccountCodeV2.detectVersion(AccountCodeV2.generate(SecureRandom())))
        assertEquals(AuthVersion.V2, AccountCodeV2.detectVersion("ltl2-abcd"))
        assertEquals(AuthVersion.V1, AccountCodeV2.detectVersion("LTL-8K3FQ-Z2WNT-7HJMC-4XRDB"))
        assertEquals(AuthVersion.V1, AccountCodeV2.detectVersion("8K3FQZ2WNT7HJMC4XRDB"))
        assertFalse(AccountCodeV2.isV2Code("LTL-8K3FQ-Z2WNT-7HJMC-4XRDB"))
    }
}

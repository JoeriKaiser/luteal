package fr.luteal.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinEntryPolicyTest {
    @Test
    fun `four digit pin auto submits on the fourth digit`() {
        assertTrue(PinEntryPolicy.shouldAutoSubmit(4, 4))
        assertFalse(PinEntryPolicy.shouldAutoSubmit(3, 4))
    }

    @Test
    fun `six digit pin does not auto submit at four digits`() {
        assertFalse(PinEntryPolicy.shouldAutoSubmit(4, 6))
        assertTrue(PinEntryPolicy.shouldAutoSubmit(6, 6))
    }

    @Test
    fun `unknown length never auto submits and can confirm from four digits`() {
        assertFalse(PinEntryPolicy.shouldAutoSubmit(4, null))
        assertFalse(PinEntryPolicy.shouldAutoSubmit(8, null))
        assertTrue(PinEntryPolicy.canConfirm(4))
        assertTrue(PinEntryPolicy.canConfirm(8))
        assertFalse(PinEntryPolicy.canConfirm(3))
    }
}

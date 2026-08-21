package fr.luteal.core.data.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinCryptoManagerTest {

    private lateinit var secretStore: InMemoryPinSecretStore
    private lateinit var pinCryptoManager: PinCryptoManager

    @Before
    fun setup() {
        secretStore = InMemoryPinSecretStore()
        pinCryptoManager = PinCryptoManager(secretStore)
    }

    @Test
    fun setAndVerifyPinSuccessfully() = runTest {
        assertFalse(pinCryptoManager.hasPinConfigured())

        pinCryptoManager.setPin("1234")
        assertTrue(pinCryptoManager.hasPinConfigured())
        assertEquals(4, pinCryptoManager.pinLength())

        assertTrue(pinCryptoManager.verifyPin("1234"))
        assertFalse(pinCryptoManager.verifyPin("0000"))
        assertFalse(pinCryptoManager.verifyPin("12345"))
    }

    @Test
    fun clearPinRemovesStoredSecrets() = runTest {
        pinCryptoManager.setPin("5678")
        assertTrue(pinCryptoManager.hasPinConfigured())

        pinCryptoManager.clearPin()
        assertFalse(pinCryptoManager.hasPinConfigured())
        assertFalse(pinCryptoManager.verifyPin("5678"))
    }

    @Test
    fun changingPinUpdatesValidVerification() = runTest {
        pinCryptoManager.setPin("1111")
        assertTrue(pinCryptoManager.verifyPin("1111"))

        pinCryptoManager.setPin("2222")
        assertFalse(pinCryptoManager.verifyPin("1111"))
        assertTrue(pinCryptoManager.verifyPin("2222"))
    }

    @Test
    fun sixDigitPinStoresLengthWithoutAcceptingPrefix() = runTest {
        pinCryptoManager.setPin("654321")
        assertEquals(6, pinCryptoManager.pinLength())
        assertFalse(pinCryptoManager.verifyPin("6543"))
        assertTrue(pinCryptoManager.verifyPin("654321"))
    }

    @Test
    fun pinLengthIsAbsentUntilConfigured() = runTest {
        assertNull(pinCryptoManager.pinLength())
    }
}

package fr.luteal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PendingBackupStoreTest {

    @Before
    fun setUp() {
        // Drain any lingering state before each test
        PendingBackupStore.consume()
    }

    @Test
    fun `consume returns null when store is empty`() {
        assertNull(PendingBackupStore.consume())
    }

    @Test
    fun `set stores json and consume retrieves and clears it`() {
        val payload = """{"version":1,"entries":[]}"""
        PendingBackupStore.set(payload)

        assertEquals(payload, PendingBackupStore.consume())
        assertNull(PendingBackupStore.consume())
    }

    @Test
    fun `second set overwrites unconsumed json`() {
        PendingBackupStore.set("first")
        PendingBackupStore.set("second")

        assertEquals("second", PendingBackupStore.consume())
        assertNull(PendingBackupStore.consume())
    }
}

package fr.luteal.app.sync

import fr.luteal.app.sync.AutoSyncCoordinator.Companion.hasNewLocalChange
import fr.luteal.app.sync.AutoSyncCoordinator.Companion.isAppOpenSyncDue
import fr.luteal.app.sync.AutoSyncCoordinator.Companion.shouldAutoSync
import fr.luteal.core.model.SyncMode
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncCoordinatorTest {

    private val minInterval = Duration.ofMinutes(5)
    private val now = 10_000_000L

    @Test
    fun `auto sync needs online mode and a linked account`() {
        assertTrue(shouldAutoSync(SyncMode.ONLINE_CLOUD.name) { true })
        assertFalse(shouldAutoSync(SyncMode.ONLINE_CLOUD.name) { false })
        assertFalse(shouldAutoSync(SyncMode.OFFLINE_LOCAL.name) { true })
        assertFalse(shouldAutoSync(SyncMode.OFFLINE_LOCAL.name) { false })
    }

    @Test
    fun `offline mode does not read credentials`() {
        assertFalse(shouldAutoSync(SyncMode.OFFLINE_LOCAL.name) { error("credentials read") })
    }

    @Test
    fun `a new revision is a local change`() {
        assertTrue(hasNewLocalChange(setOf("a"), setOf("a", "b")))
        assertTrue(hasNewLocalChange(emptySet(), setOf("a")))
    }

    @Test
    fun `a re-edited record counts as a change`() {
        assertTrue(hasNewLocalChange(setOf("a"), setOf("a2")))
    }

    @Test
    fun `a push draining revisions does not retrigger`() {
        assertFalse(hasNewLocalChange(setOf("a", "b", "c"), setOf("b", "c")))
        assertFalse(hasNewLocalChange(setOf("a"), emptySet()))
        assertFalse(hasNewLocalChange(setOf("a"), setOf("a")))
    }

    @Test
    fun `first app open with no history syncs`() {
        assertTrue(isAppOpenSyncDue(now, lastSyncedMillis = null, lastTriggerMillis = null, minInterval))
    }

    @Test
    fun `reopening inside the window after a sync is skipped`() {
        val recent = now - minInterval.toMillis() + 1
        assertFalse(isAppOpenSyncDue(now, lastSyncedMillis = recent, lastTriggerMillis = null, minInterval))
    }

    @Test
    fun `a recent trigger counts even if that sync never finished`() {
        val longAgo = now - Duration.ofHours(3).toMillis()
        val recent = now - 1_000L
        assertFalse(isAppOpenSyncDue(now, lastSyncedMillis = longAgo, lastTriggerMillis = recent, minInterval))
    }

    @Test
    fun `reopening after the window syncs again`() {
        val atEdge = now - minInterval.toMillis()
        assertTrue(isAppOpenSyncDue(now, lastSyncedMillis = atEdge, lastTriggerMillis = atEdge, minInterval))
    }
}

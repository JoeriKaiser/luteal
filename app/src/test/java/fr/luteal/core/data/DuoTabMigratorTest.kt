package fr.luteal.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.DuoWidgetCacheEntity
import fr.luteal.core.data.local.DuoWidgetCacheDao
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.network.crypto.DuoKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuoTabMigratorTest {

    private lateinit var context: Context
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var keyStore: FakeDuoKeyStore
    private lateinit var widgetCacheDao: FakeDuoWidgetCacheDao
    private lateinit var migrator: DuoTabMigrator

    private class FakeDuoKeyStore(context: Context) : DuoKeyStore(context) {
        var hasKeys = false
        override fun hasLinkKeys(): Boolean = hasKeys
    }

    private class FakeDuoWidgetCacheDao : DuoWidgetCacheDao {
        var entity: DuoWidgetCacheEntity? = null
        override fun observeLatest(): Flow<DuoWidgetCacheEntity?> = flowOf(entity)
        override suspend fun getLatest(): DuoWidgetCacheEntity? = entity
        override suspend fun upsert(entity: DuoWidgetCacheEntity) { this.entity = entity }
        override suspend fun clear() { entity = null }
    }

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        userPreferencesDataStore = UserPreferencesDataStore(context)
        userPreferencesDataStore.clear()
        keyStore = FakeDuoKeyStore(context)
        widgetCacheDao = FakeDuoWidgetCacheDao()
        migrator = DuoTabMigrator(
            userPreferencesDataStore = userPreferencesDataStore,
            duoKeyStore = keyStore,
            duoWidgetCacheRepository = DuoWidgetCacheRepository(widgetCacheDao)
        )
    }

    @Test
    fun onlyAnUnsetChoiceAfterOnboardingNeedsMigration() {
        assertTrue(DuoTabMigrator.needsMigration(hasCompletedOnboarding = true, storedDuoEnabled = null))
        assertFalse(DuoTabMigrator.needsMigration(hasCompletedOnboarding = false, storedDuoEnabled = null))
        assertFalse(DuoTabMigrator.needsMigration(hasCompletedOnboarding = true, storedDuoEnabled = false))
        assertFalse(DuoTabMigrator.needsMigration(hasCompletedOnboarding = true, storedDuoEnabled = true))
    }

    @Test
    fun resolveKeepsDuoForLinkedUsersAndPartners() {
        assertEquals(
            DuoTabMigrator.Outcome(duoEnabled = true, showMovedNotice = false),
            DuoTabMigrator.resolve(userRole = "PRIMARY_TRACKER", hasDuoLink = true)
        )
        assertEquals(
            DuoTabMigrator.Outcome(duoEnabled = true, showMovedNotice = false),
            DuoTabMigrator.resolve(userRole = "PARTNER_VIEWER", hasDuoLink = false)
        )
        assertEquals(
            DuoTabMigrator.Outcome(duoEnabled = false, showMovedNotice = true),
            DuoTabMigrator.resolve(userRole = "PRIMARY_TRACKER", hasDuoLink = false)
        )
    }

    @Test
    fun updatedInstallWithoutDuoHidesTheTabAndShowsTheNoticeOnce() = runTest {
        userPreferencesDataStore.setCompletedOnboarding(true)

        migrator.migrateIfNeeded()

        var prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(false, prefs.duoEnabled)
        assertTrue(prefs.showDuoMovedNotice)

        userPreferencesDataStore.dismissDuoMovedNotice()
        migrator.migrateIfNeeded()

        prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(false, prefs.duoEnabled)
        assertFalse(prefs.showDuoMovedNotice)
    }

    @Test
    fun updatedInstallWithLinkKeyKeepsTheTab() = runTest {
        userPreferencesDataStore.setCompletedOnboarding(true)
        keyStore.hasKeys = true

        migrator.migrateIfNeeded()

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(true, prefs.duoEnabled)
        assertFalse(prefs.showDuoMovedNotice)
    }

    @Test
    fun updatedInstallWithCachedDuoWidgetKeepsTheTab() = runTest {
        userPreferencesDataStore.setCompletedOnboarding(true)
        widgetCacheDao.entity = DuoWidgetCacheEntity(
            linkId = "link-1",
            role = "PARTNER",
            cycleDay = 12,
            estimateStart = null,
            estimateEnd = null,
            cycleDayGranted = true,
            estimateGranted = false,
            status = "AVAILABLE",
            refreshedAtEpochMillis = 0L
        )

        migrator.migrateIfNeeded()

        assertEquals(true, userPreferencesDataStore.userPreferencesFlow.first().duoEnabled)
    }

    @Test
    fun updatedPartnerKeepsTheTab() = runTest {
        userPreferencesDataStore.setUserRole("PARTNER_VIEWER")
        userPreferencesDataStore.setCompletedOnboarding(true)

        migrator.migrateIfNeeded()

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(true, prefs.duoEnabled)
        assertFalse(prefs.showDuoMovedNotice)
    }

    @Test
    fun newInstallIsLeftToOnboardingAndNeverSeesTheNotice() = runTest {
        migrator.migrateIfNeeded()
        assertNull(userPreferencesDataStore.userPreferencesFlow.first().duoEnabled)

        userPreferencesDataStore.setDuoEnabled(false)
        userPreferencesDataStore.setCompletedOnboarding(true)
        migrator.migrateIfNeeded()

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(false, prefs.duoEnabled)
        assertFalse(prefs.showDuoMovedNotice)
    }

    @Test
    fun migrationNeverOverwritesAChoiceMadeMeanwhile() = runTest {
        userPreferencesDataStore.setDuoEnabled(true)

        userPreferencesDataStore.applyDuoTabMigration(enabled = false, showMovedNotice = true)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(true, prefs.duoEnabled)
        assertFalse(prefs.showDuoMovedNotice)
    }

    @Test
    fun turningDuoOnRetiresThePendingNotice() = runTest {
        userPreferencesDataStore.setCompletedOnboarding(true)
        migrator.migrateIfNeeded()

        userPreferencesDataStore.setDuoEnabled(true)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(true, prefs.duoEnabled)
        assertFalse(prefs.showDuoMovedNotice)
    }
}

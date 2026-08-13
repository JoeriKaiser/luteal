package fr.luteal.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.network.auth.EncryptedSyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.crypto.DuoKeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import fr.luteal.core.network.sync.FakeCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class DataExportManagerTest {

    private lateinit var context: Context
    private lateinit var database: LutealDatabase
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var syncDataStore: SyncDataStore
    private lateinit var credentialStore: FakeCredentialStore
    private lateinit var duoKeyStore: DuoKeyStore
    private lateinit var exportManager: DataExportManager
    private lateinit var purgeManager: LocalDataPurgeManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userPreferencesDataStore = UserPreferencesDataStore(context)
        syncDataStore = SyncDataStore(context)
        credentialStore = FakeCredentialStore()
        duoKeyStore = DuoKeyStore(context)

        exportManager = DataExportManager(
            cycleDao = database.cycleDao(),
            dailyEntryDao = database.dailyEntryDao(),
            symptomDao = database.symptomDao(),
            userPreferencesDataStore = userPreferencesDataStore
        )

        purgeManager = LocalDataPurgeManager(
            database = database,
            userPreferencesDataStore = userPreferencesDataStore,
            syncDataStore = syncDataStore,
            syncCredentialStore = credentialStore,
            duoKeyStore = duoKeyStore
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportAndRoundTripJsonValidation() = runTest {
        // Seed cycle
        database.cycleDao().insertCycle(
            CycleEntity(
                id = "cycle-100",
                startDate = "2026-07-01",
                endDate = "2026-07-28",
                periodDaysJson = """[{"date":"2026-07-01","bleedingIntensity":"HEAVY","notes":"Day 1","symptomIds":["cramps"]}]""",
                averageLengthDays = 28,
                lutealPhaseLengthDays = 14
            )
        )

        // Seed daily entry
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-07-02",
                bleedingIntensity = "MEDIUM",
                painLevel = 3,
                moodLevel = 4,
                energyLevel = 2,
                symptomIdsJson = """["cramps","bloating"]""",
                notes = "Felt better in the evening",
                updatedAtEpochMillis = 1785500000000L
            )
        )

        // Seed symptom log
        database.symptomDao().insertSymptomLog(
            SymptomLogEntity(
                id = "symptom-log-1",
                timestampEpochMillis = 1785500000000L,
                date = "2026-07-02",
                symptomId = "cramps",
                severity = 3,
                notes = "Lower abdomen"
            )
        )

        // Set preferences
        userPreferencesDataStore.setDisorderTracking("pmdd", true)
        userPreferencesDataStore.setAgeBand("25-29")

        // Export to JSON string and stream
        val jsonString = exportManager.exportToJsonString(appVersion = "1.2.0")
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("\"schema_version\": 1"))
        assertTrue(jsonString.contains("cycle-100"))
        assertTrue(jsonString.contains("2026-07-02"))
        assertTrue(jsonString.contains("bloating"))

        // Decode back
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<LutealBackupPayload>(jsonString)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("1.2.0", decoded.appVersion)
        assertEquals(1, decoded.cycles.size)
        assertEquals("cycle-100", decoded.cycles[0].id)
        assertEquals(1, decoded.cycles[0].periodDays.size)
        assertEquals("HEAVY", decoded.cycles[0].periodDays[0].bleedingIntensity)

        assertEquals(1, decoded.dailyEntries.size)
        assertEquals("2026-07-02", decoded.dailyEntries[0].date)
        assertEquals(3, decoded.dailyEntries[0].painLevel)
        assertEquals(listOf("cramps", "bloating"), decoded.dailyEntries[0].symptomIds)

        assertEquals(1, decoded.symptomLogs.size)
        assertEquals("cramps", decoded.symptomLogs[0].symptomId)
        assertEquals(3, decoded.symptomLogs[0].severity)

        assertEquals("25-29", decoded.preferences.ageBand)
        assertTrue(decoded.preferences.trackPmdd)

        // Test stream writing
        val outStream = ByteArrayOutputStream()
        exportManager.exportToStream(outStream, appVersion = "1.2.0")
        val streamContent = outStream.toByteArray().toString(Charsets.UTF_8)
        assertTrue(streamContent.contains("cycle-100"))
    }

    @Test
    fun localDataPurgeClearsAllDataAndSecrets() = runTest {
        // Seed database
        database.cycleDao().insertCycle(
            CycleEntity(
                id = "cycle-200",
                startDate = "2026-08-01",
                endDate = null,
                periodDaysJson = "[]",
                averageLengthDays = 28,
                lutealPhaseLengthDays = 14
            )
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-08-02",
                bleedingIntensity = null,
                painLevel = 1,
                moodLevel = 5,
                energyLevel = 5,
                symptomIdsJson = "[]",
                notes = "",
                updatedAtEpochMillis = 1785600000000L
            )
        )

        // Seed preferences and credentials
        userPreferencesDataStore.setCompletedOnboarding(true)
        syncDataStore.setCursor(42L)
        credentialStore.save(
            SyncCredentials(
                accountId = "acc-123",
                accountCode = "code-456",
                deviceToken = "token-789"
            )
        )
        // Execute purge
        purgeManager.purgeAllLocalData()

        // Verify DB is empty
        assertEquals(0, database.cycleDao().getAllCyclesOnce().size)
        assertEquals(0, database.dailyEntryDao().getAllEntriesOnce().size)
        assertEquals(0, database.symptomDao().getAllSymptomLogsOnce().size)

        // Verify DataStores are cleared
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertFalse(prefs.hasCompletedOnboarding)

        val syncPrefs = syncDataStore.syncPreferencesFlow.first()
        assertEquals(0L, syncPrefs.cursor)

        // Verify credentials are wiped
        assertNull(credentialStore.load())
    }
}

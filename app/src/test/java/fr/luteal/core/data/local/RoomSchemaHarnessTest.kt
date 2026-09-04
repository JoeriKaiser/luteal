package fr.luteal.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Schema harness for LutealDatabase. Room schema JSON is exported to
 * app/schemas (exposed to unit tests as assets), so:
 *  - [latestSchemaCreatesAndValidates] fails CI when entities and the exported
 *    version-7 schema drift apart, and
 *  - future migrations get tested by creating the old version here and running
 *    `runMigrationsAndValidate(name, newVersion, true, MIGRATION_x_y)` against
 *    real data instead of discovering typos on user devices.
 */
@RunWith(RobolectricTestRunner::class)
class RoomSchemaHarnessTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LutealDatabase::class.java,
    )

    @Test
    fun latestSchemaCreatesAndValidates() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.query("SELECT count(*) FROM sqlite_master WHERE type='table'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getInt(0) > 0)
            }
        }
        helper.runMigrationsAndValidate(TEST_DB, 8, true).close()
    }

    @Test
    fun migrate1To2() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, LutealDatabase.MIGRATION_1_2).close()
    }

    @Test
    fun migrate2To3() {
        helper.createDatabase(TEST_DB, 2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, LutealDatabase.MIGRATION_2_3).close()
    }

    @Test
    fun migrate3To4() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO cycle_sync_state (cycleId, clientRev, createdAtEpochMillis, updatedAtEpochMillis, dirty) " +
                    "VALUES ('c1', 'rev1', 1000, 2000, 1)"
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 4, true, LutealDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT * FROM sync_state WHERE entityId = 'c1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun migrate4To5() {
        helper.createDatabase(TEST_DB, 4).close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, LutealDatabase.MIGRATION_4_5).close()
    }

    @Test
    fun migrate5To6() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO cycles (id, startDate, periodDaysJson, averageLengthDays, lutealPhaseLengthDays, isSynced) " +
                    "VALUES ('c1', '2026-08-01', '[]', 28, 14, 0)"
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, LutealDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT isExcludedFromEstimates, exclusionReason FROM cycles WHERE id = 'c1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate6To7() {
        helper.createDatabase(TEST_DB, 6).close()
        helper.runMigrationsAndValidate(TEST_DB, 7, true, LutealDatabase.MIGRATION_6_7).close()
    }

    @Test
    fun migrate7To8() {
        helper.createDatabase(TEST_DB, 7).close()
        helper.runMigrationsAndValidate(TEST_DB, 8, true, LutealDatabase.MIGRATION_7_8).close()
    }

    @Test
    fun migrateAll_1To8() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            LutealDatabase.MIGRATION_1_2,
            LutealDatabase.MIGRATION_2_3,
            LutealDatabase.MIGRATION_3_4,
            LutealDatabase.MIGRATION_4_5,
            LutealDatabase.MIGRATION_5_6,
            LutealDatabase.MIGRATION_6_7,
            LutealDatabase.MIGRATION_7_8
        ).close()
    }

    companion object {
        private const val TEST_DB = "luteal-schema-harness-test.db"
    }
}

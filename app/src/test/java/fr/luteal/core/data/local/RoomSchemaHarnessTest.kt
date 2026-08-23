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
        // Creating at version 7 validates the freshly created file against the
        // exported 7.json: any entity/annotation drift fails here.
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.query("SELECT count(*) FROM sqlite_master WHERE type='table'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getInt(0) > 0)
            }
        }

        // Re-opening validates the on-disk file again through the full
        // configuration path (tables, indices, foreign keys).
        helper.runMigrationsAndValidate(TEST_DB, 7, true).close()
    }

    companion object {
        private const val TEST_DB = "luteal-schema-harness-test.db"
    }
}

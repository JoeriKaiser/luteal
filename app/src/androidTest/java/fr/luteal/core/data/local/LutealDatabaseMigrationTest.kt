package fr.luteal.core.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LutealDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "widget-migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration4To5AddsDuoCacheWithoutDestroyingExistingData() {
        val version4 = helper(object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE migration_sentinel (value TEXT NOT NULL)")
                db.execSQL("INSERT INTO migration_sentinel VALUES ('preserved')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        version4.writableDatabase
        version4.close()

        val version5 = helper(object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(4, oldVersion)
                assertEquals(5, newVersion)
                LutealDatabase.MIGRATION_4_5.migrate(db)
            }
        })
        val db = version5.writableDatabase

        db.query("SELECT value FROM migration_sentinel").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("preserved", cursor.getString(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'duo_widget_cache'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        version5.close()
    }

    private fun helper(callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
}

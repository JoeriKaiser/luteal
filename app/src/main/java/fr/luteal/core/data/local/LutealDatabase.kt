package fr.luteal.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.DisorderConfigEntity
import fr.luteal.core.data.entity.DuoWidgetCacheEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.entity.UserProfileEntity

@Database(
    entities = [
        CycleEntity::class,
        SyncStateEntity::class,
        DailyEntryEntity::class,
        SymptomLogEntity::class,
        DisorderConfigEntity::class,
        UserProfileEntity::class,
        DuoWidgetCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LutealDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun dailyEntryDao(): DailyEntryDao
    abstract fun duoWidgetCacheDao(): DuoWidgetCacheDao
    abstract fun symptomDao(): SymptomDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_entries (
                        date TEXT NOT NULL PRIMARY KEY,
                        bleedingIntensity TEXT,
                        painLevel INTEGER,
                        moodLevel INTEGER,
                        energyLevel INTEGER,
                        symptomIdsJson TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cycle_sync_state (
                        cycleId TEXT NOT NULL PRIMARY KEY,
                        clientRev TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        deletedAtEpochMillis INTEGER,
                        dirty INTEGER NOT NULL DEFAULT 1,
                        lastPushError TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_state (
                        entityId TEXT NOT NULL PRIMARY KEY,
                        entityType TEXT NOT NULL,
                        clientRev TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        deletedAtEpochMillis INTEGER,
                        dirty INTEGER NOT NULL DEFAULT 1,
                        lastPushError TEXT
                    )
                    """.trimIndent()
                )
                // Migrate existing cycle sync states into the generic table.
                db.execSQL(
                    """
                    INSERT INTO sync_state (entityId, entityType, clientRev, createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis, dirty, lastPushError)
                    SELECT cycleId, 'cycle', clientRev, createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis, dirty, lastPushError
                    FROM cycle_sync_state
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS cycle_sync_state")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS duo_widget_cache (
                        linkId TEXT NOT NULL PRIMARY KEY,
                        role TEXT NOT NULL,
                        cycleDay INTEGER,
                        estimateStart TEXT,
                        estimateEnd TEXT,
                        cycleDayGranted INTEGER NOT NULL,
                        estimateGranted INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        refreshedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}

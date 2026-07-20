package fr.luteal.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DisorderConfigEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.entity.UserProfileEntity

@Database(
    entities = [
        CycleEntity::class,
        SymptomLogEntity::class,
        DisorderConfigEntity::class,
        UserProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LutealDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun symptomDao(): SymptomDao
    abstract fun userProfileDao(): UserProfileDao
}

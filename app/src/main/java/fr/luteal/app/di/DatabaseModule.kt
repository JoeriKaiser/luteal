package fr.luteal.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.DuoWidgetCacheDao
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.local.UserProfileDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLutealDatabase(
        @ApplicationContext context: Context
    ): LutealDatabase {
        return Room.databaseBuilder(
            context,
            LutealDatabase::class.java,
            "luteal.db"
        ).addMigrations(
                LutealDatabase.MIGRATION_1_2,
                LutealDatabase.MIGRATION_2_3,
                LutealDatabase.MIGRATION_3_4,
                LutealDatabase.MIGRATION_4_5,
                LutealDatabase.MIGRATION_5_6,
                LutealDatabase.MIGRATION_6_7
            ).build()
    }

    @Provides
    @Singleton
    fun provideCycleDao(database: LutealDatabase): CycleDao {
        return database.cycleDao()
    }

    @Provides
    @Singleton
    fun provideSyncStateDao(database: LutealDatabase): SyncStateDao {
        return database.syncStateDao()
    }

    @Provides
    @Singleton
    fun provideDailyEntryDao(database: LutealDatabase): DailyEntryDao {
        return database.dailyEntryDao()
    }

    @Provides
    @Singleton
    fun provideDuoWidgetCacheDao(database: LutealDatabase): DuoWidgetCacheDao {
        return database.duoWidgetCacheDao()
    }

    @Provides
    @Singleton
    fun provideSymptomDao(database: LutealDatabase): SymptomDao {
        return database.symptomDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(database: LutealDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideBiomarkerDao(database: LutealDatabase): BiomarkerDao {
        return database.biomarkerDao()
    }
}

package fr.luteal.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.luteal.app.BuildConfig
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.network.FolicularApiClient
import fr.luteal.core.network.OkHttpFolicularApiClient
import fr.luteal.core.network.auth.EncryptedSyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.sync.CycleSyncEngine
import fr.luteal.core.network.sync.DataStoreSyncCursorStore
import fr.luteal.core.network.sync.FolicularApiClientFactory
import fr.luteal.core.network.sync.SyncCursorStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Default folicular base URL for online sync, per build type: the debug/dev
 * build targets the local trial server (emulator loopback), the release build
 * targets the production API over HTTPS. Overridable at runtime in Settings
 * (debug only).
 */
private val DEFAULT_SYNC_BASE_URL: String = BuildConfig.SYNC_BASE_URL

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncCredentialStore(
        impl: EncryptedSyncCredentialStore
    ): SyncCredentialStore

    companion object {

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        @Provides
        @Singleton
        fun provideFolicularApiClientFactory(
            okHttpClient: OkHttpClient
        ): FolicularApiClientFactory = FolicularApiClientFactory { baseUrl ->
            OkHttpFolicularApiClient(baseUrl, okHttpClient)
        }

        @Provides
        @Singleton
        fun provideSyncCursorStore(syncDataStore: SyncDataStore): SyncCursorStore =
            DataStoreSyncCursorStore(syncDataStore, DEFAULT_SYNC_BASE_URL)

        @Provides
        @Singleton
        fun provideCycleSyncEngine(
            cycleRepository: CycleRepository,
            syncStateDao: SyncStateDao,
            dailyEntryDao: DailyEntryDao,
            symptomDao: SymptomDao,
            biomarkerDao: BiomarkerDao,
            credentialStore: SyncCredentialStore,
            apiClientFactory: FolicularApiClientFactory,
            cursorStore: SyncCursorStore,
            recordSealer: RecordSealer
        ): CycleSyncEngine = CycleSyncEngine(
            cycleRepository = cycleRepository,
            syncStateDao = syncStateDao,
            dailyEntryDao = dailyEntryDao,
            symptomDao = symptomDao,
            biomarkerDao = biomarkerDao,
            credentialStore = credentialStore,
            apiClientFactory = apiClientFactory,
            cursorStore = cursorStore,
            recordSealer = recordSealer
        )
    }
}

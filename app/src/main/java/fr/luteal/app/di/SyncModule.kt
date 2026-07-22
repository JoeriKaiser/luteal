package fr.luteal.app.di

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.network.FolicularApiClient
import fr.luteal.core.network.OkHttpFolicularApiClient
import fr.luteal.core.network.auth.EncryptedSyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.sync.CycleSyncEngine
import fr.luteal.core.network.sync.DataStoreSyncCursorStore
import fr.luteal.core.network.sync.FolicularApiClientFactory
import fr.luteal.core.network.sync.SyncCursorStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Emulator loopback alias to the host machine; overridable at runtime. */
const val DEFAULT_SYNC_BASE_URL = "http://10.0.2.2:8080"

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
            credentialStore: SyncCredentialStore,
            apiClientFactory: FolicularApiClientFactory,
            cursorStore: SyncCursorStore
        ): CycleSyncEngine = CycleSyncEngine(
            cycleRepository = cycleRepository,
            syncStateDao = syncStateDao,
            dailyEntryDao = dailyEntryDao,
            symptomDao = symptomDao,
            credentialStore = credentialStore,
            apiClientFactory = apiClientFactory,
            cursorStore = cursorStore,
            deviceNameProvider = { Build.MODEL.ifBlank { "Luteal" } }
        )
    }
}

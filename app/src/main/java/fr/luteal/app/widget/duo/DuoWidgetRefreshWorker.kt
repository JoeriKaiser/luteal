package fr.luteal.app.widget.duo

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.luteal.app.widget.WidgetUpdateCoordinator
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.repository.DuoCycleProjectionCacheWriter
import fr.luteal.core.data.repository.DuoRepository
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.FolicularApiException
import kotlinx.coroutines.flow.first

/** Refreshes the grant-filtered Duo cycle cache without blocking widget render. */
@HiltWorker
class DuoWidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val duoRepository: DuoRepository,
    private val cacheWriter: DuoCycleProjectionCacheWriter,
    private val cacheRepository: DuoWidgetCacheRepository,
    private val userPreferences: UserPreferencesDataStore,
    private val updates: WidgetUpdateCoordinator
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        refresh()
    } finally {
        updates.finishDuoRefresh()
    }

    private suspend fun refresh(): Result {
        if (userPreferences.userPreferencesFlow.first().syncMode != SyncMode.ONLINE_CLOUD.name) {
            return Result.success()
        }
        if (!duoRepository.hasAccount()) {
            cacheRepository.clear()
            return Result.success()
        }

        return runCatching {
            cacheWriter.save(duoRepository.duoView())
            Result.success()
        }.getOrElse { error ->
            // A confirmed 404 means there is no current relationship. Other
            // failures preserve the last readable projection for offline use.
            if (error is FolicularApiException && error.status == 404) {
                cacheRepository.clear()
                Result.success()
            } else if (error is FolicularApiException && error.status in 400..499) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}

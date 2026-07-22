package fr.luteal.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.sync.CycleSyncEngine
import java.time.Clock
import kotlinx.coroutines.flow.first

/**
 * On-demand background sync. Local writes never wait on this; it reconciles
 * Room with the server. It only does network work when the user has enabled
 * [SyncMode.ONLINE_CLOUD]; otherwise it is a no-op, so the offline app is
 * unaffected. All outcome state is written to [SyncDataStore] for the UI to
 * observe; no credentials are ever logged.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: CycleSyncEngine,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val syncDataStore: SyncDataStore,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val syncMode = userPreferencesDataStore.userPreferencesFlow.first().syncMode
        if (syncMode != SyncMode.ONLINE_CLOUD.name) {
            // Offline mode: nothing to do. Keeps the offline path pristine.
            return Result.success()
        }

        syncDataStore.setInProgress(true)
        return try {
            // Rejections/conflicts are surfaced through SyncDataStore and the
            // engine's local sync state; a completed pass is a success even
            // when some records were rejected by server-side validation.
            syncEngine.sync()
            syncDataStore.recordSuccess(clock.millis())
            Result.success()
        } catch (t: Exception) {
            syncDataStore.recordError(t.message ?: "erreur de synchronisation")
            Result.failure()
        } finally {
            // recordSuccess/recordError already clear inProgress; this guards
            // against an unexpected throw leaving the flag stuck.
            syncDataStore.setInProgress(false)
        }
    }
}

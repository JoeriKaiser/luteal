package fr.luteal.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.luteal.app.R
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.FolicularApiException
import fr.luteal.core.network.sync.CycleSyncEngine
import fr.luteal.core.network.sync.SyncAuthException
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * On-demand background sync. Local writes never wait on this; it reconciles
 * Room with the server. It only does network work when the user has enabled
 * [SyncMode.ONLINE_CLOUD]; otherwise it is a no-op, so the offline app is
 * unaffected. All outcome state is written to [SyncDataStore] for the UI to
 * observe; no credentials are ever logged.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: CycleSyncEngine,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val syncDataStore: SyncDataStore,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    companion object {
        /** Transient failures (transport, 5xx) retry with backoff, then give up. */
        const val MAX_TRANSIENT_ATTEMPTS = 3

        /** Pre-existing user-visible fallback; localised at render time upstream. */
        private const val GENERIC_SYNC_ERROR = "erreur de synchronisation"
    }

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
        } catch (ce: CancellationException) {
            // ExistingWorkPolicy.REPLACE cancels an in-flight worker whenever
            // a new sync is requested. Swallowing cancellation would corrupt
            // cooperative cancellation and strand the IN_PROGRESS flag.
            throw ce
        } catch (e: SyncAuthException) {
            // Terminal by design: credentials stay stored (the account code
            // is the only recovery credential); the user reconnects from
            // Settings. Never auto-clear or auto-re-register here.
            syncDataStore.recordError(appContext.getString(R.string.sync_error_auth_rejected))
            Result.failure()
        } catch (e: FolicularApiException) {
            if (e.status >= 500 && runAttemptCount < MAX_TRANSIENT_ATTEMPTS) {
                Result.retry()
            } else {
                syncDataStore.recordError(e.message ?: GENERIC_SYNC_ERROR)
                Result.failure()
            }
        } catch (e: IOException) {
            // Transport failure: transient by definition; backoff applies.
            if (runAttemptCount < MAX_TRANSIENT_ATTEMPTS) {
                Result.retry()
            } else {
                syncDataStore.recordError(e.message ?: GENERIC_SYNC_ERROR)
                Result.failure()
            }
        } catch (t: Exception) {
            syncDataStore.recordError(t.message ?: GENERIC_SYNC_ERROR)
            Result.failure()
        } finally {
            // recordSuccess/recordError already clear inProgress; this guards
            // against cancellation or an unexpected throw leaving the flag
            // stuck. NonCancellable because the job may already be cancelled.
            withContext(NonCancellable) { syncDataStore.setInProgress(false) }
        }
    }
}

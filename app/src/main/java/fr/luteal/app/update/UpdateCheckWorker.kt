package fr.luteal.app.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Only notifies, once per version; downloads happen from Settings. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateManager: UpdateManager,
    private val preferencesStore: UpdatePreferencesStore,
    private val notifier: UpdateNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesStore.preferences.first()
        if (!prefs.checksEnabled || !updateManager.isAvailableForThisInstall) return Result.success()

        return try {
            val outcome = updateManager.fetchOutcome()
            if (outcome is UpdateCheckOutcome.Available) {
                val version = outcome.info.version.toString()
                if (prefs.lastNotifiedVersion != version) {
                    notifier.notifyUpdateAvailable(version)
                    preferencesStore.setLastNotifiedVersion(version)
                }
            }
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

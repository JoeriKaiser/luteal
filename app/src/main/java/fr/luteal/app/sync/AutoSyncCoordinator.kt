package fr.luteal.app.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.auth.SyncCredentialStore
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Triggers sync without the user asking: when the app comes to the
 * foreground, shortly after local edits, and on a periodic schedule. Only
 * active while [SyncMode.ONLINE_CLOUD] is on and an account is linked.
 * Local edits are detected from the dirty sync envelopes the repositories
 * already write, so the UI write path is untouched.
 */
@Singleton
class AutoSyncCoordinator @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val syncDataStore: SyncDataStore,
    private val syncStateDao: SyncStateDao,
    private val credentialStore: SyncCredentialStore,
    private val clock: Clock
) : DefaultLifecycleObserver {
    private var scope: CoroutineScope? = null

    @Volatile
    private var lastAppOpenTriggerMillis: Long? = null

    fun start(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope

        // Also covers installs that already had sync on before this existed.
        // Only a later switch to online syncs right away; launch is left to onStart.
        scope.launch {
            var initial = true
            userPreferencesDataStore.userPreferencesFlow
                .map { it.syncMode }
                .distinctUntilChanged()
                .collect {
                    reconcileNow(syncNow = !initial)
                    initial = false
                }
        }

        scope.launch {
            var previous: Set<String>? = null
            syncStateDao.observeDirtyRevisions().collect { revisions ->
                val current = revisions.toSet()
                // Edits saved before this started collecting only show up as
                // already pending, so the first emission pushes them too.
                val changed = previous?.let { hasNewLocalChange(it, current) } ?: current.isNotEmpty()
                previous = current
                if (changed && isEnabled()) syncScheduler.syncAfterLocalChange()
            }
        }
    }

    /** Call after the linked account changes (created, restored, unlinked). */
    fun reconcile() {
        scope?.launch { reconcileNow(syncNow = true) }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope?.launch {
            if (!isEnabled()) return@launch
            val now = clock.millis()
            val lastSynced = syncDataStore.syncPreferencesFlow.first().lastSyncedEpochMillis
            if (isAppOpenSyncDue(now, lastSynced, lastAppOpenTriggerMillis)) {
                lastAppOpenTriggerMillis = now
                syncScheduler.syncOnAppOpen()
            }
        }
    }

    private suspend fun reconcileNow(syncNow: Boolean) {
        if (!isEnabled()) {
            syncScheduler.cancelAutomatic()
            return
        }
        syncScheduler.schedulePeriodic()
        if (syncNow) syncScheduler.syncOnAppOpen()
    }

    private suspend fun isEnabled(): Boolean = shouldAutoSync(
        syncMode = userPreferencesDataStore.userPreferencesFlow.first().syncMode,
        hasAccount = { credentialStore.load() != null }
    )

    internal companion object {
        /** [hasAccount] is lazy so offline mode never touches the Keystore. */
        fun shouldAutoSync(syncMode: String, hasAccount: () -> Boolean): Boolean =
            syncMode == SyncMode.ONLINE_CLOUD.name && hasAccount()

        /**
         * A push only removes revisions, so it never retriggers itself. A
         * conflict rebase during pull does add one, and that edit still needs
         * the follow-up push.
         */
        fun hasNewLocalChange(previous: Set<String>, current: Set<String>): Boolean =
            current.any { it !in previous }

        fun isAppOpenSyncDue(
            nowMillis: Long,
            lastSyncedMillis: Long?,
            lastTriggerMillis: Long?,
            minInterval: Duration = SyncScheduler.APP_OPEN_MIN_INTERVAL
        ): Boolean {
            val latest = listOfNotNull(lastSyncedMillis, lastTriggerMillis).maxOrNull() ?: return true
            return nowMillis - latest >= minInterval.toMillis()
        }
    }
}

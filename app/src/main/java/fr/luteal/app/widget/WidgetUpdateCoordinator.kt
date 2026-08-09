package fr.luteal.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.app.widget.duo.DuoCycleWidget
import fr.luteal.app.widget.personal.PersonalCycleWidget
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Singleton
class WidgetUpdateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun updatePersonal() = PersonalCycleWidget().updateAll(context)
    suspend fun updateDuo() = DuoCycleWidget().updateAll(context)

    suspend fun finishDuoRefresh() {
        val widget = DuoCycleWidget()
        GlanceAppWidgetManager(context)
            .getGlanceIds(widget.javaClass)
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { preferences ->
                    preferences[DuoRefreshPendingKey] = false
                }
            }
        widget.updateAll(context)
    }

    suspend fun updateAll() {
        updatePersonal()
        updateDuo()
    }
}

/**
 * Observes local sources while the process is alive. This keeps repositories
 * Android-widget-free and coalesces related Room/DataStore writes into one
 * RemoteViews update.
 */
@Singleton
class WidgetDataObserver @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val userRepository: UserRepository,
    private val duoCacheRepository: DuoWidgetCacheRepository,
    private val updates: WidgetUpdateCoordinator
) {
    private var started = false

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true

        scope.launch {
            combine(
                cycleRepository.getCycles(),
                dailyEntryRepository.observeEntries(),
                userRepository.getUserPreferences()
            ) { cycles, entries, preferences ->
                Triple(cycles, entries, preferences)
            }
                .distinctUntilChanged()
                .debounce(350)
                .collect { updates.updatePersonal() }
        }

        scope.launch {
            duoCacheRepository.observeLatest()
                .distinctUntilChanged()
                .debounce(350)
                .collect { updates.updateDuo() }
        }
    }
}

package fr.luteal.app.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DuoRepository
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.data.repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WidgetSnapshotRepository @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val userRepository: UserRepository,
    private val duoRepository: DuoRepository,
    private val duoCacheRepository: DuoWidgetCacheRepository,
    private val factory: WidgetSnapshotFactory,
    private val clock: Clock
) {
    suspend fun personal(): PersonalWidgetSnapshot = runCatching {
        val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
        factory.personal(
            cycles = cycleRepository.getCyclesOnce(),
            preferences = userRepository.getUserPreferences().first(),
            hasTodayObservation = dailyEntryRepository.getEntryOnce(today)?.hasObservations == true,
            today = today
        )
    }.getOrDefault(PersonalWidgetSnapshot.ReadFailure)

    suspend fun duo(): DuoWidgetSnapshot = runCatching {
        factory.duo(
            hasAccount = duoRepository.hasAccount(),
            cached = duoCacheRepository.getLatest(),
            now = Instant.now(clock)
        )
    }.getOrDefault(DuoWidgetSnapshot.ReadFailure)
}

/** The deliberately narrow Hilt surface available to Glance and callbacks. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun snapshots(): WidgetSnapshotRepository
    fun updates(): WidgetUpdateCoordinator
    fun workScheduler(): WidgetWorkScheduler
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

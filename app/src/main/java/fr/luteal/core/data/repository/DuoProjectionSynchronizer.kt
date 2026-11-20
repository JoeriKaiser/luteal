package fr.luteal.core.data.repository

import fr.luteal.app.notification.DuoPartnerPillAlertNotifier
import fr.luteal.app.notification.NotificationScheduler
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.DuoProjection
import fr.luteal.core.model.PillDayStatus
import fr.luteal.core.model.PillRegimenCalculator
import fr.luteal.core.model.SharedEstimate
import fr.luteal.core.model.SharedLevel
import fr.luteal.core.model.SharedPillStatus
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.contract.models.DuoRole
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.crypto.DuoCrypto
import fr.luteal.core.network.crypto.DuoKeyStore
import fr.luteal.core.network.crypto.DuoProjectionDecodeResult
import fr.luteal.core.network.crypto.DuoProjectionDecoder
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composes and publishes the tracker's grant-filtered [DuoProjection] and
 * reconciles the partner's local pill-reminder state when a projection arrives.
 */
@Singleton
class DuoProjectionSynchronizer @Inject constructor(
    private val duoRepository: DuoRepository,
    private val duoKeyStore: DuoKeyStore,
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val projectionDecoder: DuoProjectionDecoder,
    private val widgetCacheWriter: DuoCycleProjectionCacheWriter,
    private val notificationScheduler: NotificationScheduler,
    private val partnerPillAlertNotifier: DuoPartnerPillAlertNotifier,
    private val clock: Clock
) {
    fun composeLocalPillStatus(
        today: LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        prefs: fr.luteal.core.data.datastore.UserPreferences
    ): SharedPillStatus? {
        if (!prefs.duoSharing.sharePillStatus) return null
        val config = prefs.pillRegimenConfig
        return when (val status = PillRegimenCalculator.evaluate(config, today)) {
            is PillDayStatus.ActiveDay -> SharedPillStatus(
                date = today.toString(),
                isActiveDay = true,
                dayInPhase = status.dayOfPack,
                totalPhaseDays = status.activeDays,
                activeDays = status.activeDays,
                pauseDays = status.pauseDays,
                isTaken = status.isTakenToday,
                reminderTime = config.reminderTime
            )
            is PillDayStatus.PauseDay -> SharedPillStatus(
                date = today.toString(),
                isActiveDay = false,
                dayInPhase = status.dayOfPause,
                totalPhaseDays = status.pauseDays,
                activeDays = status.activeDays,
                pauseDays = status.pauseDays,
                isTaken = false,
                reminderTime = config.reminderTime
            )
            PillDayStatus.Disabled -> null
        }
    }

    suspend fun composeProjection(
        grants: Map<GrantField, Boolean>,
        today: LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
    ): DuoProjection {
        val cycles = cycleRepository.getCyclesOnce()
        val preferences = userPreferencesDataStore.userPreferencesFlow.first()

        val cycleDay = if (grants[GrantField.CYCLE_DAY] == true) {
            cycles.filter { !it.startDate.isAfter(today) }
                .maxByOrNull { it.startDate }
                ?.let { ChronoUnit.DAYS.between(it.startDate, today).toInt() + 1 }
        } else null

        val estimate = if (grants[GrantField.PERIOD_ESTIMATE] == true) {
            CycleEstimateCalculator.estimateNextPeriod(
                cycles = cycles,
                ageBand = AgeBand.fromId(preferences.ageBand),
                hasTimingContext = preferences.hasTimingContext
            )?.let {
                SharedEstimate(
                    windowStart = it.earliestDate.toString(),
                    windowEnd = it.latestDate.toString(),
                    centralDate = it.centralDate.toString(),
                    cycleCount = it.cycleCount,
                    variabilityDays = it.variabilityDays
                )
            }
        } else null

        val latest = dailyEntryRepository.observeEntries().first()
            .filter { !it.date.isAfter(today) }
            .maxByOrNull { it.date }

        val mood = if (grants[GrantField.MOOD] == true) {
            latest?.moodLevel?.let { SharedLevel(latest.date.toString(), it) }
        } else null

        val energy = if (grants[GrantField.ENERGY] == true) {
            latest?.energyLevel?.let { SharedLevel(latest.date.toString(), it) }
        } else null

        val pillStatus = composeLocalPillStatus(today, preferences)

        return DuoProjection(
            cycleDay = cycleDay,
            periodEstimate = estimate,
            mood = mood,
            energy = energy,
            pillStatus = pillStatus
        )
    }

    /**
     * Publishes the tracker's projection if online sync and an active tracker
     * link exist. Safe to call after any local pill/observation mutation.
     */
    suspend fun publishIfTrackerPaired() {
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        if (prefs.syncMode != SyncMode.ONLINE_CLOUD.name) return
        if (!duoRepository.hasAccount()) return
        runCatching {
            val view = duoRepository.duoView()
            if (view.role != DuoRole.TRACKER) return
            val linkId = view.linkId.toString()
            val key = duoKeyStore.load(linkId) ?: return
            val serverGrants = view.grants?.associateWith { true } ?: emptyMap()
            val projection = composeProjection(serverGrants)
            val json = ContractJson.encodeToString(DuoProjection.serializer(), projection)
            duoRepository.putDuoPayload(DuoCrypto.seal(key, linkId, json.toByteArray()))
            widgetCacheWriter.savePublished(linkId, projection, serverGrants)
        }
    }

    /**
     * Reconciles partner-side pill reminder state when a fresh [DuoView] is
     * received on the partner device.
     */
    suspend fun onPartnerViewReceived(view: DuoView, decoded: DuoProjectionDecodeResult) {
        if (view.role != DuoRole.PARTNER) return
        val projection = (decoded as? DuoProjectionDecodeResult.Available)?.projection
        val sharedPill = projection?.pillStatus
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()

        if (sharedPill == null) {
            userPreferencesDataStore.setDuoPartnerSharedPillJson(null)
            partnerPillAlertNotifier.cancelPendingReminderNotification()
            notificationScheduler.reconcileAllSchedules()
            return
        }

        val serialized = ContractJson.encodeToString(SharedPillStatus.serializer(), sharedPill)
        userPreferencesDataStore.setDuoPartnerSharedPillJson(serialized)

        val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate().toString()
        if (sharedPill.isActiveDay && sharedPill.isTaken && sharedPill.date == today) {
            partnerPillAlertNotifier.cancelPendingReminderNotification()
            if (prefs.isNotificationsEnabled &&
                prefs.isDuoPartnerPillTakenAlertEnabled &&
                prefs.duoPartnerNotifiedTakenDate != today
            ) {
                userPreferencesDataStore.setDuoPartnerNotifiedTakenDate(today)
                partnerPillAlertNotifier.notifyPartnerPillTaken(prefs)
            }
        }
        notificationScheduler.reconcileAllSchedules()
    }
}

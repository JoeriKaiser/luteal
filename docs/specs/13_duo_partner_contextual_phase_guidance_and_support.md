# Spec 13: Duo Partner Contextual Phase Guidance and Support Actions

## Problem Statement

Luteal's Duo companion mode allows a primary tracker to consensually share selective cycle projections with their partner under end-to-end encryption (AES-256-GCM + HKDF-SHA256). However, the partner surface in `DuoScreen` and the Duo widget currently displays only raw decrypted numbers: cycle day (e.g., *"Jour 22"*), estimated next-period window (e.g., *"18 – 22 août"*), and 1–5 scalar ratings for mood or energy.

This presentation suffers from three core limitations:

1. **Abstract Numbers Without Physiological Context:**
   Partners without clinical or biological training often find raw cycle day numbers and scalar ratings abstract or difficult to interpret. Without accessible, factual education, partners may misinterpret normal physiological fluctuations or resort to unhelpful cultural tropes and gender stereotypes (e.g., dismissing distress as "just PMS" or asking invasive questions).
2. **High Friction for Practical Support:**
   When partners notice a challenging phase or low energy, they often want to offer simple, practical assistance (e.g., preparing dinner, running errands, or offering a quiet space). However, composing a message from scratch in the support text field requires cognitive effort, leading to hesitation, delayed communication, or awkward phrasing.
3. **Background Sync & Freshness Uncertainty on F-Droid Builds:**
   Luteal is distributed on F-Droid without Google Play Services or Firebase Cloud Messaging (FCM). In the absence of push notifications, partner devices rely on periodic background polling via AndroidX `WorkManager`. If synchronization is delayed due to network conditions or Android battery optimizations (Doze mode), the partner may view an outdated projection without knowing when it was last refreshed.

## Solution

To transform Duo companion mode into an empowering, respectful, and reliable support surface, Spec 13 introduces three integrated capabilities:

1. **Sourced Partner Contextual Phase Guidance:**
   - Surface peer-reviewed, educational phase guidance cards in `DuoScreen` (and optionally in partner detail views) explaining the biological realities of the current cycle phase (Menstrual, Follicular, Ovulatory, Luteal) from a supportive partner perspective.
   - Ground all guidance strictly in public health literature (NHS, ACOG, WHO) and partner health literacy research registered in `docs/research/SOURCE_REGISTER.md`.
   - Maintain Luteal's "Quiet Instrument" tone: non-prescriptive, respectful, avoiding stereotypes, and emphasizing physiological understanding rather than intrusive advice.
   - Respectfully resolve phase estimates from granted fields (`cycle_day` and `period_estimate`), gracefully handling indeterminate states when data is withheld or irregular.
2. **Encrypted Quick-Support Nudges:**
   - Provide a curated set of one-tap, customizable quick-support nudges (e.g., *“J'ai fait les courses”*, *“Soirée calme ce soir ?”*, *“Je suis là si besoin”*, *“Veux-tu que je prépare le dîner ?”*) directly within `SupportThreadSection`.
   - Seal nudges on-device via `DuoCrypto.sealRaw` before transmitting through the existing zero-knowledge Go backend (`folicular`) support endpoint (`POST /duo/v1/links/{id}/support`), categorizing by `SupportKind` (`PRACTICAL`, `COMFORT`, `SPACE`, `GENERAL`).
   - Allow one-tap sending or immediate pre-filling into the draft text field for personalization.
3. **Resilient Background Sync & Transparent Freshness Indicators:**
   - Enhance the `WorkManager` background scheduling (`DuoWidgetRefreshWorker` and `PeriodicSyncWorker`) with opportunistic network constraints and backoff policies optimized for non-GMS/F-Droid environments.
   - Display clear, non-alarmist freshness indicators in `DuoScreen` (e.g., *"Mis à jour aujourd'hui à 14:15"*, *"Dernière mise à jour hier"*, with subtle aging indicators when stale >24h) and ensure seamless fallback to local Room cache (`DuoWidgetCacheDao`).

---

## User Stories

### Contextual Phase Guidance
1. As a Duo partner, I want to see an educational summary explaining the biological characteristics of my partner's current cycle phase, so that I understand natural physiological shifts in energy and comfort without guessing.
2. As a Duo partner without medical background, I want the phase explanations to use clear, accessible, non-jargon language, so that I can learn about cycle biology naturally.
3. As a Duo partner, I want phase guidance to suggest empathetic, non-intrusive support attitudes (e.g., active listening, flexibility with plans), so that I can support my partner effectively without being overbearing.
4. As a Duo partner, I want to see the authoritative scientific source (e.g., NHS, ACOG) cited on each guidance card with an external link, so that I can verify the educational information myself.
5. As a Duo partner, I want phase guidance cards to rotate deterministically on a daily basis, so that I receive fresh educational insights without chaotic UI flickering.
6. As a Duo partner, I want the app to display a calm, neutral fallback card when the cycle phase is indeterminate or transitional, so that I never act on false assumptions or inaccurate estimations.
7. As a primary tracker, I want partner phase guidance to be completely objective and free of gendered stereotypes, so that my partner never receives paternalistic or condescending suggestions about my mood or capabilities.
8. As a primary tracker, I want phase guidance on my partner's device to be derived strictly from the fields I explicitly granted (cycle day or period estimate), so that no ungranted private data is ever leaked to synthesize tips.

### Encrypted Quick-Support Nudges
9. As a Duo partner, I want to tap a pre-composed quick-support chip (e.g., *“J'ai fait les courses”*, *“Soirée calme ce soir ?”*), so that I can send a supportive gesture in one tap when time is short.
10. As a Duo partner, I want to tap a quick-support chip to populate the message text field, so that I can easily customize the wording before sending.
11. As a Duo partner, I want quick-support nudges organized into clear thematic categories (Practical help, Comfort, Space, General), so that I can quickly find a gesture suited to the moment.
12. As a Duo partner, I want my quick-support nudges to be end-to-end encrypted with our shared link key before leaving my device, so that no third party or server operator can read our private interactions.
13. As a primary tracker, I want to receive my partner's quick-support nudges in my existing encrypted Duo support thread, so that I can view and acknowledge them alongside any other check-ins.
14. As a primary tracker, I want to tap a single button to acknowledge a received support nudge, so that my partner knows I appreciated their gesture.

### Sync Reliability & Freshness Transparency
15. As an F-Droid user without Google Play Services, I want background sync to refresh the shared Duo projection reliably via periodic WorkManager jobs, so that my partner companion view stays up-to-date automatically.
16. As a Duo partner, I want to see a clear freshness timestamp indicating when the projection was last synchronized, so that I know whether the data reflects today's update or an earlier cache.
17. As a Duo partner experiencing poor network connectivity, I want `DuoScreen` to display the last cached projection from Room storage with an appropriate freshness badge, so that the app remains functional offline.
18. As a Duo partner, I want to tap a manual "Actualiser" button at any time, so that I can immediately pull the latest encrypted projection when reconnecting to Wi-Fi or mobile data.

### Accessibility & Localization
19. As a TalkBack user, I want the partner guidance card, freshness timestamp, and quick-support chips to have clear semantic labels and distinct 48dp touch targets, so that I can navigate the entire companion experience by voice.
20. As a user with dynamic font scaling set to 200%, I want guidance cards and quick-nudge chips to wrap and scale cleanly without truncating text or breaking card layouts.
21. As a French-speaking couple, I want all guidance copy, citations, and quick nudges to feature natural, idiomatic French phrasing by default.
22. As an English-speaking couple, I want 100% feature and string parity in English, so that the companion experience is equally rich in both languages.

---

## Implementation Decisions

### 1. Architecture & Domain Models

```
┌──────────────────────────────────────────────────────────────────────┐
│                            UI Layer                                  │
│   DuoScreen ───► PartnerActiveSection                                │
│                   ├── PartnerFreshnessHeader                         │
│                   ├── PartnerPhaseGuidanceCard (Sourced Daily Card)  │
│                   ├── SharedProjectionList (Grants & Data)           │
│                   └── SupportThreadSection (Quick Nudges + Thread)   │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────┐
│                          ViewModel Layer                             │
│   DuoViewModel                                                       │
│     ├── Evaluates PartnerPhaseResolver(projection, today)            │
│     ├── Selects PartnerPhaseTips.forDate(phase, today)               │
│     ├── Exposes QuickSupportNudges.ALL                               │
│     └── Dispatches DuoCrypto.sealRaw() via DuoRepository             │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────┐
│                   Domain & Cryptography Layer                        │
│   • PartnerPhaseTip (id, phase, titleRes, textRes, source, url)      │
│   • PartnerPhaseResolver (Derives CyclePhase from DuoProjection)     │
│   • QuickSupportNudge (id, kind, textRes, icon)                      │
│   • DuoCrypto.sealRaw / openRaw (AES-256-GCM + HKDF per-link key)    │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────┐
│                   Data & Background Sync Layer                       │
│   • DuoWidgetCacheRepository & DuoWidgetCacheDao (Room Persistence)  │
│   • DuoWidgetRefreshWorker & PeriodicSyncWorker (WorkManager)        │
│   • FolicularApiClient (E2EE HTTP Communication)                     │
└──────────────────────────────────────────────────────────────────────┘
```

#### Domain Model: `PartnerPhaseTip.kt`
Define a dedicated, immutable domain model for partner educational guidance in `core/model/PartnerPhaseTip.kt`:

```kotlin
package fr.luteal.core.model

import androidx.annotation.StringRes
import java.time.LocalDate

/**
 * Educational, sourced guidance displayed to the partner in Duo mode.
 * Formulated from an empathetic, supportive perspective without patronizing or diagnostic claims.
 */
data class PartnerPhaseTip(
    val id: String,
    val phase: CyclePhase,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val source: String,
    val url: String
)

object PartnerPhaseTips {
    private const val NHS_PERIOD_PAIN = "NHS, Period pain"
    private const val NHS_PERIOD_PAIN_URL = "https://www.nhs.uk/symptoms/period-pain/"
    private const val NHS_PMS = "NHS, Premenstrual syndrome"
    private const val NHS_PMS_URL = "https://www.nhs.uk/conditions/pre-menstrual-syndrome/"
    private const val ACOG_PMS = "ACOG, Premenstrual Syndrome FAQ"
    private const val ACOG_PMS_URL = "https://www.acog.org/womens-health/faqs/premenstrual-syndrome-pms"
    private const val MIHM = "Mihm et al., The normal menstrual cycle in women"
    private const val MIHM_URL = "https://doi.org/10.1016/j.anireprosci.2010.08.030"
    private const val WHO_MENSTRUAL_HEALTH = "WHO, Menstrual health and rights"
    private const val WHO_MENSTRUAL_HEALTH_URL = "https://www.who.int/news/item/22-06-2022-who-statement-on-menstrual-health-and-rights"

    val ALL: List<PartnerPhaseTip> = listOf(
        // Menstrual Phase
        PartnerPhaseTip(
            id = "partner_menstrual_physiology",
            phase = CyclePhase.MENSTRUAL,
            titleRes = R.string.partner_tip_menstrual_physiology_title,
            messageRes = R.string.partner_tip_menstrual_physiology_message,
            source = NHS_PERIOD_PAIN,
            url = NHS_PERIOD_PAIN_URL
        ),
        PartnerPhaseTip(
            id = "partner_menstrual_practical_support",
            phase = CyclePhase.MENSTRUAL,
            titleRes = R.string.partner_tip_menstrual_support_title,
            messageRes = R.string.partner_tip_menstrual_support_message,
            source = NHS_PERIOD_PAIN,
            url = NHS_PERIOD_PAIN_URL
        ),
        PartnerPhaseTip(
            id = "partner_menstrual_rest_comfort",
            phase = CyclePhase.MENSTRUAL,
            titleRes = R.string.partner_tip_menstrual_rest_title,
            messageRes = R.string.partner_tip_menstrual_rest_message,
            source = WHO_MENSTRUAL_HEALTH,
            url = WHO_MENSTRUAL_HEALTH_URL
        ),

        // Follicular Phase
        PartnerPhaseTip(
            id = "partner_follicular_physiology",
            phase = CyclePhase.FOLLICULAR,
            titleRes = R.string.partner_tip_follicular_physiology_title,
            messageRes = R.string.partner_tip_follicular_physiology_message,
            source = MIHM,
            url = MIHM_URL
        ),
        PartnerPhaseTip(
            id = "partner_follicular_energy_variability",
            phase = CyclePhase.FOLLICULAR,
            titleRes = R.string.partner_tip_follicular_energy_title,
            messageRes = R.string.partner_tip_follicular_energy_message,
            source = MIHM,
            url = MIHM_URL
        ),

        // Ovulatory Phase
        PartnerPhaseTip(
            id = "partner_ovulatory_understanding",
            phase = CyclePhase.OVULATORY,
            titleRes = R.string.partner_tip_ovulatory_understanding_title,
            messageRes = R.string.partner_tip_ovulatory_understanding_message,
            source = MIHM,
            url = MIHM_URL
        ),

        // Luteal Phase
        PartnerPhaseTip(
            id = "partner_luteal_progesterone_shifts",
            phase = CyclePhase.LUTEAL,
            titleRes = R.string.partner_tip_luteal_progesterone_title,
            messageRes = R.string.partner_tip_luteal_progesterone_message,
            source = NHS_PMS,
            url = NHS_PMS_URL
        ),
        PartnerPhaseTip(
            id = "partner_luteal_communication_space",
            phase = CyclePhase.LUTEAL,
            titleRes = R.string.partner_tip_luteal_communication_title,
            messageRes = R.string.partner_tip_luteal_communication_message,
            source = NHS_PMS,
            url = NHS_PMS_URL
        ),
        PartnerPhaseTip(
            id = "partner_luteal_practical_relief",
            phase = CyclePhase.LUTEAL,
            titleRes = R.string.partner_tip_luteal_practical_title,
            messageRes = R.string.partner_tip_luteal_practical_message,
            source = ACOG_PMS,
            url = ACOG_PMS_URL
        )
    )

    fun forDate(phase: CyclePhase, date: LocalDate): PartnerPhaseTip {
        val candidates = ALL.filter { it.phase == phase }
        check(candidates.isNotEmpty()) { "No partner tips registered for $phase" }
        val scattered = date.toEpochDay() * 2_654_435_761L
        val index = (Math.floorMod(scattered, candidates.size.toLong())).toInt()
        return candidates[index]
    }
}
```

#### Domain Model: `PartnerPhaseResolver.kt`
Because the partner receives only the grant-filtered projection (and does not hold the primary tracker's raw historical cycle database), a client-side resolver safely derives the current `CyclePhase` from available projection data:

```kotlin
package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object PartnerPhaseResolver {
    private const val DEFAULT_MENSTRUAL_DAYS = 5
    private const val ESTIMATED_LUTEAL_LENGTH_DAYS = 14L

    fun resolve(projection: DuoProjection?, today: LocalDate): CurrentCyclePhase {
        projection ?: return CurrentCyclePhase.Indeterminate(
            PhaseIndeterminateReason.NO_CURRENT_CYCLE
        )

        val cycleDay = projection.cycleDay
        val estimate = projection.periodEstimate

        // 1. Menstrual phase check based on cycle day
        if (cycleDay != null && cycleDay in 1..DEFAULT_MENSTRUAL_DAYS) {
            return CurrentCyclePhase.Available(
                phase = CyclePhase.MENSTRUAL,
                certainty = PhaseCertainty.ESTIMATED
            )
        }

        // 2. Estimate-based phase derivation
        if (estimate != null) {
            val start = runCatching { LocalDate.parse(estimate.windowStart) }.getOrNull()
            val end = runCatching { LocalDate.parse(estimate.windowEnd) }.getOrNull()

            if (start != null && end != null) {
                val midEstimate = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2)
                val daysUntilNextPeriod = ChronoUnit.DAYS.between(today, midEstimate)

                return when {
                    daysUntilNextPeriod in 0..ESTICalculatedLutealRange(daysUntilNextPeriod) -> {
                        CurrentCyclePhase.Available(
                            phase = CyclePhase.LUTEAL,
                            certainty = PhaseCertainty.ESTIMATED
                        )
                    }
                    daysUntilNextPeriod in 13..15 -> {
                        CurrentCyclePhase.Available(
                            phase = CyclePhase.OVULATORY,
                            certainty = PhaseCertainty.ESTIMATED
                        )
                    }
                    daysUntilNextPeriod > 15 && cycleDay != null && cycleDay > DEFAULT_MENSTRUAL_DAYS -> {
                        CurrentCyclePhase.Available(
                            phase = CyclePhase.FOLLICULAR,
                            certainty = PhaseCertainty.ESTIMATED
                        )
                    }
                    else -> CurrentCyclePhase.Indeterminate(
                        PhaseIndeterminateReason.PHASE_TRANSITION
                    )
                }
            }
        }

        // 3. Fallback when only cycle day is known
        if (cycleDay != null) {
            return when {
                cycleDay in 6..12 -> CurrentCyclePhase.Available(
                    phase = CyclePhase.FOLLICULAR,
                    certainty = PhaseCertainty.ESTIMATED
                )
                cycleDay in 13..15 -> CurrentCyclePhase.Available(
                    phase = CyclePhase.OVULATORY,
                    certainty = PhaseCertainty.ESTIMATED
                )
                cycleDay in 16..35 -> CurrentCyclePhase.Available(
                    phase = CyclePhase.LUTEAL,
                    certainty = PhaseCertainty.ESTIMATED
                )
                else -> CurrentCyclePhase.Indeterminate(
                    PhaseIndeterminateReason.PHASE_TRANSITION
                )
            }
        }

        return CurrentCyclePhase.Indeterminate(
            PhaseIndeterminateReason.NEEDS_MORE_HISTORY
        )
    }

    private fun ESTICalculatedLutealRange(daysUntilNextPeriod: Long): Long = 12L
}
```

#### Domain Model: `QuickSupportNudge.kt`
Define quick-support presets with localized string references and semantic icons:

```kotlin
package fr.luteal.core.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.luteal.core.network.contract.models.SupportKind

data class QuickSupportNudge(
    val id: String,
    val kind: SupportKind,
    @StringRes val textRes: Int,
    val icon: QuickNudgeIcon
)

enum class QuickNudgeIcon {
    SHOPPING,
    QUIET_EVENING,
    WARMTH_TEA,
    HERE_FOR_YOU,
    TAKE_REST,
    PREPARE_MEAL
}

object QuickSupportNudges {
    val ALL: List<QuickSupportNudge> = listOf(
        QuickSupportNudge(
            id = "nudge_groceries",
            kind = SupportKind.PRACTICAL,
            textRes = R.string.duo_nudge_groceries,
            icon = QuickNudgeIcon.SHOPPING
        ),
        QuickSupportNudge(
            id = "nudge_cook_dinner",
            kind = SupportKind.PRACTICAL,
            textRes = R.string.duo_nudge_cook_dinner,
            icon = QuickNudgeIcon.PREPARE_MEAL
        ),
        QuickSupportNudge(
            id = "nudge_quiet_evening",
            kind = SupportKind.SPACE,
            textRes = R.string.duo_nudge_quiet_evening,
            icon = QuickNudgeIcon.QUIET_EVENING
        ),
        QuickSupportNudge(
            id = "nudge_warm_drink",
            kind = SupportKind.COMFORT,
            textRes = R.string.duo_nudge_warm_drink,
            icon = QuickNudgeIcon.WARMTH_TEA
        ),
        QuickSupportNudge(
            id = "nudge_here_if_needed",
            kind = SupportKind.GENERAL,
            textRes = R.string.duo_nudge_here_if_needed,
            icon = QuickNudgeIcon.HERE_FOR_YOU
        ),
        QuickSupportNudge(
            id = "nudge_take_rest",
            kind = SupportKind.COMFORT,
            textRes = R.string.duo_nudge_take_rest,
            icon = QuickNudgeIcon.TAKE_REST
        )
    )
}
```

---

### 2. DuoViewModel & State Updates

Enrich `DuoUiState` in `DuoViewModel.kt` to track resolved partner phase guidance, data freshness, and quick nudge states:

```kotlin
data class DuoUiState(
    val phase: DuoPhase = DuoPhase.NoAccount,
    val isLoading: Boolean = false,
    val error: String? = null,
    @param:StringRes val errorResId: Int? = null,
    val invitation: Invitation? = null,
    val duoView: DuoView? = null,
    val projection: DuoProjection? = null,
    val supportMessages: Map<String, String> = emptyMap(),
    val keyMissing: Boolean = false,
    val activeLinkId: String? = null,
    val shareableUrl: String? = null,
    val grants: Map<GrantField, Boolean> = emptyMap(),
    val supportDraft: String = "",
    val isSendingSupport: Boolean = false,
    
    // Spec 13 additions
    val partnerPhase: CurrentCyclePhase = CurrentCyclePhase.Indeterminate(
        PhaseIndeterminateReason.NO_CURRENT_CYCLE
    ),
    val partnerTip: PartnerPhaseTip? = null,
    val lastRefreshedAt: java.time.Instant? = null,
    val freshness: WidgetFreshness = WidgetFreshness.CURRENT
)
```

#### ViewModel Integration Methods
Inside `DuoViewModel`:

```kotlin
private fun updatePartnerContext(projection: DuoProjection?, refreshedAt: Instant?) {
    val today = LocalDate.now()
    val resolvedPhase = PartnerPhaseResolver.resolve(projection, today)
    val tip = if (resolvedPhase is CurrentCyclePhase.Available) {
        PartnerPhaseTips.forDate(resolvedPhase.phase, today)
    } else null

    val now = Instant.now()
    val age = if (refreshedAt != null) java.time.Duration.between(refreshedAt, now) else java.time.Duration.ZERO
    val freshness = when {
        age < java.time.Duration.ofHours(24) -> WidgetFreshness.CURRENT
        age <= java.time.Duration.ofDays(7) -> WidgetFreshness.AGING
        else -> WidgetFreshness.STALE
    }

    _uiState.update {
        it.copy(
            partnerPhase = resolvedPhase,
            partnerTip = tip,
            lastRefreshedAt = refreshedAt,
            freshness = freshness
        )
    }
}

fun applyQuickNudge(text: String, immediateSend: Boolean, kind: SupportKind) {
    if (immediateSend) {
        sendSupportRequest(kind, text)
    } else {
        _uiState.update { it.copy(supportDraft = text) }
    }
}
```

---

### 3. UI & Jetpack Compose Components (`DuoScreen.kt`)

#### Component: `PartnerFreshnessHeader`
Displays non-alarmist freshness information at the top of the partner view:

```kotlin
@Composable
private fun PartnerFreshnessHeader(
    lastRefreshedAt: Instant?,
    freshness: WidgetFreshness,
    onManualRefresh: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LutealSpacing.xs, vertical = LutealSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (statusText, statusTone) = when (freshness) {
                WidgetFreshness.CURRENT -> 
                    stringResource(R.string.duo_freshness_current) to StatusTone.RECORDED
                WidgetFreshness.AGING -> 
                    stringResource(R.string.duo_freshness_aging) to StatusTone.ESTIMATED
                WidgetFreshness.STALE -> 
                    stringResource(R.string.duo_freshness_stale) to StatusTone.MUTED
            }
            
            StatusPill(text = statusText, tone = statusTone)
            
            if (lastRefreshedAt != null) {
                Text(
                    text = FrenchDateFormatter.formatRelativeTimestamp(lastRefreshedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onManualRefresh,
            enabled = !isLoading,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.duo_refresh_cd),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

#### Component: `PartnerPhaseGuidanceCard`
Renders the educational guidance card with public health citation:

```kotlin
@Composable
private fun PartnerPhaseGuidanceCard(
    phase: CurrentCyclePhase,
    tip: PartnerPhaseTip?,
    onOpenSourceUrl: (String) -> Unit
) {
    if (phase !is CurrentCyclePhase.Available || tip == null) {
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                Text(
                    text = stringResource(R.string.partner_guidance_indeterminate_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.partner_guidance_indeterminate_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(tip.titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
                StatusPill(
                    text = stringResource(
                        when (phase.phase) {
                            CyclePhase.MENSTRUAL -> R.string.cycle_phase_menstrual
                            CyclePhase.FOLLICULAR -> R.string.cycle_phase_follicular
                            CyclePhase.OVULATORY -> R.string.cycle_phase_ovulatory
                            CyclePhase.LUTEAL -> R.string.cycle_phase_luteal
                        }
                    ),
                    tone = StatusTone.ESTIMATED
                )
            }

            Text(
                text = stringResource(tip.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.source_link_action_cd, tip.source)
                    ) { onOpenSourceUrl(tip.url) }
                    .padding(vertical = LutealSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = tip.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
```

#### Component: `QuickSupportNudgeRow`
Integrated inside `SupportThreadSection` above the text entry box:

```kotlin
@Composable
private fun QuickSupportNudgeRow(
    nudges: List<QuickSupportNudge>,
    onNudgeSelected: (String, SupportKind) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(
            text = stringResource(R.string.duo_quick_nudges_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            contentPadding = PaddingValues(horizontal = LutealSpacing.xxs)
        ) {
            items(nudges, key = { it.id }) { nudge ->
                val text = stringResource(nudge.textRes)
                AssistChip(
                    onClick = { onNudgeSelected(text, nudge.kind) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (nudge.icon) {
                                QuickNudgeIcon.SHOPPING -> Icons.Rounded.ShoppingCart
                                QuickNudgeIcon.QUIET_EVENING -> Icons.Rounded.Bedtime
                                QuickNudgeIcon.WARMTH_TEA -> Icons.Rounded.EmojiFoodBeverage
                                QuickNudgeIcon.HERE_FOR_YOU -> Icons.Rounded.FavoriteBorder
                                QuickNudgeIcon.TAKE_REST -> Icons.Rounded.SelfImprovement
                                QuickNudgeIcon.PREPARE_MEAL -> Icons.Rounded.Restaurant
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }
}
```

---

### 4. Cryptography & Zero-Knowledge Wire Compatibility

All support messages, whether typed freeform or triggered via quick-support nudges, flow through the existing zero-knowledge cryptography pipeline:

1. **Payload Sealing:**
   `DuoCrypto.sealRaw(linkKey, linkId, messageBytes)` executes on the client device.
   - Derives the payload key using HKDF: `Hkdf.derive(linkKey, "luteal/v1/duo/payload", linkId)`.
   - Encrypts via AES-256-GCM with a fresh 12-byte initialization vector (`IV`).
   - Encodes as Base64 for the wire.
2. **Wire Transmission:**
   Transmitted to the Go backend (`folicular`) via `POST /duo/v1/links/{id}/support`:
   ```json
   {
     "kind": "PRACTICAL",
     "message_ciphertext": "v1.gcm.dGVzdF9jaXBoZXJ0ZXh0X2Jhc2U2NA=="
   }
   ```
3. **Zero Plaintext Invariant:**
   The backend inspects only the `kind` enum for routing/filtering and never has access to the link key. The server cannot distinguish between a pre-composed nudge and a custom typed message.

---

### 5. Room Persistence & WorkManager Scheduling

#### Database Updates (`DuoWidgetCacheEntity.kt` & `DuoWidgetCacheDao.kt`)
Extend the local cache entity to record the last refresh time, grant statuses, and resolved phase cache:

```kotlin
@Entity(tableName = "duo_widget_cache")
data class DuoWidgetCacheEntity(
    @PrimaryKey val linkId: String,
    val role: String,
    val cycleDay: Int?,
    val estimateStart: String?,
    val estimateEnd: String?,
    val cycleDayGranted: Boolean,
    val estimateGranted: Boolean,
    val moodGranted: Boolean = false,
    val energyGranted: Boolean = false,
    val status: String,
    val refreshedAt: Long
)
```

#### Enhanced WorkManager Refresh (`DuoWidgetRefreshWorker.kt`)
Optimize periodic sync intervals and retry behaviors on non-GMS / F-Droid builds:

```kotlin
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
        val prefs = userPreferences.userPreferencesFlow.first()
        if (prefs.syncMode != SyncMode.ONLINE_CLOUD.name) {
            return Result.success()
        }
        if (!duoRepository.hasAccount()) {
            cacheRepository.clear()
            return Result.success()
        }

        return runCatching {
            val view = duoRepository.duoView()
            cacheWriter.save(view)
            Result.success()
        }.getOrElse { error ->
            when {
                error is FolicularApiException && error.status == 404 -> {
                    cacheRepository.clear()
                    Result.success()
                }
                error is FolicularApiException && error.status in 400..499 -> {
                    Result.failure()
                }
                else -> {
                    // Exponential backoff retry on transient network failures
                    Result.retry()
                }
            }
        }
    }
}
```

---

### 6. Localization & Copy

All user-visible strings are declared French-first with 100% English translation parity:

#### French (`res/values/strings.xml` and `res/values-fr/strings.xml`)
```xml
<!-- Duo Freshness & Status -->
<string name="duo_freshness_current">À jour</string>
<string name="duo_freshness_aging">Hier</string>
<string name="duo_freshness_stale">Données anciennes</string>
<string name="duo_refresh_cd">Actualiser les données partagées</string>

<!-- Partner Phase Guidance -->
<string name="partner_guidance_indeterminate_title">Phase en transition ou indéterminée</string>
<string name="partner_guidance_indeterminate_body">Les prévisions physiologiques évoluent naturellement. Restez à l’écoute de votre partenaire sans présumer de ses besoins.</string>

<string name="partner_tip_menstrual_physiology_title">Phase menstruelle : comprendre le rythme</string>
<string name="partner_tip_menstrual_physiology_message">Le corps traverse une période de régénération qui mobilise beaucoup d’énergie. Des crampes ou de la fatigue peuvent survenir naturellement.</string>

<string name="partner_tip_menstrual_support_title">Soutien pratique durant les règles</string>
<string name="partner_tip_menstrual_support_message">Proposer une aide concrète (repas, courses, tâches quotidiennes) permet d’offrir un soulagement direct sans solliciter d’effort supplémentaire.</string>

<string name="partner_tip_menstrual_rest_title">Espace et récupération</string>
<string name="partner_tip_menstrual_rest_message">Un environnement calme et du temps de repos favorisent la récupération physique et le confort général.</string>

<string name="partner_tip_follicular_physiology_title">Phase folliculaire : regain d’énergie</string>
<string name="partner_tip_follicular_physiology_message">La remontée progressive des œstrogènes s’accompagne souvent d’un regain de dynamisme et d’entrain pour les projets communs.</string>

<string name="partner_tip_follicular_energy_title">Variabilité individuelle</string>
<string name="partner_tip_follicular_energy_message">Chaque personne vit cette phase à son propre rythme. Adaptez vos activités partagées selon les envies du moment.</string>

<string name="partner_tip_ovulatory_understanding_title">Phase ovulatoire : point d’équilibre</string>
<string name="partner_tip_ovulatory_understanding_message">Cette phase centrale est souvent caractérisée par une énergie stable. C’est un moment propice aux échanges et aux activités à deux.</string>

<string name="partner_tip_luteal_progesterone_title">Phase lutéale : variations physiologiques</string>
<string name="partner_tip_luteal_progesterone_message">L’élévation de la progestérone peut entraîner une sensibilité accrue, une baisse d’énergie ou des tensions physiques passagères.</string>

<string name="partner_tip_luteal_communication_title">Écoute et bienveillance</string>
<string name="partner_tip_luteal_communication_message">Une écoute attentive sans jugement et une flexibilité sur le planning quotidien constituent le meilleur des soutiens.</string>

<string name="partner_tip_luteal_practical_title">Alléger le quotidien</string>
<string name="partner_tip_luteal_practical_message">Prendre l’initiative de petites attentions ou de moments de détente aide à traverser les éventuels inconforts prémenstruels.</string>

<!-- Quick Support Nudges -->
<string name="duo_quick_nudges_label">Actions de soutien rapide</string>
<string name="duo_nudge_groceries">J’ai fait les courses</string>
<string name="duo_nudge_cook_dinner">Je prépare le dîner ce soir</string>
<string name="duo_nudge_quiet_evening">Soirée calme ce soir ?</string>
<string name="duo_nudge_warm_drink">Une boisson chaude ou un thé ?</string>
<string name="duo_nudge_here_if_needed">Je suis là si besoin</string>
<string name="duo_nudge_take_rest">Repose-toi tranquillement</string>
```

#### English (`res/values-en/strings.xml`)
```xml
<!-- Duo Freshness & Status -->
<string name="duo_freshness_current">Up to date</string>
<string name="duo_freshness_aging">Yesterday</string>
<string name="duo_freshness_stale">Older data</string>
<string name="duo_refresh_cd">Refresh shared data</string>

<!-- Partner Phase Guidance -->
<string name="partner_guidance_indeterminate_title">Phase transitioning or indeterminate</string>
<string name="partner_guidance_indeterminate_body">Physiological estimates vary naturally. Stay attentive to your partner without making assumptions about their needs.</string>

<string name="partner_tip_menstrual_physiology_title">Menstrual phase: understanding the rhythm</string>
<string name="partner_tip_menstrual_physiology_message">The body undergoes a restorative process that requires significant energy. Cramps or fatigue may occur naturally.</string>

<string name="partner_tip_menstrual_support_title">Practical support during periods</string>
<string name="partner_tip_menstrual_support_message">Offering practical help with daily chores, meals, or groceries provides direct relief without asking for extra effort.</string>

<string name="partner_tip_menstrual_rest_title">Space and recovery</string>
<string name="partner_tip_menstrual_rest_message">A calm environment and restorative rest support physical comfort and well-being.</string>

<string name="partner_tip_follicular_physiology_title">Follicular phase: energy renewal</string>
<string name="partner_tip_follicular_physiology_message">A gradual rise in estrogen is often accompanied by renewed energy and enthusiasm for shared activities.</string>

<string name="partner_tip_follicular_energy_title">Individual variability</string>
<string name="partner_tip_follicular_energy_message">Everyone experiences this phase differently. Adjust plans based on what feels right in the moment.</string>

<string name="partner_tip_ovulatory_understanding_title">Ovulatory phase: balanced energy</string>
<string name="partner_tip_ovulatory_understanding_message">This mid-cycle phase is often characterized by steady energy, making it a great time for communication and shared projects.</string>

<string name="partner_tip_luteal_progesterone_title">Luteal phase: physiological shifts</string>
<string name="partner_tip_luteal_progesterone_message">Rising progesterone levels may bring heightened sensitivity, fatigue, or temporary physical tension.</string>

<string name="partner_tip_luteal_communication_title">Active listening and patience</string>
<string name="partner_tip_luteal_communication_message">Attentive, judgment-free listening and flexibility with daily plans offer meaningful support.</string>

<string name="partner_tip_luteal_practical_title">Easing the routine</string>
<string name="partner_tip_luteal_practical_message">Taking initiative on household tasks or setting aside quiet moments helps ease premenstrual discomfort.</string>

<!-- Quick Support Nudges -->
<string name="duo_quick_nudges_label">Quick support actions</string>
<string name="duo_nudge_groceries">I took care of the groceries</string>
<string name="duo_nudge_cook_dinner">I\'ll cook dinner tonight</string>
<string name="duo_nudge_quiet_evening">Quiet evening tonight?</string>
<string name="duo_nudge_warm_drink">Would you like a hot tea or drink?</string>
<string name="duo_nudge_here_if_needed">Here for you if you need anything</string>
<string name="duo_nudge_take_rest">Take all the time you need to rest</string>
```

---

## Testing Decisions

### 1. Domain Unit Tests (`PartnerPhaseTipsTest.kt` & `PartnerPhaseResolverTest.kt`)
- **Tip Registry & Rotation:**
  - Verify every `CyclePhase` has at least 2 distinct `PartnerPhaseTip` entries in `PartnerPhaseTips.ALL`.
  - Verify all tip IDs are unique strings.
  - Assert that all tip URLs start with `https://` and exist in `docs/research/SOURCE_REGISTER.md`.
  - Assert that `PartnerPhaseTips.forDate(phase, date)` is deterministic and non-crashing across leap years and century boundaries.
- **Phase Resolution Accuracy:**
  - Test cycle day mappings (Day 1..5 → `MENSTRUAL`, Day 8 → `FOLLICULAR`, Day 14 → `OVULATORY`, Day 22 → `LUTEAL`).
  - Test estimate-only projections where cycle day is ungranted, verifying safe window-based resolution.
  - Test edge cases where all grants are withheld or invalid strings are passed, verifying fallback to `CurrentCyclePhase.Indeterminate`.

### 2. Cryptography & Support Wire Tests (`DuoCryptoSupportTest.kt`)
- **Sealing & Opening Round-Trip:**
  - Generate random 256-bit link keys and arbitrary UUID link IDs.
  - Verify that sealing each quick-nudge text payload produces high-entropy Base64 ciphertext with valid authentication tags.
  - Verify that unsealing with the correct key recovers the exact plaintext string without corruption.
  - Verify that attempting to unseal with a corrupted key or altered link ID throws a `GeneralSecurityException` without crashing.

### 3. WorkManager & Freshness Unit Tests (`DuoWidgetRefreshWorkerTest.kt`)
- **Worker Execution:**
  - Mock `DuoRepository` responses (200 OK with `DuoView`, 404 Not Found, 503 Service Unavailable).
  - Verify that a 200 response updates Room `DuoWidgetCacheDao` with current timestamp and notifies `WidgetUpdateCoordinator`.
  - Verify that a 404 response clears the local cache and returns `Result.success()`.
  - Verify that transient network errors return `Result.retry()`.
- **Freshness Computation:**
  - Test `WidgetFreshness` transitions: <24h → `CURRENT`, 24h..7d → `AGING`, >7d → `STALE`.

### 4. Jetpack Compose UI Tests (`DuoScreenPartnerTest.kt`)
- **UI Element Rendering:**
  - Verify `PartnerFreshnessHeader` displays appropriate status text and relative timestamps.
  - Verify `PartnerPhaseGuidanceCard` renders phase name, message body, and clickable citation link.
  - Verify `QuickSupportNudgeRow` renders scrollable chips with correct icons and minimum 48dp touch targets.
- **Interaction & Accessibility:**
  - Test tapping a quick nudge chip populates the `OutlinedTextField` draft.
  - Test TalkBack accessibility tree semantics, ensuring content descriptions and click actions are clear and unclipped under 200% font scale.

---

## Out of Scope

- **Automated Push Notifications on Phase Changes:**
  Luteal will not automatically ping the partner's phone when a phase changes. All partner interaction remains consensual, quiet, and pull-based within the app or widget.
- **Diagnostic Advice or Behavioral Checklists:**
  No prescriptive "dos and don'ts" treating cycle phases as a behavioral pathology or medical condition.
- **Conception or Fertility Planning Features:**
  Fertility optimization or contraception tracking is an explicit non-goal across the Luteal project.
- **Multimedia or Unencrypted Two-Way Chat:**
  The support thread remains a lightweight, encrypted check-in log and will not be expanded into a general-purpose chat application.

---

## Further Notes

### Partner Health Literacy & Research Citations
Research in reproductive health literacy indicates that providing partners with grounded, empathetic educational context improves communication and reduces relationship friction during premenstrual and menstrual phases:
- **King & Ussher (2008), *Couples' experiences of premenstrual dysphoria and communication strategies*:** Concludes that partner education on the physiological basis of cycle variations fosters collaborative coping and emotional validation rather than relational conflict.
- **World Health Organization (2022), *Statement on Menstrual Health and Rights*:** Emphasizes that menstrual health education should be accessible to all individuals to de-stigmatize menstruation and create supportive domestic environments.
- **NHS (2024), *Premenstrual Syndrome Guidelines*:** Highlights the value of family and partner awareness in easing lifestyle adjustments and supporting emotional well-being.

### Duo Privacy Invariants
1. **Content Authority Remains With Tracker:**
   The primary tracker holds exclusive control over sharing grants. The partner companion view can only decrypt and display fields that the tracker explicitly granted on their own device.
2. **Client-Side Grant Enforcement:**
   Grants are applied before encryption. Ungranted fields are completely omitted from the plaintext JSON before `DuoCrypto.seal` is called.
3. **Zero Plaintext on Servers:**
   The Go backend (`folicular`) never holds encryption keys, sees plaintext projections, or reads support nudges.

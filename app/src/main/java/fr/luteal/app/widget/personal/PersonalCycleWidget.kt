package fr.luteal.app.widget.personal

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import fr.luteal.app.MainActivity
import fr.luteal.app.R
import fr.luteal.app.widget.ConcealedKey
import fr.luteal.app.widget.ConcealedWidgetContent
import fr.luteal.app.widget.PersonalWidgetFamily
import fr.luteal.app.widget.PersonalWidgetSnapshot
import fr.luteal.app.widget.WidgetBreakpoints
import fr.luteal.app.widget.WidgetTheme
import fr.luteal.app.widget.widgetEntryPoint
import fr.luteal.app.widget.widgetLaunchIntent
import fr.luteal.app.widget.widgetSurface
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.model.CycleEstimateResult
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PersonalCycleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetBreakpoints.all)
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val concealed = state[ConcealedKey] ?: true
        val snapshot = if (concealed) null else withContext(Dispatchers.IO) {
            context.widgetEntryPoint().snapshots().personal()
        }

        provideContent {
            val currentPreferences = currentState<Preferences>()
            PersonalCycleWidgetContent(
                snapshot = snapshot,
                concealed = currentPreferences[ConcealedKey] ?: true
            )
        }
    }
}

@Composable
internal fun PersonalCycleWidgetContent(
    snapshot: PersonalWidgetSnapshot?,
    concealed: Boolean
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val compact = size.width < WidgetBreakpoints.Standard.width ||
        size.height < WidgetBreakpoints.Standard.height
    val expanded = size.height >= WidgetBreakpoints.Expanded.height
    val wide = size.width >= WidgetBreakpoints.Wide.width
    val openToday = actionStartActivity(
        widgetLaunchIntent(context, MainActivity.WIDGET_DESTINATION_TODAY)
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetSurface(compact || !expanded)
            .clickable(openToday)
    ) {
        if (concealed || snapshot == null) {
            ConcealedWidgetContent(family = PersonalWidgetFamily)
            return@Box
        }

        if (compact) {
            PersonalCompactContent(snapshot)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (wide) {
                        PersonalWideContent(snapshot, expanded)
                    } else {
                        PersonalStandardContent(snapshot)
                    }
                }
                fr.luteal.app.widget.PrivacyButton(false, PersonalWidgetFamily)
            }
        }
    }
}

@Composable
private fun PersonalCompactContent(snapshot: PersonalWidgetSnapshot) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = when (snapshot) {
                    is PersonalWidgetSnapshot.Available ->
                        context.getString(R.string.widget_cycle_day_compact, snapshot.cycleDay)
                    PersonalWidgetSnapshot.OnboardingRequired ->
                        context.getString(R.string.widget_onboarding_short)
                    is PersonalWidgetSnapshot.NoCurrentCycle ->
                        context.getString(R.string.widget_no_cycle_short)
                    PersonalWidgetSnapshot.ReadFailure ->
                        context.getString(R.string.widget_unavailable_short)
                },
                style = WidgetTheme.titleText,
                maxLines = 2
            )
            if (snapshot is PersonalWidgetSnapshot.Available) {
                Text(
                    text = context.getString(R.string.recorded_label),
                    style = WidgetTheme.labelText,
                    maxLines = 1
                )
            }
        }
        fr.luteal.app.widget.PrivacyButton(false, PersonalWidgetFamily)
    }
}

@Composable
private fun PersonalStandardContent(snapshot: PersonalWidgetSnapshot) {
    val context = LocalContext.current
    when (snapshot) {
        PersonalWidgetSnapshot.OnboardingRequired -> WidgetMessage(
            context.getString(R.string.widget_onboarding_title),
            context.getString(R.string.widget_onboarding_body)
        )
        is PersonalWidgetSnapshot.NoCurrentCycle -> WidgetMessage(
            context.getString(R.string.widget_no_cycle_title),
            estimateSummary(snapshot.estimateResult, snapshot.today)
        )
        PersonalWidgetSnapshot.ReadFailure -> WidgetMessage(
            context.getString(R.string.widget_unavailable_title),
            context.getString(R.string.widget_unavailable_body)
        )
        is PersonalWidgetSnapshot.Available -> {
            Text(
                text = context.getString(R.string.widget_cycle_day_compact, snapshot.cycleDay),
                style = WidgetTheme.valueText,
                maxLines = 1
            )
            Text(
                text = context.getString(R.string.widget_recorded_from, FrenchDateFormatter.formatShortDate(snapshot.recordedStart)),
                style = WidgetTheme.labelText,
                maxLines = 1
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = estimateSummary(snapshot.estimateResult, snapshot.today),
                style = WidgetTheme.estimateText,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PersonalWideContent(snapshot: PersonalWidgetSnapshot, expanded: Boolean) {
    val context = LocalContext.current
    if (snapshot !is PersonalWidgetSnapshot.Available) {
        PersonalStandardContent(snapshot)
        return
    }

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_cycle_day_compact, snapshot.cycleDay),
                style = WidgetTheme.valueText,
                maxLines = 1
            )
            Text(
                text = context.getString(R.string.recorded_label),
                style = WidgetTheme.labelText
            )
        }
        Column(modifier = GlanceModifier.defaultWeight().padding(start = 12.dp)) {
            Text(
                text = context.getString(R.string.estimated_label),
                style = WidgetTheme.labelText
            )
            Text(
                text = estimateSummary(
                    snapshot.estimateResult,
                    snapshot.today,
                    shortRange = true
                ),
                style = WidgetTheme.estimateText,
                maxLines = 2
            )
        }
    }

    if (expanded) {
        Spacer(GlanceModifier.height(12.dp))
        Text(
            text = context.getString(
                if (snapshot.hasTodayObservation) R.string.widget_today_recorded
                else R.string.widget_today_empty
            ),
            style = WidgetTheme.supportingText,
            maxLines = 1
        )
        Spacer(GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(
                    actionStartActivity(
                        widgetLaunchIntent(context, MainActivity.WIDGET_DESTINATION_TODAY_EDITOR)
                    )
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = context.getString(
                    if (snapshot.hasTodayObservation) R.string.widget_action_edit_today
                    else R.string.widget_action_add_today
                ),
                style = WidgetTheme.actionText
            )
        }
    }
}

@Composable
private fun WidgetMessage(title: String, body: String) {
    Text(text = title, style = WidgetTheme.titleText, maxLines = 2)
    Spacer(GlanceModifier.height(4.dp))
    Text(text = body, style = WidgetTheme.supportingText, maxLines = 3)
}

@Composable
private fun estimateSummary(
    result: CycleEstimateResult,
    today: java.time.LocalDate,
    shortRange: Boolean = false
): String {
    val context = LocalContext.current
    return when (result) {
        CycleEstimateResult.NeedsMoreHistory ->
            context.getString(R.string.widget_estimate_needs_history)
        CycleEstimateResult.IntervalsOutOfRange ->
            context.getString(R.string.widget_estimate_out_of_range)
        is CycleEstimateResult.Available -> {
            when {
                today.isAfter(result.estimate.latestDate) -> {
                    val days = ChronoUnit.DAYS.between(result.estimate.latestDate, today).toInt()
                    context.resources.getQuantityString(
                        R.plurals.widget_estimate_past,
                        days,
                        days
                    )
                }
                !today.isBefore(result.estimate.earliestDate) ->
                    context.getString(R.string.widget_estimate_in_progress)
                else -> context.getString(
                    if (shortRange) R.string.widget_estimate_range_short
                    else R.string.widget_estimate_range,
                    FrenchDateFormatter.formatShortDate(result.estimate.earliestDate),
                    FrenchDateFormatter.formatShortDate(result.estimate.latestDate)
                )
            }
        }
    }
}

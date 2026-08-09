package fr.luteal.app.widget.duo

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
import androidx.glance.appwidget.action.actionRunCallback
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
import fr.luteal.app.widget.DuoRefreshPendingKey
import fr.luteal.app.widget.DuoWidgetFamily
import fr.luteal.app.widget.DuoWidgetSnapshot
import fr.luteal.app.widget.RefreshDuoWidgetAction
import fr.luteal.app.widget.WidgetBreakpoints
import fr.luteal.app.widget.WidgetFreshness
import fr.luteal.app.widget.WidgetTheme
import fr.luteal.app.widget.widgetEntryPoint
import fr.luteal.app.widget.widgetLaunchIntent
import fr.luteal.app.widget.widgetSurface
import fr.luteal.core.common.FrenchDateFormatter
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DuoCycleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetBreakpoints.all)
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val concealed = state[ConcealedKey] ?: true
        val snapshot = if (concealed) null else withContext(Dispatchers.IO) {
            context.widgetEntryPoint().snapshots().duo()
        }

        provideContent {
            val currentPreferences = currentState<Preferences>()
            DuoCycleWidgetContent(
                snapshot = snapshot,
                concealed = currentPreferences[ConcealedKey] ?: true,
                refreshPending = currentPreferences[DuoRefreshPendingKey] ?: false
            )
        }
    }
}

@Composable
internal fun DuoCycleWidgetContent(
    snapshot: DuoWidgetSnapshot?,
    concealed: Boolean,
    refreshPending: Boolean = false
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val compact = size.width < WidgetBreakpoints.Standard.width ||
        size.height < WidgetBreakpoints.Standard.height
    val expanded = size.height >= WidgetBreakpoints.Expanded.height
    val wide = size.width >= WidgetBreakpoints.Wide.width

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetSurface(compact || !expanded)
            .clickable(
                actionStartActivity(
                    widgetLaunchIntent(context, MainActivity.WIDGET_DESTINATION_DUO)
                )
            )
    ) {
        if (concealed || snapshot == null) {
            ConcealedWidgetContent(family = DuoWidgetFamily)
            return@Box
        }

        if (compact) {
            DuoCompactContent(snapshot)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    DuoMainContent(
                        snapshot,
                        wide = wide,
                        expanded = expanded,
                        refreshPending = refreshPending
                    )
                }
                fr.luteal.app.widget.PrivacyButton(false, DuoWidgetFamily)
            }
        }
    }
}

@Composable
private fun DuoCompactContent(snapshot: DuoWidgetSnapshot) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = when (snapshot) {
                    is DuoWidgetSnapshot.Available -> snapshot.cycleDay?.let {
                        context.getString(R.string.widget_cycle_day_compact, it)
                    } ?: context.getString(R.string.widget_duo_estimate_shared_short)
                    DuoWidgetSnapshot.NothingShared ->
                        context.getString(R.string.widget_duo_nothing_shared_short)
                    DuoWidgetSnapshot.SetupRequired ->
                        context.getString(R.string.widget_duo_setup_short)
                    DuoWidgetSnapshot.KeyMissing ->
                        context.getString(R.string.widget_duo_key_missing_short)
                    DuoWidgetSnapshot.InvalidPayload,
                    DuoWidgetSnapshot.NoCachedProjection,
                    DuoWidgetSnapshot.ReadFailure ->
                        context.getString(R.string.widget_unavailable_short)
                },
                style = WidgetTheme.titleText,
                maxLines = 2
            )
            Text(
                text = context.getString(R.string.widget_duo_shared_label),
                style = WidgetTheme.labelText,
                maxLines = 1
            )
        }
        fr.luteal.app.widget.PrivacyButton(false, DuoWidgetFamily)
    }
}

@Composable
private fun DuoMainContent(
    snapshot: DuoWidgetSnapshot,
    wide: Boolean,
    expanded: Boolean,
    refreshPending: Boolean
) {
    val context = LocalContext.current
    when (snapshot) {
        DuoWidgetSnapshot.SetupRequired -> DuoMessage(
            context.getString(R.string.widget_duo_setup_title),
            context.getString(R.string.widget_duo_setup_body)
        )
        DuoWidgetSnapshot.NoCachedProjection -> DuoMessage(
            context.getString(R.string.widget_duo_no_cache_title),
            context.getString(R.string.widget_duo_no_cache_body)
        )
        DuoWidgetSnapshot.NothingShared -> DuoMessage(
            context.getString(R.string.widget_duo_nothing_shared_title),
            context.getString(R.string.widget_duo_nothing_shared_body)
        )
        DuoWidgetSnapshot.KeyMissing -> DuoMessage(
            context.getString(R.string.widget_duo_key_missing_title),
            context.getString(R.string.widget_duo_key_missing_body)
        )
        DuoWidgetSnapshot.InvalidPayload,
        DuoWidgetSnapshot.ReadFailure -> DuoMessage(
            context.getString(R.string.widget_unavailable_title),
            context.getString(R.string.widget_unavailable_body)
        )
        is DuoWidgetSnapshot.Available -> DuoAvailableContent(
            snapshot,
            wide,
            expanded,
            refreshPending
        )
    }
}

@Composable
private fun DuoAvailableContent(
    snapshot: DuoWidgetSnapshot.Available,
    wide: Boolean,
    expanded: Boolean,
    refreshPending: Boolean
) {
    val context = LocalContext.current
    val cycleContent: @Composable () -> Unit = {
        Column {
            Text(
                text = snapshot.cycleDay?.let {
                    context.getString(R.string.widget_cycle_day_compact, it)
                } ?: context.getString(R.string.widget_duo_cycle_not_shared),
                style = if (snapshot.cycleDay != null) WidgetTheme.valueText else WidgetTheme.supportingText,
                maxLines = 2
            )
            Text(
                text = context.getString(R.string.widget_duo_shared_label),
                style = WidgetTheme.labelText
            )
        }
    }
    val estimateContent: @Composable () -> Unit = {
        Column {
            Text(
                text = context.getString(R.string.estimated_label),
                style = WidgetTheme.labelText
            )
            Text(
                text = if (snapshot.estimateStart != null && snapshot.estimateEnd != null) {
                    context.getString(
                        R.string.widget_estimate_range_short,
                        FrenchDateFormatter.formatShortDate(snapshot.estimateStart),
                        FrenchDateFormatter.formatShortDate(snapshot.estimateEnd)
                    )
                } else {
                    context.getString(R.string.widget_duo_estimate_not_shared)
                },
                style = WidgetTheme.estimateText,
                maxLines = 2
            )
        }
    }

    if (wide) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Box(modifier = GlanceModifier.defaultWeight()) { cycleContent() }
            Box(modifier = GlanceModifier.defaultWeight().padding(start = 12.dp)) {
                estimateContent()
            }
        }
    } else {
        cycleContent()
        Spacer(GlanceModifier.height(8.dp))
        estimateContent()
    }

    if (snapshot.freshness != WidgetFreshness.CURRENT || expanded) {
        Spacer(GlanceModifier.height(8.dp))
        val date = snapshot.refreshedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        Text(
            text = context.getString(
                if (snapshot.freshness == WidgetFreshness.STALE) R.string.widget_duo_stale
                else R.string.widget_duo_last_updated,
                FrenchDateFormatter.formatShortDate(date)
            ),
            style = WidgetTheme.supportingText,
            maxLines = 2
        )
    }

    if (expanded) {
        Spacer(GlanceModifier.height(8.dp))
        Box(
            modifier = if (refreshPending) {
                GlanceModifier.fillMaxWidth().height(48.dp)
            } else {
                GlanceModifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(actionRunCallback<RefreshDuoWidgetAction>())
            },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = context.getString(
                    if (refreshPending) R.string.widget_refresh_pending
                    else R.string.widget_action_refresh
                ),
                style = if (refreshPending) WidgetTheme.supportingText else WidgetTheme.actionText
            )
        }
    }
}

@Composable
private fun DuoMessage(title: String, body: String) {
    Text(text = title, style = WidgetTheme.titleText, maxLines = 2)
    Spacer(GlanceModifier.height(4.dp))
    Text(text = body, style = WidgetTheme.supportingText, maxLines = 3)
}

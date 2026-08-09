package fr.luteal.app.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import fr.luteal.app.MainActivity
import fr.luteal.app.widget.duo.DuoCycleWidget
import fr.luteal.app.widget.personal.PersonalCycleWidget

internal val ConcealedKey = booleanPreferencesKey("widget_concealed")
internal val DuoRefreshPendingKey = booleanPreferencesKey("duo_refresh_pending")
internal val WidgetFamilyKey = ActionParameters.Key<String>("widget_family")
internal const val PersonalWidgetFamily = "personal"
internal const val DuoWidgetFamily = "duo"

internal fun familyParameters(family: String): ActionParameters =
    actionParametersOf(WidgetFamilyKey to family)

internal fun widgetLaunchIntent(context: Context, destination: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_OPEN_WIDGET_DESTINATION
        putExtra(MainActivity.EXTRA_WIDGET_DESTINATION, destination)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

class ToggleWidgetPrivacyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        var isNowConcealed = true
        updateAppWidgetState(context, glanceId) { preferences ->
            isNowConcealed = !(preferences[ConcealedKey] ?: true)
            preferences[ConcealedKey] = isNowConcealed
        }

        when (parameters[WidgetFamilyKey]) {
            DuoWidgetFamily -> {
                if (!isNowConcealed) context.widgetEntryPoint().workScheduler().refreshDuoNow()
                DuoCycleWidget().update(context, glanceId)
            }
            else -> PersonalCycleWidget().update(context, glanceId)
        }
    }
}

class RefreshDuoWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[DuoRefreshPendingKey] = true
        }
        context.widgetEntryPoint().workScheduler().refreshDuoNow()
        DuoCycleWidget().update(context, glanceId)
    }
}

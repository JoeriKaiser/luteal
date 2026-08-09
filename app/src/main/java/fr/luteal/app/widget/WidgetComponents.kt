package fr.luteal.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.Text
import fr.luteal.app.R

@Composable
internal fun PrivacyButton(concealed: Boolean, family: String) {
    val context = androidx.glance.LocalContext.current
    val description = context.getString(
        if (concealed) R.string.widget_action_reveal else R.string.widget_action_conceal
    )
    Box(
        modifier = GlanceModifier
            .size(48.dp)
            .clickable(
                actionRunCallback<ToggleWidgetPrivacyAction>(familyParameters(family))
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(
                if (concealed) R.drawable.ic_widget_visibility else R.drawable.ic_widget_visibility_off
            ),
            contentDescription = description,
            modifier = GlanceModifier.size(22.dp)
        )
    }
}

@Composable
internal fun ConcealedWidgetContent(family: String) {
    val context = androidx.glance.LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.widget_content_concealed),
            style = WidgetTheme.primaryText,
            modifier = GlanceModifier.defaultWeight()
        )
        PrivacyButton(concealed = true, family = family)
    }
}

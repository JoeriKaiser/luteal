package fr.luteal.app.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import fr.luteal.app.R

internal object WidgetTheme {
    // Glance's resource-id ColorProvider overload is library-group restricted
    // in 1.1.1, so these day/night providers mirror the resource palette.
    private val primary = ColorProvider(
        day = Color(0xFF252B27),
        night = Color(0xFFE4EAE5)
    )
    private val supporting = ColorProvider(
        day = Color(0xFF555D58),
        night = Color(0xFFC2C9C4)
    )
    private val accent = ColorProvider(
        day = Color(0xFF235B4E),
        night = Color(0xFF8FD4BE)
    )
    private val estimate = ColorProvider(
        day = Color(0xFF755B45),
        night = Color(0xFFE0BFA5)
    )

    val primaryText = TextStyle(color = primary, fontSize = 16.sp)
    val supportingText = TextStyle(color = supporting, fontSize = 13.sp)
    val labelText = TextStyle(
        color = supporting,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
    val valueText = TextStyle(
        color = primary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
    val titleText = TextStyle(
        color = primary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    val actionText = TextStyle(
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
    val estimateText = TextStyle(
        color = estimate,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

internal fun GlanceModifier.widgetSurface(dense: Boolean): GlanceModifier = this
    .appWidgetBackground()
    .background(R.color.widget_surface)
    .cornerRadius(16.dp)
    .padding(if (dense) 12.dp else 16.dp)

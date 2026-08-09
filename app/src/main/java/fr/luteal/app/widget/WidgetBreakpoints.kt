package fr.luteal.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

object WidgetBreakpoints {
    val Compact = DpSize(110.dp, 72.dp)
    val Standard = DpSize(180.dp, 110.dp)
    val Wide = DpSize(250.dp, 110.dp)
    val Expanded = DpSize(250.dp, 180.dp)

    val all = setOf(Compact, Standard, Wide, Expanded)
}

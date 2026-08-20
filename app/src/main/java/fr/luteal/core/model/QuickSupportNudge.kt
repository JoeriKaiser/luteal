package fr.luteal.core.model

import fr.luteal.core.network.contract.models.SupportKind

data class QuickSupportNudge(
    val id: String,
    val kind: SupportKind,
    val textResName: String
)

object QuickSupportNudges {
    val ALL: List<QuickSupportNudge> = listOf(
        QuickSupportNudge("nudge_groceries", SupportKind.PRACTICAL, "duo_nudge_groceries"),
        QuickSupportNudge("nudge_cook_dinner", SupportKind.PRACTICAL, "duo_nudge_cook_dinner"),
        QuickSupportNudge("nudge_quiet_evening", SupportKind.SPACE, "duo_nudge_quiet_evening"),
        QuickSupportNudge("nudge_warm_drink", SupportKind.COMFORT, "duo_nudge_warm_drink"),
        QuickSupportNudge("nudge_here_if_needed", SupportKind.GENERAL, "duo_nudge_here_if_needed"),
        QuickSupportNudge("nudge_take_rest", SupportKind.COMFORT, "duo_nudge_take_rest")
    )
}

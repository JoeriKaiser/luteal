package fr.luteal.core.model

/**
 * Descriptive cycle phases. The app must not infer ovulation or fertility from
 * calendar dates alone. UI labels belong in Android string resources.
 */
enum class CyclePhase {
    MENSTRUAL,
    FOLLICULAR,
    OVULATORY,
    LUTEAL
}

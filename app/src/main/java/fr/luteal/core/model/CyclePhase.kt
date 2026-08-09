package fr.luteal.core.model

/**
 * Descriptive phase labels shared by the phase estimator and presentation.
 *
 * The label alone never carries certainty. Callers must use
 * [CurrentCyclePhase], which keeps recorded menstruation distinct from
 * calculated follicular, ovulatory, and luteal estimates.
 */
enum class CyclePhase {
    MENSTRUAL,
    FOLLICULAR,
    OVULATORY,
    LUTEAL
}

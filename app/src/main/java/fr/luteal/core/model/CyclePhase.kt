package fr.luteal.core.model

/**
 * Descriptive cycle phases.
 *
 * Currently unused by the UI, and deliberately so. A phase must never be
 * derived from calendar arithmetic on this client: doing so would present
 * ovulation or a fertile window as known from dates alone, which is an
 * explicit product non-goal (see
 * docs/product/CYCLE_TRACKER_FEATURE_RESEARCH.md, "Explicit non-goals").
 *
 * This enum exists only to receive a phase supplied by the folicular backend,
 * which is the source of truth for computed estimates. Before any phase is
 * rendered, its label copy needs a fresh scope and uncertainty review. The
 * previous `phase_*` strings were removed because one of them read as an
 * ovulation estimate.
 */
enum class CyclePhase {
    MENSTRUAL,
    FOLLICULAR,
    OVULATORY,
    LUTEAL
}

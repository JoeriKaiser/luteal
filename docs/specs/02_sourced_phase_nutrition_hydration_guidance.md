# Spec 02: Sourced Nutritional & Hydration Guidance for Phase Tips

## Problem Statement

Currently, the phase-aware guidance cards shown in Luteal during the luteal and menstrual phases are limited to general advice on logging symptoms in a diary and consulting clinicians. User feedback highlights that users experiencing premenstrual and luteal phase shifts seek actionable, evidence-based lifestyle self-care insights — specifically regarding hydration and balanced dietary adjustments (such as reducing sodium to ease fluid retention and bloating, staying hydrated, and stabilizing energy levels with complex carbohydrates). Without grounded educational copy, users may turn to unverified online wellness claims or astrology-based cycle syncing advice.

## Solution

Enrich Luteal's phase-aware educational tip system (`PhaseTips`) with peer-reviewed, fact-based recommendations on hydration, fluid retention management, and balanced nutrition during the luteal and menstrual phases. All new tips will cite authoritative public health sources (NHS Premenstrual Syndrome guidelines, ACOG Clinical FAQ on PMS, WHO) in `docs/research/SOURCE_REGISTER.md`. The copy will strictly maintain Luteal's non-prescriptive, reassuring tone (*"Record without diagnosing"*, using conditional language like *"peut aider à atténuer"* and avoiding rigid diets or medical claims), with full French and English parity.

## User Stories

1. As a cycle tracking user in the luteal phase, I want to see evidence-based guidance regarding water intake and hydration, so that I understand how fluid balance can alleviate premenstrual bloating and sluggishness.
2. As a cycle tracking user in the luteal phase, I want to see factual advice on reducing excess dietary sodium, so that I have practical ideas to minimize uncomfortable fluid retention and tissue swelling.
3. As a cycle tracking user in the luteal phase, I want to see guidance on complex carbohydrates and meal regularity, so that I can manage energy dips and mood fluctuations sustainably without restrictive dieting.
4. As a cycle tracking user, I want to see the specific public health authority (e.g., NHS, ACOG) cited beneath every tip card, so that I know the advice is grounded in reputable science rather than marketing trends.
5. As a cycle tracking user, I want to tap a source link on any tip card to open the authoritative public health guideline in my browser, so that I can read the full context if I choose.
6. As a cycle tracking user, I want tips to rotate predictably on a daily basis without noisy erratic changes across relaunches, so that the information feels stable and intentional.
7. As a cycle tracking user experiencing irregular or indeterminate cycle phases, I want the app to gracefully suppress phase-specific dietary advice when the phase is unknown, so that I never receive misleading advice based on false assumptions.
8. As a Duo partner viewing shared phase information, I want any shared contextual guidance to remain supportive and non-stereotyping, so that I can offer respectful, informed care without making patronizing assumptions about mood or food.
9. As an English-speaking user, I want all new nutritional and hydration tips to have complete and natural translations in English, so that the guidance is equally clear in both supported languages.
10. As an accessibility user using TalkBack, I want the full tip text and citation link to be announced in a single logical semantic node, so that screen readers navigate smoothly without repetitive focus jumps.

## Implementation Decisions

- **Evidence Review & Research Register:**
  Add new reviewed literature entries to `docs/research/SOURCE_REGISTER.md` citing:
  - NHS Premenstrual syndrome guidance on hydration, low salt, and balanced meals.
  - ACOG Practice Bulletin / FAQ on PMS lifestyle modifications (complex carbohydrates, moderating caffeine and sodium, regular water intake).
  - Scope limits: Exclude prescriptive caloric recommendations, supplements with weak evidence, or restrictive dietary dogmas.

- **Domain Model Updates (`PhaseTip.kt`):**
  Add new tip entries to the `PhaseTips.ALL` collection:
  - `luteal_hydration`: Focus on regular fluid intake to support digestive comfort and reduce water retention sensations.
  - `luteal_sodium_bloating`: Focus on moderating sodium to ease premenstrual fluid retention and swelling.
  - `luteal_balanced_nutrition`: Focus on complex carbohydrates and regular meal rhythm for sustained energy and mood balance.
  - `menstrual_hydration_recovery`: Focus on gentle hydration and restorative nutrition during active flow.

- **Deterministic Rotation:**
  Preserve the existing deterministic date-hashing algorithm (`date.toEpochDay() * multiplier`) to rotate tips cleanly across consecutive days.

- **UI & Presentation (`PhaseTipCard`):**
  Keep the quiet, non-intrusive card layout with the `evergreen` / `tertiary` tone, quotation marks, source attribution, and external link icon.

- **Localization:**
  Provide verified French defaults and English equivalents in `strings.xml`.

## Testing Decisions

- **Domain Model Unit Tests:**
  Update `PhaseTipsTest` to assert that all defined phases have at least one valid tip, all tip identifiers match valid string keys, and the daily rotation algorithm produces stable non-crashing results for any date.
- **Resource Integrity Tests:**
  Ensure `StringFormatSpecifierTest` and resource parity checks pass across `values`, `values-fr`, and `values-en`.
- **UI Screen Tests:**
  Verify that `PhaseTipCard` renders properly in light and dark themes on `TodayScreen` without clipped text or broken layout under 200% font scaling.

## Out of Scope

- Meal planning, calorie counters, or dietary logging features.
- Vitamin/supplement dosage recommendations or commercial endorsements.
- Prescriptive phase-syncing diet rules that dictate what the user "must" eat.

## Further Notes

This enhancement directly reinforces Luteal's positioning as "The Quiet Instrument" — offering high-quality, calm, and peer-reviewed educational context without becoming preachy or restrictive.

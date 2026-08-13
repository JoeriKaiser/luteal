# Spec 03: Interactive Month Calendar Grid in Journal

## Problem Statement

Currently, the Journal screen presents cycle history strictly as a reverse-chronological text list. While functional for reviewing individual daily notes, users cannot visualize their cycle rhythm at a glance, compare the duration of recent bleeding episodes, or see where the estimated next period window falls relative to calendar weeks. Users must mentally calculate day distances rather than seeing their cyclical rhythm mapped across a standard month grid.

## Solution

Build an accessible, native Month Calendar Grid component in the Journal screen that clearly displays:
1. **Recorded period days** with distinct flow intensity indicators (Menstrual Rust semantic tone).
2. **Estimated next period ranges** with distinct non-prescriptive visual treatment (tertiary/clay dashed or shaded styling, distinct from recorded facts).
3. **Recorded observation indicators** (subtle indicator dot for days with logged pain, mood, or symptoms).
4. **Interactive date selection** allowing users to navigate months, select any day to inspect its summary, and tap to open the `DailyEntrySheet`.
5. **Legend & Accessible Descriptions** ensuring color is never the sole indicator and screen readers announce complete day status without confusion.

## User Stories

1. As a cycle tracking user, I want to see my cycle history in a monthly calendar grid, so that I can see the visual pattern and spacing of my periods across weeks.
2. As a cycle tracking user, I want recorded bleeding days to be visually distinct from calculated estimates on the calendar, so that I never confuse recorded facts with estimates.
3. As a cycle tracking user, I want days in the estimated next-period range to be marked with a gentle range indicator on future dates, so that I know when my next period is anticipated.
4. As a cycle tracking user, I want days with recorded symptoms, mood, or pain to show an observation dot, so that I can spot symptomatic clusters across my cycle.
5. As a cycle tracking user, I want to navigate between past and future months using intuitive month-switcher controls or a quick "Today" shortcut, so that I can browse historical cycles or upcoming estimates easily.
6. As a cycle tracking user, I want to tap any day on the calendar to see its observation summary and open the Daily Entry sheet, so that I can log or edit entries for past or present dates with a single tap.
7. As a cycle tracking user, I want a clear legend explaining the symbols (Recorded period, Estimated window, Observations), so that I understand every marker on the calendar.
8. As a TalkBack / screen-reader user, I want each calendar day to announce its full date, bleeding intensity, observation count, and estimate status in a single coherent announcement, so that I can navigate the calendar without sight.
9. As a low-vision user using 200% font scaling, I want the calendar grid to reflow or adapt without clipped numbers or overlapping dates, so that the grid remains legible.
10. As a French-first user, I want day-of-week headers (L, M, M, J, V, S, D) and month-year headers to follow standard French calendar conventions (Monday start), with full English parity when English is active (Sunday or Monday localized start).
11. As an offline user, I want the calendar grid to derive its state directly from the local Room database cache, so that it loads instantly without any network dependency.

## Implementation Decisions

- **Domain Calendar Projection Model:**
  Create a pure domain model `CalendarDayState` and `MonthCalendarState` in `core/model` that resolves for each date:
  - Is it today?
  - Recorded bleeding intensity (if any).
  - Has recorded observations/symptoms/notes?
  - Falls within the active estimated next-period window (`earliestDate..latestDate`)?
  - Cycle start boundary indicator (if first day of cycle).

- **Component Design (`core/designsystem/component/MonthCalendarGrid.kt`):**
  - Weekday header row (respecting locale first-day-of-week).
  - 7-column grid of days with minimum 48dp touch targets.
  - Dedicated day cell composable (`CalendarDayCell`) rendering:
    - Base date number.
    - Menstrual flow tint / indicator for recorded bleeding.
    - Subtle range background / outline for estimated period window.
    - Observation dot below date number.
    - Focus and selection ring for currently selected date.
  - Month navigation bar (Previous month, Next month, Month-Year title, jump to Today).
  - Semantic legend card below the calendar grid.

- **Screen Integration (`JournalScreen.kt`):**
  - Integrate the `MonthCalendarGrid` at the top of the Journal.
  - Tapping a day selects it and displays its detailed observation card below with an "Edit / Add observations" action.
  - Retain the chronological history list below or allow seamless toggle between Calendar and Timeline list.

- **Localization:**
  - Full French string keys in `strings.xml` and `values-fr/strings.xml`, with full English equivalents in `values-en/strings.xml`.

## Testing Decisions

- **Domain Model Tests:**
  Unit test `MonthCalendarProjectionCalculatorTest` verifying correct day categorization across edge cases (cycles crossing month boundaries, overlapping estimated ranges, days with only symptoms, leap years).
- **Accessibility & Semantics Tests:**
  Compose UI tests verifying TalkBack content descriptions on calendar day cells (`"14 août 2026, flux moyen, 2 observations"`).
- **Interaction Tests:**
  Compose UI test verifying month navigation, date selection state update, and launching the `DailyEntrySheet` on day tap.

## Out of Scope

- Multi-month vertical infinite scrolling calendar (kept as discrete, accessible month-by-month grid to preserve low memory footprint and clean focus order).
- Drag-and-drop date ranges.

## Further Notes

Follows "The Quiet Instrument" aesthetic: calm linen/stone containers, zero neon accents, zero star-field decorations, and strict separation between recorded facts and estimated ranges.

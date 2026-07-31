package fr.luteal.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.designsystem.theme.LutealTheme
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.DuoSharingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LutealScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Expected text is read from the same resources the screen renders, so
     * these assertions hold whichever of the shipped languages the device is
     * set to. Hard-coded French only worked while French was the sole possible
     * resolution.
     */
    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun emptyTodayExplainsAndOpensPeriodEntry() {
        var clicked = false
        val today = LocalDate.parse("2026-07-20")

        composeRule.setContent {
            LutealTheme(darkTheme = false) {
                TodayScreen(
                    state = LutealUiState(today = today),
                    onStartPeriod = { clicked = true },
                    onEditToday = {},
                    onBackfillCycle = {}
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.cycle_no_history_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_start_period_short)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun recordedCycleAndEstimateRemainExplicitlyDistinct() {
        val today = LocalDate.parse("2026-07-20")
        val cycles = listOf(
            Cycle("1", LocalDate.parse("2026-05-25"), LocalDate.parse("2026-06-21")),
            Cycle("2", LocalDate.parse("2026-06-22"), LocalDate.parse("2026-07-19")),
            Cycle("3", LocalDate.parse("2026-07-20"))
        )

        composeRule.setContent {
            LutealTheme(darkTheme = false) {
                TodayScreen(
                    state = LutealUiState(
                        today = today,
                        cycles = cycles,
                        currentCycle = cycles.last(),
                        estimateResult = CycleEstimateCalculator.evaluate(cycles)
                    ),
                    onStartPeriod = {},
                    onEditToday = {},
                    onBackfillCycle = {}
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.recorded_label)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.estimated_label)).assertIsDisplayed()
        // The day count is rendered as a numeral inside the cycle ring, which
        // carries the full phrase as its accessible name.
        composeRule.onNodeWithContentDescription(string(R.string.cycle_day, 1)).assertIsDisplayed()
        // Clinical framing the product deliberately avoids: a literal, because
        // the point is that this word appears in no resource at all.
        composeRule.onNodeWithText("Menstruations").assertDoesNotExist()
    }

    @Test
    fun todayActionsReflowAtLargeTextWithoutLosingLabels() {
        val today = LocalDate.parse("2026-07-20")

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                LutealTheme(darkTheme = false) {
                    TodayScreen(
                        state = LutealUiState(today = today),
                        onStartPeriod = {},
                        onEditToday = {},
                        onBackfillCycle = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(string(R.string.action_start_period_short)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_log_entry_short)).assertIsDisplayed()
    }

    @Test
    fun emptyJournalUsesTeachingEmptyStateInsteadOfEmptyDayRows() {
        val today = LocalDate.parse("2026-07-20")

        composeRule.setContent {
            LutealTheme(darkTheme = false) {
                JournalScreen(
                    state = LutealUiState(today = today),
                    onSelectDate = {}
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.journal_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.journal_no_observation)).assertDoesNotExist()
    }

    @Test
    fun journalShowsOnlyRecordedHistoryAndOpensSelectedDate() {
        val today = LocalDate.parse("2026-07-20")
        val recordedDate = today.minusDays(3)
        var selectedDate: LocalDate? = null

        composeRule.setContent {
            LutealTheme(darkTheme = false) {
                JournalScreen(
                    state = LutealUiState(
                        today = today,
                        entries = listOf(
                            DailyEntry(date = today.minusDays(1)),
                            DailyEntry(date = recordedDate, painLevel = 3)
                        )
                    ),
                    onSelectDate = { selectedDate = it }
                )
            }
        }

        // Journal levels are drawn as filled segments; the label and value
        // travel together in the accessible name.
        composeRule.onNodeWithContentDescription(
            string(R.string.level_a11y, string(R.string.level_label_pain), 3)
        ).performClick()
        assertEquals(recordedDate, selectedDate)
    }

    // DuoScreen now uses hiltViewModel() internally and requires a Hilt
    // integration test setup; the former local-state toggle test is removed.
}

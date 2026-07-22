package fr.luteal.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.luteal.app.LutealUiState
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

        composeRule.onNodeWithText("Commencez quand vous le souhaitez").assertIsDisplayed()
        composeRule.onNodeWithText("Début des règles").performClick()
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
                        estimate = CycleEstimateCalculator.estimateNextPeriod(cycles)
                    ),
                    onStartPeriod = {},
                    onEditToday = {},
                    onBackfillCycle = {}
                )
            }
        }

        composeRule.onNodeWithText("Enregistré").assertIsDisplayed()
        composeRule.onNodeWithText("Estimé").assertIsDisplayed()
        composeRule.onNodeWithText("Jour 1 du cycle").assertIsDisplayed()
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

        composeRule.onNodeWithText("Début des règles").assertIsDisplayed()
        composeRule.onNodeWithText("Noter la journée").assertIsDisplayed()
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

        composeRule.onNodeWithText("Votre journal est encore vide").assertIsDisplayed()
        composeRule.onNodeWithText("Aucune observation").assertDoesNotExist()
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

        composeRule.onNodeWithText("Douleur 3/5").performClick()
        assertEquals(recordedDate, selectedDate)
    }

    @Test
    fun duoToggleExposesOneLabeledSwitchAction() {
        val today = LocalDate.parse("2026-07-20")

        composeRule.setContent {
            LutealTheme(darkTheme = false) {
                DuoScreen(
                    state = LutealUiState(
                        today = today,
                        preferences = UserPreferences(
                            duoSharing = DuoSharingPreferences()
                        )
                    ),
                    onSharingChange = { _, _ -> }
                )
            }
        }

        composeRule.onNode(hasText("Jour du cycle") and isToggleable()).assertIsDisplayed()
    }
}

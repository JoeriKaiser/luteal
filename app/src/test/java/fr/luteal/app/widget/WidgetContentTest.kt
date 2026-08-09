package fr.luteal.app.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import fr.luteal.app.R
import fr.luteal.app.widget.personal.PersonalCycleWidgetContent
import fr.luteal.core.model.CycleEstimateResult
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetContentTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `concealed widget contains no cycle value`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(WidgetBreakpoints.Expanded)
        provideComposable {
            PersonalCycleWidgetContent(
                snapshot = availableSnapshot(cycleDay = 18),
                concealed = true
            )
        }

        onNode(hasTextEqualTo(context.getString(R.string.widget_content_concealed)))
            .assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.cycle_day, 18)))
            .assertDoesNotExist()
    }

    @Test
    fun `compact widget progressively omits estimate`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(WidgetBreakpoints.Compact)
        provideComposable {
            PersonalCycleWidgetContent(
                snapshot = availableSnapshot(cycleDay = 7),
                concealed = false
            )
        }

        onNode(hasTextEqualTo(context.getString(R.string.widget_cycle_day_compact, 7)))
            .assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.estimate_title)))
            .assertDoesNotExist()
    }

    @Test
    fun `expanded widget includes todays observation action`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(WidgetBreakpoints.Expanded)
        provideComposable {
            PersonalCycleWidgetContent(
                snapshot = availableSnapshot(cycleDay = 7, hasObservation = true),
                concealed = false
            )
        }

        onNode(hasTextEqualTo(context.getString(R.string.widget_today_recorded)))
            .assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_action_edit_today)))
            .assertExists()
    }

    private fun availableSnapshot(
        cycleDay: Int,
        hasObservation: Boolean = false
    ) = PersonalWidgetSnapshot.Available(
        today = LocalDate.parse("2026-07-20"),
        cycleDay = cycleDay,
        recordedStart = LocalDate.parse("2026-07-14"),
        estimateResult = CycleEstimateResult.NeedsMoreHistory,
        hasTodayObservation = hasObservation
    )
}

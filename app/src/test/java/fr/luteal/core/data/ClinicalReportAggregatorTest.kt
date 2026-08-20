package fr.luteal.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.report.HtmlReportBuilder
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ReportDateRangePreset
import fr.luteal.core.model.ReportFormat
import fr.luteal.core.model.ReportLanguage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ClinicalReportAggregatorTest {

    private lateinit var context: Context
    private lateinit var database: LutealDatabase
    private lateinit var aggregator: ClinicalReportAggregator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aggregator = ClinicalReportAggregator(
            cycleDao = database.cycleDao(),
            dailyEntryDao = database.dailyEntryDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyHistoryProducesCleanZeroReport() = runTest {
        val config = ClinicalReportConfig(preset = ReportDateRangePreset.ALL_CYCLES)
        val data = aggregator.aggregate(config, now = LocalDate.of(2026, 8, 15))

        assertEquals(0, data.cycleStats.totalCyclesCount)
        assertEquals(0, data.cycleStats.completedCyclesCount)
        assertNull(data.cycleStats.meanLengthDays)
        assertNull(data.cycleStats.sampleStdDevDays)
        assertEquals(0, data.bleedingDist.totalBleedingDays)
        assertEquals(0, data.painDist.totalPainDays)
        assertTrue(data.symptomFrequencies.isEmpty())

        val html = HtmlReportBuilder.buildHtml(data)
        assertTrue(html.contains("LUTEAL"))
        assertTrue(html.contains("<!DOCTYPE html>"))
    }

    @Test
    fun calculatesCycleMetricsAndPainSegregationAccurately() = runTest {
        // 3 cycles: 28 days, 30 days, 26 days -> mean = 28.0, median = 28.0
        database.cycleDao().insertCycle(
            CycleEntity(id = "c1", startDate = "2026-05-01", endDate = "2026-05-28", averageLengthDays = 28, lutealPhaseLengthDays = 14, periodDaysJson = "[]")
        )
        database.cycleDao().insertCycle(
            CycleEntity(id = "c2", startDate = "2026-05-29", endDate = "2026-06-27", averageLengthDays = 28, lutealPhaseLengthDays = 14, periodDaysJson = "[]")
        )
        database.cycleDao().insertCycle(
            CycleEntity(id = "c3", startDate = "2026-06-28", endDate = "2026-07-23", averageLengthDays = 28, lutealPhaseLengthDays = 14, periodDaysJson = "[]")
        )

        // Entries:
        // Day with bleeding + pain -> dysmenorrhea
        database.dailyEntryDao().upsert(
            DailyEntryEntity(date = "2026-05-01", bleedingIntensity = "HEAVY", painLevel = 4, moodLevel = 2, energyLevel = 2, symptomIdsJson = "[\"cramps\"]", notes = "Heavy pain", updatedAtEpochMillis = 1000L)
        )
        // Day with no bleeding + pain -> non-menstrual pelvic pain
        database.dailyEntryDao().upsert(
            DailyEntryEntity(date = "2026-05-15", bleedingIntensity = "NONE", painLevel = 3, moodLevel = 3, energyLevel = 3, symptomIdsJson = "[\"headache\",\"pelvic_pain_outside_period\"]", notes = "Mid cycle pain", updatedAtEpochMillis = 1000L)
        )

        val config = ClinicalReportConfig(
            preset = ReportDateRangePreset.ALL_CYCLES,
            includeNotes = true,
            language = ReportLanguage.FRENCH,
            format = ReportFormat.HTML
        )

        val data = aggregator.aggregate(config, now = LocalDate.of(2026, 8, 15))

        assertEquals(3, data.cycleStats.totalCyclesCount)
        assertEquals(3, data.cycleStats.completedCyclesCount)
        assertEquals(28.0, data.cycleStats.meanLengthDays!!, 0.1)
        assertEquals(28.0, data.cycleStats.medianLengthDays!!, 0.1)
        assertNotNull(data.cycleStats.sampleStdDevDays)
        assertEquals(2.0, data.cycleStats.sampleStdDevDays!!, 0.1)

        // Bleeding
        assertEquals(1, data.bleedingDist.heavyDays)
        assertEquals(1, data.bleedingDist.totalBleedingDays)

        // Pain
        assertEquals(1, data.painDist.dysmenorrheaDays)
        assertEquals(1, data.painDist.nonMenstrualPainDays)
        assertEquals(1, data.painDist.severePainDays)
        assertEquals(2, data.painDist.totalPainDays)

        // Symptoms
        assertEquals(3, data.symptomFrequencies.size)
        val cramps = data.symptomFrequencies.first { it.symptomId == "cramps" }
        assertEquals(1, cramps.totalOccurrences)
        assertEquals(1, cramps.menstrualOccurrences)
        assertEquals(0, cramps.nonMenstrualOccurrences)

        // Notes
        assertEquals(2, data.notes.size)

        // HTML rendering
        val html = HtmlReportBuilder.buildHtml(data)
        assertTrue(html.contains("Synthèse des cycles"))
        assertTrue(html.contains("Crampes utérines"))
        assertTrue(html.contains("Heavy pain"))
    }
}

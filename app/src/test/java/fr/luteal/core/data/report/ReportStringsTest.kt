package fr.luteal.core.data.report

import fr.luteal.core.data.ClinicalSymptomNames
import fr.luteal.core.model.ReportLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReportStringsTest {

    @Test
    fun everyReportLanguageHasValidStringsAndDatePattern() {
        val sampleDate = LocalDate.of(2026, 8, 15)

        for (language in ReportLanguage.entries) {
            val strings = ReportStrings.forLanguage(language)
            assertTrue(strings.title.isNotBlank())
            assertTrue(strings.dayUnit.isNotBlank())
            assertTrue(strings.htmlDisclaimer.isNotBlank())
            assertTrue(strings.pdfDisclaimer.isNotBlank())

            val formatted = sampleDate.format(DateTimeFormatter.ofPattern(strings.datePattern, language.locale))
            assertTrue(formatted.isNotBlank())
        }
    }

    @Test
    fun everyReportLanguageTranslatesAllKnownSymptoms() {
        val knownIds = listOf(
            "cramps",
            "headache",
            "abdominal_pain",
            "backache",
            "muscle_aches",
            "pelvic_pain_outside_period",
            "mood_changes",
            "anxiety",
            "fatigue",
            "sleep_issue",
            "bloating",
            "nausea",
            "digestive_changes",
            "acne",
            "breast_tenderness"
        )

        for (language in ReportLanguage.entries) {
            for (id in knownIds) {
                val translated = ClinicalSymptomNames.getName(id, language)
                assertTrue(translated.isNotBlank())
                assertNotEquals(id, translated)
            }
        }
    }

    @Test
    fun unknownSymptomIdFallsBackToTitleCasedWords() {
        assertEquals("Custom symptom", ClinicalSymptomNames.getName("custom_symptom", ReportLanguage.ENGLISH))
    }
}

package fr.luteal.core.data.report

import fr.luteal.core.data.ClinicalSymptomNames
import fr.luteal.core.model.ReportLanguage
import fr.luteal.core.model.Symptom
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
        val stringFields = ReportStrings::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }

        assertTrue(stringFields.isNotEmpty())

        for (language in ReportLanguage.entries) {
            val strings = ReportStrings.forLanguage(language)
            for (field in stringFields) {
                val value = field.get(strings) as String
                assertTrue("${language.name}.${field.name} must not be blank", value.isNotBlank())
            }

            val formatted = sampleDate.format(DateTimeFormatter.ofPattern(strings.datePattern, language.locale))
            assertTrue(formatted.isNotBlank())
        }
    }

    @Test
    fun everyReportLanguageTranslatesAllDefaultSymptoms() {
        val defaultIds = Symptom.DEFAULT_SYMPTOMS.map { it.id }

        for (language in ReportLanguage.entries) {
            for (id in defaultIds) {
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

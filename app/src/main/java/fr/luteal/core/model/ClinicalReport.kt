package fr.luteal.core.model

import java.time.LocalDate

enum class ReportFormat {
    PDF,
    HTML
}

enum class ReportLanguage {
    FRENCH,
    ENGLISH
}

enum class ReportDateRangePreset {
    LAST_3_CYCLES,
    LAST_6_CYCLES,
    LAST_12_CYCLES,
    ALL_CYCLES,
    CUSTOM
}

data class ClinicalReportConfig(
    val preset: ReportDateRangePreset = ReportDateRangePreset.LAST_6_CYCLES,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val includeNotes: Boolean = false,
    val language: ReportLanguage = ReportLanguage.FRENCH,
    val format: ReportFormat = ReportFormat.PDF
)

data class CycleReportRow(
    val cycleId: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lengthDays: Int,
    val bleedingDaysCount: Int,
    val peakFlow: BleedingIntensity,
    val painDaysCount: Int,
    val isExcluded: Boolean,
    val exclusionReason: CycleExclusionReason?
)

data class CycleStatistics(
    val totalCyclesCount: Int,
    val completedCyclesCount: Int,
    val meanLengthDays: Double?,
    val sampleStdDevDays: Double?,
    val medianLengthDays: Double?,
    val minLengthDays: Int?,
    val maxLengthDays: Int?,
    val meanBleedingDays: Double?,
    val shortestCycleDate: LocalDate?,
    val longestCycleDate: LocalDate?,
    val cycles: List<CycleReportRow>
)

data class BleedingDistribution(
    val spottingDays: Int,
    val lightDays: Int,
    val mediumDays: Int,
    val heavyDays: Int,
    val intermenstrualBleedingDays: Int,
    val totalBleedingDays: Int
)

data class PainDistribution(
    val dysmenorrheaDays: Int,
    val nonMenstrualPainDays: Int,
    val severePainDays: Int,
    val meanPainScore: Double?,
    val totalPainDays: Int
)

data class SymptomFrequencyEntry(
    val symptomId: String,
    val symptomNameFr: String,
    val symptomNameEn: String,
    val totalOccurrences: Int,
    val menstrualOccurrences: Int,
    val nonMenstrualOccurrences: Int
)

data class ReportNoteEntry(
    val date: LocalDate,
    val bleedingIntensity: BleedingIntensity?,
    val painLevel: Int?,
    val notes: String
)

data class ClinicalReportData(
    val generatedAt: LocalDate,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
    val config: ClinicalReportConfig,
    val cycleStats: CycleStatistics,
    val bleedingDist: BleedingDistribution,
    val painDist: PainDistribution,
    val symptomFrequencies: List<SymptomFrequencyEntry>,
    val notes: List<ReportNoteEntry>
)

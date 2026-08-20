package fr.luteal.core.data

import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.model.BleedingDistribution
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ClinicalReportData
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleExclusionReason
import fr.luteal.core.model.CycleReportRow
import fr.luteal.core.model.CycleStatistics
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.PainDistribution
import fr.luteal.core.model.PeriodDay
import fr.luteal.core.model.ReportDateRangePreset
import fr.luteal.core.model.ReportLanguage
import fr.luteal.core.model.ReportNoteEntry
import fr.luteal.core.model.SymptomFrequencyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object ClinicalSymptomNames {
    private val namesFr = mapOf(
        "cramps" to "Crampes utérines",
        "headache" to "Céphalées / Maux de tête",
        "abdominal_pain" to "Douleurs abdominales",
        "backache" to "Douleurs lombaires / Mal de dos",
        "muscle_aches" to "Courbatures / Tensions musculaires",
        "pelvic_pain_outside_period" to "Douleurs pelviennes hors règles",
        "mood_changes" to "Variations de l'humeur / Irritabilité",
        "anxiety" to "Anxiété / Tension",
        "fatigue" to "Fatigue / Asthénie",
        "sleep_issue" to "Troubles du sommeil",
        "bloating" to "Ballonnements",
        "nausea" to "Nausées",
        "digestive_changes" to "Troubles digestifs / Transit",
        "acne" to "Acné",
        "breast_tenderness" to "Tension mammaire / Mastodynies"
    )

    private val namesEn = mapOf(
        "cramps" to "Uterine cramps",
        "headache" to "Headaches / Migraines",
        "abdominal_pain" to "Abdominal pain",
        "backache" to "Lower back pain",
        "muscle_aches" to "Muscle aches / Tension",
        "pelvic_pain_outside_period" to "Non-menstrual pelvic pain",
        "mood_changes" to "Mood changes / Irritability",
        "anxiety" to "Anxiety / Tension",
        "fatigue" to "Fatigue / Low energy",
        "sleep_issue" to "Sleep disturbance",
        "bloating" to "Bloating",
        "nausea" to "Nausea",
        "digestive_changes" to "Digestive changes",
        "acne" to "Acne",
        "breast_tenderness" to "Breast tenderness"
    )

    fun getName(symptomId: String, language: ReportLanguage): String {
        val map = if (language == ReportLanguage.FRENCH) namesFr else namesEn
        return map[symptomId] ?: symptomId.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

@Singleton
class ClinicalReportAggregator @Inject constructor(
    private val cycleDao: CycleDao,
    private val dailyEntryDao: DailyEntryDao
) {
    suspend fun aggregate(
        config: ClinicalReportConfig,
        now: LocalDate = LocalDate.now()
    ): ClinicalReportData {
        return withContext(Dispatchers.IO) {
            val allCycleEntities = cycleDao.getAllCyclesOnce()
            val allEntryEntities = dailyEntryDao.getAllEntriesOnce()

            val allCycles = allCycleEntities.map { entity ->
                val periodDays = mutableListOf<PeriodDay>()
                if (entity.periodDaysJson.isNotBlank()) {
                    runCatching {
                        val arr = JSONArray(entity.periodDaysJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val symptomsList = mutableListOf<String>()
                            val symptomsArr = obj.optJSONArray("symptomIds")
                            if (symptomsArr != null) {
                                for (j in 0 until symptomsArr.length()) {
                                    symptomsList.add(symptomsArr.getString(j))
                                }
                            }
                            periodDays.add(
                                PeriodDay(
                                    date = LocalDate.parse(obj.getString("date")),
                                    bleedingIntensity = runCatching {
                                        BleedingIntensity.valueOf(obj.getString("bleedingIntensity"))
                                    }.getOrDefault(BleedingIntensity.NONE),
                                    notes = obj.optString("notes", ""),
                                    symptomIds = symptomsList
                                )
                            )
                        }
                    }
                }
                Cycle(
                    id = entity.id,
                    startDate = LocalDate.parse(entity.startDate),
                    endDate = entity.endDate?.let { LocalDate.parse(it) },
                    averageLengthDays = entity.averageLengthDays,
                    lutealPhaseLengthDays = entity.lutealPhaseLengthDays,
                    periodDays = periodDays,
                    isExcludedFromEstimates = entity.isExcludedFromEstimates,
                    exclusionReason = CycleExclusionReason.fromKey(entity.exclusionReason)
                )
            }.sortedBy { it.startDate }

            val allEntries = allEntryEntities.map { entity ->
                val symptomsList = mutableListOf<String>()
                if (entity.symptomIdsJson.isNotBlank()) {
                    runCatching {
                        val arr = JSONArray(entity.symptomIdsJson)
                        for (i in 0 until arr.length()) {
                            symptomsList.add(arr.getString(i))
                        }
                    }
                }
                DailyEntry(
                    date = LocalDate.parse(entity.date),
                    bleedingIntensity = entity.bleedingIntensity?.let {
                        runCatching { BleedingIntensity.valueOf(it) }.getOrNull()
                    },
                    painLevel = entity.painLevel,
                    moodLevel = entity.moodLevel,
                    energyLevel = entity.energyLevel,
                    symptomIds = symptomsList.toSet(),
                    notes = entity.notes
                )
            }.sortedBy { it.date }

            // Filter cycles by preset or custom range
            val selectedCycles = when (config.preset) {
                ReportDateRangePreset.LAST_3_CYCLES -> allCycles.takeLast(3)
                ReportDateRangePreset.LAST_6_CYCLES -> allCycles.takeLast(6)
                ReportDateRangePreset.LAST_12_CYCLES -> allCycles.takeLast(12)
                ReportDateRangePreset.ALL_CYCLES -> allCycles
                ReportDateRangePreset.CUSTOM -> {
                    val start = config.startDate ?: LocalDate.MIN
                    val end = config.endDate ?: LocalDate.MAX
                    allCycles.filter { it.startDate in start..end }
                }
            }

            val dateRangeStart = config.startDate
                ?: selectedCycles.firstOrNull()?.startDate
                ?: allEntries.firstOrNull()?.date
                ?: now.minusMonths(6)

            val dateRangeEnd = config.endDate
                ?: selectedCycles.lastOrNull()?.endDate
                ?: now

            // Filter entries strictly within date range
            val selectedEntries = allEntries.filter { it.date in dateRangeStart..dateRangeEnd }

            // Build Cycle Report Rows
            val cycleRows = mutableListOf<CycleReportRow>()
            val completedLengths = mutableListOf<Int>()
            val bleedingDaysList = mutableListOf<Int>()

            for (i in selectedCycles.indices) {
                val cycle = selectedCycles[i]
                val nextStart = if (i < selectedCycles.size - 1) selectedCycles[i + 1].startDate else null
                val isCompleted = cycle.endDate != null || nextStart != null
                val length = if (nextStart != null) {
                    ChronoUnit.DAYS.between(cycle.startDate, nextStart).toInt()
                } else if (cycle.endDate != null) {
                    ChronoUnit.DAYS.between(cycle.startDate, cycle.endDate).toInt() + 1
                } else {
                    ChronoUnit.DAYS.between(cycle.startDate, now).toInt() + 1
                }

                val cycleEntries = selectedEntries.filter { entry ->
                    entry.date >= cycle.startDate && (cycle.endDate == null || entry.date <= cycle.endDate)
                }

                val bleedingDaysCount = cycleEntries.count {
                    it.bleedingIntensity != null && it.bleedingIntensity != BleedingIntensity.NONE
                }

                val peakFlow = cycleEntries.mapNotNull { it.bleedingIntensity }
                    .maxByOrNull { it.ordinal } ?: BleedingIntensity.NONE

                val painDaysCount = cycleEntries.count { (it.painLevel ?: 0) > 0 }

                cycleRows.add(
                    CycleReportRow(
                        cycleId = cycle.id,
                        startDate = cycle.startDate,
                        endDate = cycle.endDate ?: nextStart?.minusDays(1),
                        lengthDays = length,
                        bleedingDaysCount = bleedingDaysCount,
                        peakFlow = peakFlow,
                        painDaysCount = painDaysCount,
                        isExcluded = cycle.isExcludedFromEstimates,
                        exclusionReason = cycle.exclusionReason
                    )
                )

                if (isCompleted) {
                    completedLengths.add(length)
                    bleedingDaysList.add(bleedingDaysCount)
                }
            }

            // Compute Cycle Statistics
            val meanLength = if (completedLengths.isNotEmpty()) completedLengths.average() else null
            val medianLength = if (completedLengths.isNotEmpty()) {
                val sorted = completedLengths.sorted()
                if (sorted.size % 2 == 1) {
                    sorted[sorted.size / 2].toDouble()
                } else {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                }
            } else null

            val sampleStdDev = if (completedLengths.size >= 3 && meanLength != null) {
                val sumSquaredDiffs = completedLengths.sumOf { (it - meanLength).pow(2) }
                sqrt(sumSquaredDiffs / (completedLengths.size - 1))
            } else null

            val minLength = completedLengths.minOrNull()
            val maxLength = completedLengths.maxOrNull()
            val meanBleeding = if (bleedingDaysList.isNotEmpty()) bleedingDaysList.average() else null

            val shortestCycle = selectedCycles.filter { it.endDate != null }
                .minByOrNull { ChronoUnit.DAYS.between(it.startDate, it.endDate).toInt() + 1 }
            val longestCycle = selectedCycles.filter { it.endDate != null }
                .maxByOrNull { ChronoUnit.DAYS.between(it.startDate, it.endDate).toInt() + 1 }

            val cycleStats = CycleStatistics(
                totalCyclesCount = selectedCycles.size,
                completedCyclesCount = completedLengths.size,
                meanLengthDays = meanLength,
                sampleStdDevDays = sampleStdDev,
                medianLengthDays = medianLength,
                minLengthDays = minLength,
                maxLengthDays = maxLength,
                meanBleedingDays = meanBleeding,
                shortestCycleDate = shortestCycle?.startDate,
                longestCycleDate = longestCycle?.startDate,
                cycles = cycleRows
            )

            // Bleeding Distribution
            var spottingCount = 0
            var lightCount = 0
            var mediumCount = 0
            var heavyCount = 0
            var intermenstrualCount = 0

            val cycleStartDates = selectedCycles.map { it.startDate }.toSet()

            for (entry in selectedEntries) {
                val intensity = entry.bleedingIntensity ?: continue
                when (intensity) {
                    BleedingIntensity.NONE -> {}
                    BleedingIntensity.SPOTTING -> spottingCount++
                    BleedingIntensity.LIGHT -> lightCount++
                    BleedingIntensity.MEDIUM -> mediumCount++
                    BleedingIntensity.HEAVY -> heavyCount++
                }

                if (intensity != BleedingIntensity.NONE) {
                    val daysFromNearestCycleStart = cycleStartDates.minOfOrNull {
                        abs(ChronoUnit.DAYS.between(it, entry.date))
                    } ?: Long.MAX_VALUE

                    if (daysFromNearestCycleStart >= 7) {
                        intermenstrualCount++
                    }
                }
            }

            val bleedingDist = BleedingDistribution(
                spottingDays = spottingCount,
                lightDays = lightCount,
                mediumDays = mediumCount,
                heavyDays = heavyCount,
                intermenstrualBleedingDays = intermenstrualCount,
                totalBleedingDays = spottingCount + lightCount + mediumCount + heavyCount
            )

            // Pain Distribution
            var dysmenorrheaDays = 0
            var nonMenstrualPainDays = 0
            var severePainDays = 0
            val painScores = mutableListOf<Int>()

            for (entry in selectedEntries) {
                val pain = entry.painLevel ?: continue
                if (pain > 0) {
                    painScores.add(pain)
                    if (pain >= 4) severePainDays++

                    val isBleedingDay = entry.bleedingIntensity != null && entry.bleedingIntensity != BleedingIntensity.NONE
                    if (isBleedingDay) {
                        dysmenorrheaDays++
                    } else {
                        nonMenstrualPainDays++
                    }
                }
            }

            val painDist = PainDistribution(
                dysmenorrheaDays = dysmenorrheaDays,
                nonMenstrualPainDays = nonMenstrualPainDays,
                severePainDays = severePainDays,
                meanPainScore = if (painScores.isNotEmpty()) painScores.average() else null,
                totalPainDays = painScores.size
            )

            // Symptom Frequency Matrix
            val symptomMap = mutableMapOf<String, Triple<Int, Int, Int>>() // total, menstrual, non-menstrual
            for (entry in selectedEntries) {
                val isBleedingDay = entry.bleedingIntensity != null && entry.bleedingIntensity != BleedingIntensity.NONE
                for (symptomId in entry.symptomIds) {
                    val (tot, men, nonMen) = symptomMap[symptomId] ?: Triple(0, 0, 0)
                    symptomMap[symptomId] = Triple(
                        tot + 1,
                        if (isBleedingDay) men + 1 else men,
                        if (!isBleedingDay) nonMen + 1 else nonMen
                    )
                }
            }

            val symptomFrequencies = symptomMap.map { (id, counts) ->
                SymptomFrequencyEntry(
                    symptomId = id,
                    symptomNameFr = ClinicalSymptomNames.getName(id, ReportLanguage.FRENCH),
                    symptomNameEn = ClinicalSymptomNames.getName(id, ReportLanguage.ENGLISH),
                    totalOccurrences = counts.first,
                    menstrualOccurrences = counts.second,
                    nonMenstrualOccurrences = counts.third
                )
            }.sortedByDescending { it.totalOccurrences }

            // Notes Chronology
            val notesList = if (config.includeNotes) {
                selectedEntries.filter { it.notes.isNotBlank() }.map { entry ->
                    ReportNoteEntry(
                        date = entry.date,
                        bleedingIntensity = entry.bleedingIntensity,
                        painLevel = entry.painLevel,
                        notes = entry.notes
                    )
                }
            } else {
                emptyList()
            }

            ClinicalReportData(
                generatedAt = now,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
                config = config,
                cycleStats = cycleStats,
                bleedingDist = bleedingDist,
                painDist = painDist,
                symptomFrequencies = symptomFrequencies,
                notes = notesList
            )
        }
    }
}

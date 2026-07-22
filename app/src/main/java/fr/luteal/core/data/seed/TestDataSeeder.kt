package fr.luteal.core.data.seed

import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.PeriodDay
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

interface TestDataSeeder {
    suspend fun seedMockData(anchorDate: LocalDate = LocalDate.now())
    suspend fun clearAllData()
}

@Singleton
class TestDataSeederImpl @Inject constructor(
    private val cycleDao: CycleDao,
    private val dailyEntryDao: DailyEntryDao,
    private val symptomDao: SymptomDao,
    private val syncStateDao: SyncStateDao
) : TestDataSeeder {

    override suspend fun clearAllData() {
        cycleDao.deleteAllCycles()
        dailyEntryDao.deleteAllEntries()
        symptomDao.deleteAllSymptomLogs()
        syncStateDao.deleteAll()
    }

    override suspend fun seedMockData(anchorDate: LocalDate) {
        clearAllData()

        val nowEpoch = anchorDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        // Cycle 1: 58 days ago to 31 days ago (28 days)
        val cycle1Start = anchorDate.minusDays(58)
        val cycle1End = anchorDate.minusDays(31)
        val cycle1PeriodDays = listOf(
            PeriodDay(cycle1Start, BleedingIntensity.HEAVY, "Premières règles"),
            PeriodDay(cycle1Start.plusDays(1), BleedingIntensity.MEDIUM),
            PeriodDay(cycle1Start.plusDays(2), BleedingIntensity.LIGHT),
            PeriodDay(cycle1Start.plusDays(3), BleedingIntensity.SPOTTING)
        )
        val cycle1Entity = CycleEntity(
            id = UUID.randomUUID().toString(),
            startDate = cycle1Start.toString(),
            endDate = cycle1End.toString(),
            periodDaysJson = cycle1PeriodDays.toJson(),
            averageLengthDays = 28,
            lutealPhaseLengthDays = 14
        )

        // Cycle 2: 30 days ago to 6 days ago (25 days)
        val cycle2Start = anchorDate.minusDays(30)
        val cycle2End = anchorDate.minusDays(6)
        val cycle2PeriodDays = listOf(
            PeriodDay(cycle2Start, BleedingIntensity.HEAVY, "Début du cycle"),
            PeriodDay(cycle2Start.plusDays(1), BleedingIntensity.HEAVY),
            PeriodDay(cycle2Start.plusDays(2), BleedingIntensity.MEDIUM),
            PeriodDay(cycle2Start.plusDays(3), BleedingIntensity.LIGHT),
            PeriodDay(cycle2Start.plusDays(4), BleedingIntensity.SPOTTING)
        )
        val cycle2Entity = CycleEntity(
            id = UUID.randomUUID().toString(),
            startDate = cycle2Start.toString(),
            endDate = cycle2End.toString(),
            periodDaysJson = cycle2PeriodDays.toJson(),
            averageLengthDays = 28,
            lutealPhaseLengthDays = 14
        )

        // Cycle 3: 5 days ago to present (ongoing)
        val cycle3Start = anchorDate.minusDays(5)
        val cycle3PeriodDays = listOf(
            PeriodDay(cycle3Start, BleedingIntensity.HEAVY, "Flux abondant"),
            PeriodDay(cycle3Start.plusDays(1), BleedingIntensity.MEDIUM),
            PeriodDay(cycle3Start.plusDays(2), BleedingIntensity.LIGHT),
            PeriodDay(cycle3Start.plusDays(3), BleedingIntensity.SPOTTING)
        )
        val cycle3Entity = CycleEntity(
            id = UUID.randomUUID().toString(),
            startDate = cycle3Start.toString(),
            endDate = null,
            periodDaysJson = cycle3PeriodDays.toJson(),
            averageLengthDays = 28,
            lutealPhaseLengthDays = 14
        )

        cycleDao.insertCycle(cycle1Entity)
        cycleDao.insertCycle(cycle2Entity)
        cycleDao.insertCycle(cycle3Entity)

        // Daily entries
        val entries = listOf(
            DailyEntryEntity(
                date = cycle1Start.toString(),
                bleedingIntensity = BleedingIntensity.HEAVY.name,
                painLevel = 3,
                moodLevel = 2,
                energyLevel = 2,
                symptomIdsJson = JSONArray(listOf("cramps", "fatigue")).toString(),
                notes = "Crampes modérées et fatigue",
                updatedAtEpochMillis = nowEpoch
            ),
            DailyEntryEntity(
                date = cycle1Start.plusDays(13).toString(),
                bleedingIntensity = null,
                painLevel = 1,
                moodLevel = 4,
                energyLevel = 5,
                symptomIdsJson = JSONArray(listOf("ovulation_pain")).toString(),
                notes = "Pointe d'ovulation, bonne énergie",
                updatedAtEpochMillis = nowEpoch
            ),
            DailyEntryEntity(
                date = cycle2Start.toString(),
                bleedingIntensity = BleedingIntensity.HEAVY.name,
                painLevel = 4,
                moodLevel = 1,
                energyLevel = 1,
                symptomIdsJson = JSONArray(listOf("cramps", "headache")).toString(),
                notes = "Maux de tête et crampes intenses",
                updatedAtEpochMillis = nowEpoch
            ),
            DailyEntryEntity(
                date = cycle2Start.plusDays(11).toString(),
                bleedingIntensity = null,
                painLevel = 1,
                moodLevel = 5,
                energyLevel = 4,
                symptomIdsJson = JSONArray(listOf("high_libido")).toString(),
                notes = "Période ovulatoire",
                updatedAtEpochMillis = nowEpoch
            ),
            DailyEntryEntity(
                date = cycle3Start.toString(),
                bleedingIntensity = BleedingIntensity.HEAVY.name,
                painLevel = 3,
                moodLevel = 2,
                energyLevel = 2,
                symptomIdsJson = JSONArray(listOf("cramps")).toString(),
                notes = "Règles actuelles",
                updatedAtEpochMillis = nowEpoch
            ),
            DailyEntryEntity(
                date = anchorDate.toString(),
                bleedingIntensity = BleedingIntensity.SPOTTING.name,
                painLevel = 1,
                moodLevel = 4,
                energyLevel = 3,
                symptomIdsJson = JSONArray(listOf("headache")).toString(),
                notes = "Légers maux de tête en fin de règles",
                updatedAtEpochMillis = nowEpoch
            )
        )

        for (entry in entries) {
            dailyEntryDao.upsert(entry)
        }

        // Symptom logs
        val symptomLogs = listOf(
            SymptomLogEntity(
                id = UUID.randomUUID().toString(),
                date = cycle1Start.toString(),
                timestampEpochMillis = nowEpoch,
                symptomId = "cramps",
                severity = 3,
                notes = "Crampes bas-ventre"
            ),
            SymptomLogEntity(
                id = UUID.randomUUID().toString(),
                date = cycle2Start.toString(),
                timestampEpochMillis = nowEpoch,
                symptomId = "headache",
                severity = 4,
                notes = "Migraine"
            ),
            SymptomLogEntity(
                id = UUID.randomUUID().toString(),
                date = anchorDate.toString(),
                timestampEpochMillis = nowEpoch,
                symptomId = "headache",
                severity = 1,
                notes = "Léger mal de tête"
            )
        )

        for (log in symptomLogs) {
            symptomDao.insertSymptomLog(log)
        }
    }

    private fun List<PeriodDay>.toJson(): String {
        val array = JSONArray()
        for (day in this) {
            val obj = JSONObject()
            obj.put("date", day.date.toString())
            obj.put("bleedingIntensity", day.bleedingIntensity.name)
            obj.put("notes", day.notes)
            val symptomsArray = JSONArray()
            for (symptom in day.symptomIds) {
                symptomsArray.put(symptom)
            }
            obj.put("symptomIds", symptomsArray)
            array.put(obj)
        }
        return array.toString()
    }
}

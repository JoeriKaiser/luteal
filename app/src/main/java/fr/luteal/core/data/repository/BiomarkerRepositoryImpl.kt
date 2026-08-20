package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.BasalBodyTemperature
import fr.luteal.core.model.BbtDisturbance
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.CervicalFluidObservation
import fr.luteal.core.model.CervicalMucusSensation
import fr.luteal.core.model.CervicalMucusTexture
import fr.luteal.core.model.HcgTestResult
import fr.luteal.core.model.LhTestResult
import fr.luteal.core.model.RapidTestLogs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiomarkerRepositoryImpl @Inject constructor(
    private val biomarkerDao: BiomarkerDao,
    private val syncStateDao: SyncStateDao,
    private val clock: Clock
) : BiomarkerRepository {
    override fun observeObservations(): Flow<List<BiomarkerObservation>> =
        biomarkerDao.observeObservations().map { rows -> rows.map { it.toDomain() } }

    override fun observeObservation(date: LocalDate): Flow<BiomarkerObservation?> =
        biomarkerDao.observeObservation(date.toString()).map { it?.toDomain() }

    override suspend fun getObservationOnce(date: LocalDate): BiomarkerObservation? =
        biomarkerDao.getObservationOnce(date.toString())?.toDomain()

    override suspend fun save(observation: BiomarkerObservation) {
        if (observation.isEmpty) {
            delete(observation.date)
            return
        }
        biomarkerDao.upsert(observation.toEntity())
        markDirty(observation.date.toString(), deleted = false)
    }

    override suspend fun delete(date: LocalDate) {
        val existing = biomarkerDao.getObservationOnce(date.toString())
        biomarkerDao.deleteForDate(date.toString())
        if (existing != null) {
            markDirty(date.toString(), deleted = true)
        }
    }

    private fun BiomarkerObservationEntity.toDomain(): BiomarkerObservation {
        val temperature = bbtCelsius?.let { celsius ->
            BasalBodyTemperature(
                valueCelsius = celsius,
                measuredTime = bbtTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                disturbances = bbtDisturbancesJson.toDisturbances()
            )
        }
        val fluid = CervicalFluidObservation(
            sensation = cervicalSensation?.let { name ->
                CervicalMucusSensation.entries.firstOrNull { it.name == name }
            },
            texture = cervicalTexture?.let { name ->
                CervicalMucusTexture.entries.firstOrNull { it.name == name }
            }
        )
        val tests = RapidTestLogs(
            lhTest = lhTestResult?.let { name -> LhTestResult.entries.firstOrNull { it.name == name } },
            hcgTest = hcgTestResult?.let { name -> HcgTestResult.entries.firstOrNull { it.name == name } }
        )
        return BiomarkerObservation(
            date = LocalDate.parse(date),
            bbt = temperature,
            cervicalFluid = fluid.takeIf { it.hasObservation },
            rapidTests = tests.takeIf { it.hasLogs },
            notes = notes,
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
        )
    }

    private fun BiomarkerObservation.toEntity() = BiomarkerObservationEntity(
        date = date.toString(),
        bbtCelsius = bbt?.valueCelsius,
        bbtTime = bbt?.measuredTime?.toString()?.take(5),
        bbtQuality = if (bbt?.isDisturbed == true) "disturbed" else "normal",
        bbtDisturbancesJson = bbt?.disturbances.orEmpty().toJson(),
        cervicalSensation = cervicalFluid?.sensation?.name,
        cervicalTexture = cervicalFluid?.texture?.name,
        lhTestResult = rapidTests?.lhTest?.name,
        hcgTestResult = rapidTests?.hcgTest?.name,
        notes = notes.trim(),
        updatedAtEpochMillis = updatedAt.toEpochMilli()
    )

    private fun Set<BbtDisturbance>.toJson(): String {
        val array = JSONArray()
        sortedBy { it.name }.forEach { array.put(it.name) }
        return array.toString()
    }

    private fun String.toDisturbances(): Set<BbtDisturbance> = runCatching {
        val array = JSONArray(this)
        buildSet {
            for (index in 0 until array.length()) {
                val name = array.getString(index)
                BbtDisturbance.entries.firstOrNull { it.name == name }?.let(::add)
            }
        }
    }.getOrDefault(emptySet())

    private suspend fun markDirty(date: String, deleted: Boolean) {
        val now = clock.millis()
        val entityId = SyncStateEntity.biomarkerEntityId(date)
        val existing = syncStateDao.getState(entityId)
        syncStateDao.upsert(
            SyncStateEntity(
                entityId = entityId,
                entityType = SyncStateEntity.TYPE_BIOMARKER_OBSERVATION,
                clientRev = UUID.randomUUID().toString(),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = if (deleted) now else null,
                dirty = true,
                lastPushError = null
            )
        )
    }
}

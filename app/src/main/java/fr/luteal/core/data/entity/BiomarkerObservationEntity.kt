package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "biomarker_observations")
data class BiomarkerObservationEntity(
    @PrimaryKey val date: String,
    val bbtCelsius: Double?,
    val bbtTime: String?,
    val bbtQuality: String,
    val bbtDisturbancesJson: String,
    val cervicalSensation: String?,
    val cervicalTexture: String?,
    val lhTestResult: String?,
    val hcgTestResult: String?,
    val notes: String,
    val updatedAtEpochMillis: Long
)

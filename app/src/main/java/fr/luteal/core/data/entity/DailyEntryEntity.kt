package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_entries")
data class DailyEntryEntity(
    @PrimaryKey val date: String,
    val bleedingIntensity: String?,
    val painLevel: Int?,
    val moodLevel: Int?,
    val energyLevel: Int?,
    val symptomIdsJson: String,
    val notes: String,
    val updatedAtEpochMillis: Long
)

package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "symptom_logs",
    indices = [
        Index(value = ["date"])
    ]
)
data class SymptomLogEntity(
    @PrimaryKey val id: String,
    val date: String,
    val timestampEpochMillis: Long,
    val symptomId: String,
    val severity: Int,
    val notes: String,
    val isSynced: Boolean = false
)

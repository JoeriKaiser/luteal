package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String? = null,
    val periodDaysJson: String,
    val averageLengthDays: Int,
    val lutealPhaseLengthDays: Int,
    val isSynced: Boolean = false
)

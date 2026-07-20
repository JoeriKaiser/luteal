package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disorder_configs")
data class DisorderConfigEntity(
    @PrimaryKey val disorderId: String,
    val isEnabled: Boolean,
    val customNotes: String,
    val alertPhaseDaysBefore: Int
)

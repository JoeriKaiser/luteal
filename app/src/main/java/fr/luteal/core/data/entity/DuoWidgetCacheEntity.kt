package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Derived, grant-filtered Duo cycle information for offline widget display. */
@Entity(tableName = "duo_widget_cache")
data class DuoWidgetCacheEntity(
    @PrimaryKey val linkId: String,
    val role: String,
    val cycleDay: Int?,
    val estimateStart: String?,
    val estimateEnd: String?,
    val cycleDayGranted: Boolean,
    val estimateGranted: Boolean,
    val status: String,
    val refreshedAtEpochMillis: Long
)

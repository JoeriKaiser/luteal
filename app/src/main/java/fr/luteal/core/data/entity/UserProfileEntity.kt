package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val role: String,
    val syncMode: String,
    val couplePairingCode: String? = null,
    val partnerName: String? = null,
    val isPaired: Boolean
)

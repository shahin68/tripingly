package com.falcon.tripingly.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "trip_members",
    primaryKeys = ["tripId", "userId"],
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TripMemberEntity(
    val tripId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    /** `owner` or `editor`. */
    val role: String,
    /** Members are listed owner first, then in the order they joined. */
    val position: Int,
)

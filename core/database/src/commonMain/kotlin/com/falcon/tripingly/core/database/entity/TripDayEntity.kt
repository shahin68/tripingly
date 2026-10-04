package com.falcon.tripingly.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_days",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tripId"])],
)
data class TripDayEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val position: Int,
    /** `YYYY-MM-DD`, null when the trip has no dates. */
    val date: String?,
)

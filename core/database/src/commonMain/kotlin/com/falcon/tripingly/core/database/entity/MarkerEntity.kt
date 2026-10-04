package com.falcon.tripingly.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "markers",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TripDayEntity::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tripId"]), Index(value = ["dayId"])],
)
data class MarkerEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val dayId: String,
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** `HH:mm`, or null. */
    val time: String?,
    val position: Int,
    val coverThumbUrl: String?,
    val photoCount: Int,
)

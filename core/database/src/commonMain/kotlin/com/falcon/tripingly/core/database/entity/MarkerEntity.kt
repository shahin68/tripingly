package com.falcon.tripingly.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.falcon.tripingly.core.database.entity.TripEntity

@Entity(
    tableName = "markers",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tripId"])]
)
data class MarkerEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val dayIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String? = null,
    val orderNumber: Int,
    val color: Long
)

package com.falcon.tripingly.feature.map.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "markers")
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

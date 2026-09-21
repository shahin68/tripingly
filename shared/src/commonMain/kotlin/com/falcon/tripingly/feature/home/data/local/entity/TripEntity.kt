package com.falcon.tripingly.feature.home.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val imageUrl: String? = null
)

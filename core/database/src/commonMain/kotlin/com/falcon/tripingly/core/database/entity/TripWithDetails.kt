package com.falcon.tripingly.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A trip with its days, markers and members, read in one transaction. Relations are unordered. */
data class TripWithDetails(
    @Embedded val trip: TripEntity,
    @Relation(entity = TripDayEntity::class, parentColumn = "id", entityColumn = "tripId")
    val days: List<DayWithMarkers>,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val members: List<TripMemberEntity>,
)

data class DayWithMarkers(
    @Embedded val day: TripDayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId")
    val markers: List<MarkerEntity>,
)

package com.falcon.tripingly.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached trip. The server is the source of truth; the cache holds the signed-in
 * user's trips for offline reading and is wiped when the session ends.
 * Dates are `YYYY-MM-DD`, [updatedAt] epoch milliseconds.
 */
@Entity(tableName = "trips", indices = [Index(value = ["role", "updatedAt"])])
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: String?,
    val endDate: String?,
    /** `public` or `private`. */
    val visibility: String,
    /** The signed-in user's role: `owner`, `editor` or `viewer`. */
    val role: String,
    val ownerId: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val coverThumbUrl: String?,
    val dayCount: Int,
    val markerCount: Int,
    val updatedAt: Long,
    val copiedFromTripId: String? = null,
    val copiedFromOwnerId: String? = null,
    val copiedFromOwnerUsername: String? = null,
    val copiedFromOwnerDisplayName: String? = null,
)

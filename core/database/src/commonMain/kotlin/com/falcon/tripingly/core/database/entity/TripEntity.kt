package com.falcon.tripingly.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached trip. The server is the source of truth; the cache holds the signed-in
 * user's trips for offline reading and is wiped when the session ends.
 * Dates are `YYYY-MM-DD`, [createdAt] and [updatedAt] epoch milliseconds.
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
    val ownerPhotoId: String? = null,
    val ownerPhotoUrl: String? = null,
    val coverPhotoId: String?,
    val coverThumbUrl: String?,
    val dayCount: Int,
    val markerCount: Int,
    val editorCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    /** Someone else's trip I saved; it shows in My Trips with my own and shared trips. */
    val savedByMe: Boolean = false,
    /** My request to join: `pending`, `declined` or null. */
    val myJoinRequest: String? = null,
    val pendingJoinRequestCount: Int = 0,
    val destinationName: String? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
)

package com.falcon.tripingly.core.model.photo

/** What a photo belongs to: a trip's stop, or a place (anyone may add those, without a trip). */
sealed interface PhotoOwner {
    data class Stop(val markerId: String) : PhotoOwner

    data class Place(val placeId: String) : PhotoOwner
}

/** A photo on a stop or a place. Its URLs are signed and expire after about an hour; its pixels never change. */
data class Photo(
    val id: String,
    val owner: PhotoOwner,
    val status: PhotoStatus,
    /** 256 px square; null until ready. */
    val thumbUrl: String?,
    /** At most 1600 px on the long edge; null until ready. */
    val displayUrl: String?,
    val width: Int?,
    val height: Int?,
    /** Gallery order, 0-based; 0 for place photos, which are ordered by likes. */
    val position: Int,
    val likeCount: Int,
    val likedByMe: Boolean,
    val uploaderId: String?,
)

enum class PhotoStatus {
    /** Created, the file isn't in storage yet. */
    Uploading,

    /** In storage; the server is making its sizes. */
    Processing,
    Ready,
    Failed,
}

/** An image the user picked, read only when its turn to upload comes, so a big pick doesn't fill the memory. */
fun interface PickedImage {
    /** The file as stored on the phone; null when it can no longer be read. */
    suspend fun bytes(): ByteArray?
}

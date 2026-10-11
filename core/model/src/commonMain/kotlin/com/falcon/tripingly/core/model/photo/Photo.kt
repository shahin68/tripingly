package com.falcon.tripingly.core.model.photo

/** A photo on a marker. Its URLs are signed and expire after about an hour; its pixels never change. */
data class Photo(
    val id: String,
    val markerId: String,
    val status: PhotoStatus,
    /** 256 px square; null until ready. */
    val thumbUrl: String?,
    /** At most 1600 px on the long edge; null until ready. */
    val displayUrl: String?,
    val width: Int?,
    val height: Int?,
    /** Gallery order, 0-based. */
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

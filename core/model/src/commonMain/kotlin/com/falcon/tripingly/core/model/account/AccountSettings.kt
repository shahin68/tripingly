package com.falcon.tripingly.core.model.account

import kotlin.time.Instant

/** The pushes a person can turn off one by one. Off stops the push, not the entry in the app. */
enum class NotificationType { COMMENTS, ADDED_TO_TRIP, TRIP_CHANGES, LIKES, JOIN_REQUESTS }

/** Which [NotificationType]s send a push; all on until the person turns one off. */
data class NotificationSettings(val enabled: Set<NotificationType> = NotificationType.entries.toSet())

/** The latest copy of my data (GDPR export), made by the server and kept for a few days. */
data class DataExport(
    val status: DataExportStatus,
    /** Signed, only while [status] is [DataExportStatus.READY]; never log it. */
    val downloadUrl: String?,
    val expiresAt: Instant?,
)

enum class DataExportStatus { PENDING, READY, FAILED, EXPIRED }

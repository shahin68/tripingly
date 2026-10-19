package com.falcon.tripingly.core.model.account

import kotlin.time.Instant

/** The pushes a person can turn off one by one. Off stops the push, not the entry in the app. */
enum class NotificationType { COMMENTS, ADDED_TO_TRIP, TRIP_CHANGES, LIKES, JOIN_REQUESTS }

/** A fresh Google or Apple sign-in that confirms who is deleting the account. */
sealed interface IdentityProof {
    data class Google(val idToken: String) : IdentityProof

    data class Apple(val identityToken: String) : IdentityProof
}

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

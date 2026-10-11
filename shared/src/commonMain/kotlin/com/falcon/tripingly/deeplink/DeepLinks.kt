package com.falcon.tripingly.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Where a link points inside the app (`docs/knowledge/04-realtime-and-push.md`, Deep links). */
sealed interface DeepLink {
    /** The notification the link came from, marked read once it's opened. */
    val notificationId: String?

    data class Trip(val tripId: String, override val notificationId: String? = null) : DeepLink

    data class Marker(val markerId: String, override val notificationId: String? = null) : DeepLink

    /** The app itself (`tripinly://notifications` until there's an Activity screen, or an unknown link). */
    data class Home(override val notificationId: String? = null) : DeepLink

    companion object {
        /** `tripinly://trips/{id}`, `tripinly://markers/{id}`; anything else opens the app. */
        fun parse(url: String, notificationId: String? = null): DeepLink {
            val path = url.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.trimEnd('/')?.split('/')
            val id = path?.takeIf { it.size == 2 }?.get(1)?.ifBlank { null }
            return when {
                id != null && path[0] == "trips" -> Trip(id, notificationId)
                id != null && path[0] == "markers" -> Marker(id, notificationId)
                else -> Home(notificationId)
            }
        }

        private const val PREFIX = "tripinly://"
    }
}

/**
 * A link the app was opened with (a push tap), kept until the signed-in navigation takes it, so it
 * survives the launch and sign-in screens.
 */
class DeepLinks {
    private val pending = MutableStateFlow<DeepLink?>(null)

    val next: StateFlow<DeepLink?> = pending.asStateFlow()

    fun open(link: DeepLink) {
        pending.value = link
    }

    /** Taken by the navigation; null when another taker was first. */
    fun take(): DeepLink? = pending.getAndUpdate { null }
}

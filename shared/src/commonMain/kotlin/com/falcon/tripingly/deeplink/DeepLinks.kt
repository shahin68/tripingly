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

    /** One stop: its trip opens on the stop. */
    data class Marker(val markerId: String, override val notificationId: String? = null) : DeepLink

    /** A trip's Members sheet, where join requests are accepted or declined. */
    data class Members(val tripId: String, override val notificationId: String? = null) : DeepLink

    /** Someone's profile, by username. */
    data class User(val username: String, override val notificationId: String? = null) : DeepLink

    /** The app itself (`tripinly://notifications` until there's an Activity screen, or an unknown link). */
    data class Home(override val notificationId: String? = null) : DeepLink

    companion object {
        /**
         * `tripinly://trips/{id}`, `tripinly://trips/{id}/members`, `tripinly://markers/{id}`,
         * `tripinly://users/{username}`; anything else opens the app.
         */
        fun parse(url: String, notificationId: String? = null): DeepLink {
            val path = url.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.trimEnd('/')?.split('/')
            val id = path?.getOrNull(1)?.ifBlank { null }
            return when {
                id == null -> Home(notificationId)
                path.size == 2 && path[0] == "trips" -> Trip(id, notificationId)
                path.size == 3 && path[0] == "trips" && path[2] == "members" -> Members(id, notificationId)
                path.size == 2 && path[0] == "markers" -> Marker(id, notificationId)
                path.size == 2 && path[0] == "users" -> User(id, notificationId)
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

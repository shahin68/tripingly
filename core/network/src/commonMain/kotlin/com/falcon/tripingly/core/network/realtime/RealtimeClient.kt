package com.falcon.tripingly.core.network.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The live connection to the server (Socket.IO): events about the trips being watched and the user's own
 * ones. Runs while the app is signed in and in the foreground ([start]/[stop]), reconnects on its own, and
 * refreshes an expired token with the API client. Events are hints: what was sent while it was down is
 * missed, so data is reloaded on every [connections] after the first.
 */
interface RealtimeClient {
    val events: Flow<RealtimeEvent>

    /** Emits each time the connection is (re)established and the watched trips are subscribed. */
    val connections: Flow<Unit>

    /** The trips watched right now. */
    val watchedTrips: Set<String>

    fun start()

    fun stop()

    /** Counted: a trip stays subscribed until every watcher has called [unwatchTrip]. */
    fun watchTrip(tripId: String)

    fun unwatchTrip(tripId: String)
}

/** Who watches which trip, counted. */
internal class TripWatches {
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    val current: Set<String> get() = counts.value.keys

    val trips: Flow<Set<String>> = counts.map { it.keys }.distinctUntilChanged()

    fun watch(tripId: String) = counts.update { it + (tripId to (it[tripId] ?: 0) + 1) }

    fun unwatch(tripId: String) = counts.update {
        val left = (it[tripId] ?: 0) - 1
        if (left > 0) it + (tripId to left) else it - tripId
    }
}

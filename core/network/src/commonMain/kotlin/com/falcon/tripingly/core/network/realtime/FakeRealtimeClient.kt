package com.falcon.tripingly.core.network.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** No server behind it: with the fake API, and in tests, which send events and connections themselves. */
class FakeRealtimeClient : RealtimeClient {
    private val watches = TripWatches()
    private val _events = MutableSharedFlow<RealtimeEvent>()
    private val _connections = MutableSharedFlow<Unit>()

    override val events: Flow<RealtimeEvent> = _events
    override val connections: Flow<Unit> = _connections
    override val watchedTrips: Set<String> get() = watches.current

    var isRunning = false
        private set

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }

    override fun watchTrip(tripId: String) = watches.watch(tripId)

    override fun unwatchTrip(tripId: String) = watches.unwatch(tripId)

    suspend fun send(event: RealtimeEvent) = _events.emit(event)

    suspend fun connect() = _connections.emit(Unit)
}

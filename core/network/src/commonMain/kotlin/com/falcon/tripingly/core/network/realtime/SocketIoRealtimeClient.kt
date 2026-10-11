package com.falcon.tripingly.core.network.realtime

import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.SessionEndReason
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.TokenStore
import com.falcon.tripingly.core.network.refreshExpiredToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The live connection over Ktor's WebSockets, speaking the Socket.IO protocol to the `/v1/realtime`
 * namespace (the backend's realtime gateway). Its own HTTP client, without the API's headers, timeouts
 * and logging; [apiClient] only refreshes an expired token, so a request and the connection never send
 * the same refresh token twice.
 */
internal class SocketIoRealtimeClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    private val apiClient: HttpClient,
    private val tokenStore: TokenStore,
    private val sessionEvents: SessionEvents,
    private val scope: CoroutineScope,
) : RealtimeClient {

    private val http = HttpClient(engine) { install(WebSockets) }
    private val url = socketUrl(config.baseUrl)
    private val watches = TripWatches()
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val _connections = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var connection: Job? = null

    override val events: Flow<RealtimeEvent> = _events
    override val connections: Flow<Unit> = _connections
    override val watchedTrips: Set<String> get() = watches.current

    override fun start() {
        if (connection?.isActive != true) connection = scope.launch { connectWhileStarted() }
    }

    override fun stop() {
        connection?.cancel()
        connection = null
    }

    override fun watchTrip(tripId: String) = watches.watch(tripId)

    override fun unwatchTrip(tripId: String) = watches.unwatch(tripId)

    private enum class Outcome { Dropped, TokenExpired, Refused }

    /** Connects and reconnects until stopped, signed out or refused. */
    private suspend fun connectWhileStarted() {
        var failures = 0
        while (true) {
            val token = tokenStore.get()?.accessToken ?: return
            var connected = false
            val outcome = connectOnce(token) { connected = true }
            if (connected) failures = 0
            when (outcome) {
                Outcome.Refused -> return
                Outcome.TokenExpired -> {
                    val refreshed = apiClient.refreshExpiredToken(token)
                    // The refresh was refused and the session ended.
                    if (refreshed == null && tokenStore.get() == null) return
                    // Straight back with the new token, but only once in a row.
                    if (refreshed != null && failures == 0) {
                        failures++
                        continue
                    }
                }
                Outcome.Dropped -> Unit
            }
            delay(reconnectDelay(failures++))
        }
    }

    private suspend fun connectOnce(accessToken: String, onConnected: () -> Unit): Outcome {
        var outcome = Outcome.Dropped
        try {
            http.webSocket(url) { outcome = session(accessToken, onConnected) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Couldn't connect (offline, server down) or the connection broke; reconnect.
        }
        return outcome
    }

    private suspend fun DefaultClientWebSocketSession.session(accessToken: String, onConnected: () -> Unit): Outcome {
        // Until the handshake, then the server's ping interval and timeout: silence that long means a dead connection.
        var silenceLimit = HANDSHAKE_TIMEOUT
        var subscriptions: Job? = null
        try {
            while (true) {
                val frame = withTimeoutOrNull(silenceLimit) { incoming.receiveCatching().getOrNull() } ?: return Outcome.Dropped
                val text = (frame as? Frame.Text)?.readText() ?: continue
                when (val packet = SocketIoPacket.parse(text, NAMESPACE)) {
                    is SocketIoPacket.Open -> {
                        silenceLimit = (packet.pingInterval + packet.pingTimeout).milliseconds
                        send(SocketIoPacket.connect(NAMESPACE, accessToken))
                    }
                    SocketIoPacket.Ping -> send(SocketIoPacket.PONG)
                    SocketIoPacket.Connected -> {
                        onConnected()
                        subscriptions = launch(start = CoroutineStart.UNDISPATCHED) { keepSubscribed() }
                        _connections.emit(Unit)
                    }
                    is SocketIoPacket.ConnectError -> return refusal(packet.code)
                    is SocketIoPacket.Event -> receive(packet)?.let { return it }
                    SocketIoPacket.Closed -> return Outcome.Dropped
                    SocketIoPacket.Ignored -> Unit
                }
            }
        } finally {
            subscriptions?.cancel()
        }
    }

    /** Subscribes to the watched trips, and follows them while connected. */
    private suspend fun DefaultClientWebSocketSession.keepSubscribed() {
        var subscribed = emptySet<String>()
        watches.trips.collect { trips ->
            (trips - subscribed).forEach { send(SocketIoPacket.event(NAMESPACE, "trip.subscribe", tripPayload(it))) }
            (subscribed - trips).forEach { send(SocketIoPacket.event(NAMESPACE, "trip.unsubscribe", tripPayload(it))) }
            subscribed = trips
        }
    }

    /** Passes an event on; an outcome when it ends the connection. */
    private suspend fun receive(packet: SocketIoPacket.Event): Outcome? {
        when (packet.name) {
            // The server closes the connection when the token expires. Other errors are refused subscriptions
            // (a trip deleted or no longer visible), which the trip's own reload reports.
            "error" -> if (codeOf(packet.payload) == TOKEN_EXPIRED) return Outcome.TokenExpired
            "account.suspended" -> return endSession()
            else -> parseRealtimeEvent(packet.name, packet.payload)?.let { _events.emit(it) }
        }
        return null
    }

    private suspend fun refusal(code: String?): Outcome = when (code) {
        TOKEN_EXPIRED -> Outcome.TokenExpired
        ACCOUNT_SUSPENDED -> endSession()
        // The server failed, not the user: try again.
        null, INTERNAL_ERROR -> Outcome.Dropped
        // Not signed in, onboarding or consent missing: the app sorts that out; the next start tries again.
        else -> Outcome.Refused
    }

    /** Signs out, as when a refresh is refused for a suspended account. */
    private suspend fun endSession(): Outcome {
        tokenStore.clear()
        sessionEvents.end(SessionEndReason.ACCOUNT_SUSPENDED)
        return Outcome.Refused
    }

    private fun tripPayload(tripId: String): JsonObject = buildJsonObject { put("tripId", tripId) }

    private fun codeOf(payload: JsonElement): String? =
        ((payload as? JsonObject)?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val NAMESPACE = "/v1/realtime"
        const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
        const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"
        const val EVENT_BUFFER = 64
        val HANDSHAKE_TIMEOUT = 20.seconds

        // Socket.IO's own client defaults: 1 s doubling up to 5 s, each ±50 %.
        val RECONNECT_DELAY = 1.seconds
        val MAX_RECONNECT_DELAY = 5.seconds

        fun reconnectDelay(failures: Int): Duration {
            val delay = (RECONNECT_DELAY * (1 shl failures.coerceAtMost(3))).coerceAtMost(MAX_RECONNECT_DELAY)
            return delay * Random.nextDouble(0.5, 1.5)
        }
    }
}

/** The Socket.IO endpoint of the API at [baseUrl] (`https://host/v1` → `wss://host/socket.io/?EIO=4&transport=websocket`). */
internal fun socketUrl(baseUrl: String): String {
    val origin = baseUrl.trimEnd('/').removeSuffix("/v1")
    val webSocketOrigin = when {
        origin.startsWith("https://") -> "wss://" + origin.removePrefix("https://")
        origin.startsWith("http://") -> "ws://" + origin.removePrefix("http://")
        else -> origin
    }
    return "$webSocketOrigin/socket.io/?EIO=4&transport=websocket"
}

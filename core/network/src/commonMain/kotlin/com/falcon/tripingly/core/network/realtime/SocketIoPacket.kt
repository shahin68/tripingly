package com.falcon.tripingly.core.network.realtime

import com.falcon.tripingly.core.network.NetworkJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull

/**
 * The text frames of Engine.IO 4 and Socket.IO 5 over a WebSocket, for one namespace: an Engine.IO type
 * digit, and for messages (`4`) a Socket.IO type digit, `/namespace,` and a JSON payload.
 * See https://socket.io/docs/v4/socket-io-protocol/.
 */
internal sealed interface SocketIoPacket {
    /** Engine.IO handshake: the server pings every [pingInterval] ms and waits [pingTimeout] ms for the pong. */
    data class Open(val pingInterval: Long, val pingTimeout: Long) : SocketIoPacket

    data object Ping : SocketIoPacket

    /** The namespace accepted the connection. */
    data object Connected : SocketIoPacket

    /** The namespace refused the connection; [code] is the API error code. */
    data class ConnectError(val code: String?) : SocketIoPacket

    data class Event(val name: String, val payload: JsonElement) : SocketIoPacket

    /** The server closed the namespace or the whole connection. */
    data object Closed : SocketIoPacket

    /** Anything else: another namespace, acks, binary or unknown packets. */
    data object Ignored : SocketIoPacket

    companion object {
        const val PONG = "3"

        fun parse(text: String, namespace: String): SocketIoPacket = try {
            when (text.firstOrNull()) {
                '0' -> NetworkJson.parseToJsonElement(text.substring(1)).jsonObject.let {
                    Open(it.getValue("pingInterval").jsonPrimitive.long, it.getValue("pingTimeout").jsonPrimitive.long)
                }
                '1' -> Closed
                '2' -> Ping
                '4' -> parseMessage(text.substring(1), namespace)
                else -> Ignored
            }
        } catch (_: NoSuchElementException) {
            Ignored
        } catch (_: IllegalArgumentException) {
            // Not JSON, or not the expected shape (serialization errors are IllegalArgumentExceptions too).
            Ignored
        }

        /** Connects to [namespace] with the access token, which the server reads as `auth.token`. */
        fun connect(namespace: String, accessToken: String): String =
            "40$namespace," + buildJsonObject { put("token", accessToken) }

        fun event(namespace: String, name: String, payload: JsonObject): String =
            "42$namespace," + JsonArray(listOf(JsonPrimitive(name), payload))

        private fun parseMessage(message: String, namespace: String): SocketIoPacket {
            val type = message.firstOrNull() ?: return Ignored
            // The default namespace has no "/name," part; this client only uses its own.
            val prefix = "$namespace,"
            if (!message.startsWith(prefix, startIndex = 1)) return Ignored
            val body = message.substring(1 + prefix.length)
            return when (type) {
                '0' -> Connected
                '1' -> Closed
                '2' -> {
                    // An optional ack id comes before the array.
                    val array = NetworkJson.parseToJsonElement(body.dropWhile { it.isDigit() }) as? JsonArray ?: return Ignored
                    val name = (array.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return Ignored
                    Event(name, array.getOrNull(1) ?: JsonObject(emptyMap()))
                }
                '4' -> {
                    val error = body.takeIf { it.isNotEmpty() }?.let { NetworkJson.parseToJsonElement(it) as? JsonObject }
                    val code = (error?.get("data") as? JsonObject)?.get("code") as? JsonPrimitive
                    ConnectError(code?.contentOrNull)
                }
                else -> Ignored
            }
        }
    }
}

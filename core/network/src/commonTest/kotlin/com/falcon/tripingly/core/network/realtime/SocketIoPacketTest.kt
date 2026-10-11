package com.falcon.tripingly.core.network.realtime

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class SocketIoPacketTest {

    private val ns = "/v1/realtime"

    private fun parse(text: String) = SocketIoPacket.parse(text, ns)

    @Test
    fun engineIoPackets() {
        assertEquals(
            SocketIoPacket.Open(pingInterval = 25000, pingTimeout = 20000),
            parse("""0{"sid":"abc","upgrades":[],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}"""),
        )
        assertEquals(SocketIoPacket.Ping, parse("2"))
        assertEquals(SocketIoPacket.Closed, parse("1"))
        assertEquals(SocketIoPacket.Ignored, parse("6"))
        assertEquals(SocketIoPacket.Ignored, parse(""))
        assertEquals(SocketIoPacket.Ignored, parse("0not json"))
    }

    @Test
    fun namespacePackets() {
        assertEquals(SocketIoPacket.Connected, parse("""40/v1/realtime,{"sid":"xyz"}"""))
        assertEquals(SocketIoPacket.Closed, parse("41/v1/realtime,"))
        assertEquals(
            SocketIoPacket.ConnectError("TOKEN_EXPIRED"),
            parse("""44/v1/realtime,{"message":"TOKEN_EXPIRED","data":{"code":"TOKEN_EXPIRED"}}"""),
        )
        assertEquals(SocketIoPacket.ConnectError(null), parse("""44/v1/realtime,{"message":"boom"}"""))
        // The default namespace and others aren't this client's.
        assertEquals(SocketIoPacket.Ignored, parse("""40{"sid":"xyz"}"""))
        assertEquals(SocketIoPacket.Ignored, parse("""42/other,["marker.created",{}]"""))
    }

    @Test
    fun events_withAndWithoutAckId() {
        val payload = buildJsonObject { put("tripId", "t1") }
        assertEquals(SocketIoPacket.Event("trip.updated", payload), parse("""42/v1/realtime,["trip.updated",{"tripId":"t1"}]"""))
        assertEquals(SocketIoPacket.Event("trip.updated", payload), parse("""42/v1/realtime,12["trip.updated",{"tripId":"t1"}]"""))
        assertEquals(SocketIoPacket.Ignored, parse("""42/v1/realtime,[1,2]"""))
        assertEquals(SocketIoPacket.Ignored, parse("""42/v1/realtime,{"not":"an array"}"""))
    }

    @Test
    fun encodesConnectAndEvents() {
        assertEquals("""40/v1/realtime,{"token":"a\"b"}""", SocketIoPacket.connect(ns, "a\"b"))
        assertEquals(
            """42/v1/realtime,["trip.subscribe",{"tripId":"t1"}]""",
            SocketIoPacket.event(ns, "trip.subscribe", buildJsonObject { put("tripId", "t1") }),
        )
        assertEquals("3", SocketIoPacket.PONG)
        // What's sent parses back.
        val sent = SocketIoPacket.event(ns, "x", buildJsonObject { put("n", 1) })
        val parsed = parse(sent) as SocketIoPacket.Event
        assertEquals(JsonPrimitive(1), parsed.payload.jsonObject["n"])
    }

    @Test
    fun socketUrl_fromTheApiBaseUrl() {
        assertEquals(
            "wss://api-staging.example.app/socket.io/?EIO=4&transport=websocket",
            socketUrl("https://api-staging.example.app/v1"),
        )
        assertEquals("ws://10.0.2.2:3000/socket.io/?EIO=4&transport=websocket", socketUrl("http://10.0.2.2:3000/v1/"))
    }

    @Test
    fun tripWatches_areCounted() {
        val watches = TripWatches()
        watches.watch("t1")
        watches.watch("t1")
        watches.watch("t2")
        watches.unwatch("t1")
        assertEquals(setOf("t1", "t2"), watches.current)
        watches.unwatch("t1")
        watches.unwatch("t3")
        assertEquals(setOf("t2"), watches.current)
    }
}

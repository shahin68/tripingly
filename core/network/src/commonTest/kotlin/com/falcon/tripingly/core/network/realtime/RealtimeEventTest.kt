package com.falcon.tripingly.core.network.realtime

import com.falcon.tripingly.core.network.NetworkJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RealtimeEventTest {

    private fun parse(name: String, json: String) = parseRealtimeEvent(name, NetworkJson.parseToJsonElement(json))

    private fun envelope(event: String, data: String, tripId: String? = "t1", actorId: String? = "u2") =
        """{"event":"$event","tripId":${tripId?.let { "\"$it\"" }},"actorId":${actorId?.let { "\"$it\"" }},"at":"2026-10-11T10:00:00Z","data":$data}"""

    private val markerJson = """
        {"id":"m1","tripId":"t1","dayId":"d1","placeId":"p1","name":"Louvre","location":{"lat":48.86,"lng":2.33},
         "time":null,"position":2,"coverPhotoId":null,"coverThumbUrl":null,"photoCount":0,"likeCount":0,
         "likedByMe":false,"canLike":true,"commentCount":0,"createdBy":null,"createdAt":"2026-10-11T10:00:00Z","updatedAt":"2026-10-11T10:00:00Z"}
    """.trimIndent()

    private val photoJson = """
        {"id":"ph1","markerId":"m1","status":"ready","thumbUrl":"https://t","displayUrl":"https://d","width":800,
         "height":600,"position":0,"likeCount":0,"likedByMe":false,"uploader":null,"createdAt":"2026-10-11T10:00:00Z"}
    """.trimIndent()

    @Test
    fun markerEvents() {
        val saved = assertIs<RealtimeEvent.MarkerSaved>(parse("marker.created", envelope("marker.created", """{"marker":$markerJson}""")))
        assertEquals("t1", saved.tripId)
        assertEquals("u2", saved.actorId)
        assertEquals("Louvre", saved.marker.name)
        assertIs<RealtimeEvent.MarkerSaved>(parse("marker.updated", envelope("marker.updated", """{"marker":$markerJson}""")))
        assertEquals(
            RealtimeEvent.MarkerDeleted("t1", "u2", "m1"),
            parse("marker.deleted", envelope("marker.deleted", """{"markerId":"m1","dayId":"d1"}""")),
        )
        assertEquals(
            RealtimeEvent.MarkersReordered("t1", "u2", listOf("m2", "m1")),
            parse("markers.reordered", envelope("markers.reordered", """{"dayId":"d1","markerIds":["m2","m1"]}""")),
        )
        assertEquals(
            RealtimeEvent.MarkerCoverChanged("t1", "u2", "m1"),
            parse("marker.cover_changed", envelope("marker.cover_changed", """{"markerId":"m1","photoId":"ph1"}""")),
        )
    }

    @Test
    fun commentAndLikeEvents() {
        val comment = """
            {"id":"c1","markerId":"m1","body":"Lovely","author":{"id":"u2","username":"anna","displayName":"Anna"},
             "likeCount":0,"likedByMe":false,"canDelete":false,"createdAt":"2026-10-11T10:00:00Z"}
        """.trimIndent()
        val added = assertIs<RealtimeEvent.CommentAdded>(parse("comment.created", envelope("comment.created", """{"comment":$comment}""")))
        assertEquals("Lovely", added.comment.body)
        assertEquals(
            RealtimeEvent.CommentDeleted("t1", "u2", "c1", "m1"),
            parse("comment.deleted", envelope("comment.deleted", """{"commentId":"c1","markerId":"m1"}""")),
        )
        assertEquals(
            RealtimeEvent.LikeCountChanged("t1", "u2", "marker", "m1", 3),
            parse("like.count_changed", envelope("like.count_changed", """{"targetType":"marker","targetId":"m1","count":3,"liked":true}""")),
        )
    }

    @Test
    fun photoEvents() {
        val ready = assertIs<RealtimeEvent.PhotoSaved>(parse("photo.ready", envelope("photo.ready", """{"photo":$photoJson}""")))
        assertEquals("ph1", ready.photo.id)
        assertEquals(
            RealtimeEvent.PhotoFailed("t1", "u2", "ph1", "m1"),
            parse("photo.failed", envelope("photo.failed", """{"photoId":"ph1","markerId":"m1"}""")),
        )
        assertEquals(
            RealtimeEvent.PhotoDeleted("t1", "u2", "ph1", "m1"),
            parse("photo.deleted", envelope("photo.deleted", """{"photoId":"ph1","markerId":"m1","coverPhotoId":null}""")),
        )
        assertEquals(
            RealtimeEvent.PhotosReordered("t1", "u2", "m1", listOf("ph2", "ph1")),
            parse("photos.reordered", envelope("photos.reordered", """{"markerId":"m1","photoIds":["ph2","ph1"]}""")),
        )
    }

    @Test
    fun placePhotoEvents_comeToMeWithoutATrip() {
        val placePhoto = photoJson.replace("\"markerId\":\"m1\"", "\"markerId\":null,\"placeId\":\"pl1\"")
        val ready = assertIs<RealtimeEvent.PlacePhotoSaved>(
            parse("photo.ready", envelope("photo.ready", """{"photo":$placePhoto}""", tripId = null)),
        )
        assertEquals("pl1", ready.photo.placeId)
        assertEquals(
            RealtimeEvent.PlacePhotoFailed("ph1", "pl1"),
            parse("photo.failed", envelope("photo.failed", """{"photoId":"ph1","placeId":"pl1"}""", tripId = null)),
        )
    }

    @Test
    fun tripAndUserEvents() {
        listOf("trip.updated", "day.created", "day.deleted", "member.added", "member.removed").forEach {
            assertEquals(RealtimeEvent.TripChanged("t1", "u2"), parse(it, envelope(it, "{}")))
        }
        assertEquals(RealtimeEvent.TripDeleted("t1", null), parse("trip.deleted", envelope("trip.deleted", "{}", actorId = null)))
        assertEquals(
            RealtimeEvent.MyTripsChanged("t9"),
            parse("trips.changed", envelope("trips.changed", """{"tripId":"t9","change":"added"}""", tripId = null, actorId = null)),
        )
        assertEquals(
            RealtimeEvent.FollowsChanged("u1", "u2", following = true),
            parse(
                "follows.changed",
                envelope("follows.changed", """{"followerId":"u1","followeeId":"u2","following":true}""", tripId = null, actorId = "u1"),
            ),
        )
    }

    @Test
    fun unknownOrBrokenEvents_areSkipped() {
        assertNull(parse("comment.created", envelope("comment.created", "{}")))
        assertNull(parse("marker.deleted", envelope("marker.deleted", "{}")))
        assertNull(parse("marker.deleted", envelope("marker.deleted", """{"markerId":"m1"}""", tripId = null)))
        assertNull(parse("marker.created", "[]"))
    }
}

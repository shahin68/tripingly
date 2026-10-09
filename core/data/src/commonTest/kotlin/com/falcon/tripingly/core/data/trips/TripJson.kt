package com.falcon.tripingly.core.data.trips

/** Response bodies shaped like the staging API's (see the backend's openapi.json). */
internal object TripJson {
    const val OWNER = """{"id":"11111111-1111-4111-8111-111111111111","username":"shahin","displayName":"Shahin"}"""
    const val EDITOR = """{"id":"22222222-2222-4222-8222-222222222222","username":"anna","displayName":"Anna"}"""

    fun summary(id: String, title: String, updatedAt: String = "2026-10-04T10:00:00.000Z", role: String = "owner") = """
        {"id":"$id","title":"$title","startDate":"2026-06-01","endDate":"2026-06-03","visibility":"public",
         "owner":$OWNER,"role":"$role","coverThumbUrl":null,"dayCount":3,"markerCount":0,
         "likeCount":0,"likedByMe":false,"copyCount":0,"updatedAt":"$updatedAt"}
    """.trimIndent()

    fun page(nextCursor: String?, vararg items: String) =
        """{"items":[${items.joinToString(",")}],"nextCursor":${nextCursor?.let { "\"$it\"" } ?: "null"}}"""

    fun marker(id: String, tripId: String, dayId: String, position: Int, name: String = "Stop #${position + 1}") = """
        {"id":"$id","tripId":"$tripId","dayId":"$dayId","placeId":"99999999-9999-4999-8999-99999999999$position",
         "name":"$name","location":{"lat":48.8584,"lng":2.2945},"time":null,"position":$position,
         "coverPhotoId":null,"coverThumbUrl":null,"photoCount":0,"likeCount":0,"likedByMe":false,
         "commentCount":0,"createdBy":$OWNER,"createdAt":"2026-10-04T10:00:00.000Z","updatedAt":"2026-10-04T10:00:00.000Z"}
    """.trimIndent()

    fun day(id: String, position: Int, date: String?, vararg markers: String) =
        """{"id":"$id","position":$position,"date":${date?.let { "\"$it\"" } ?: "null"},"markers":[${markers.joinToString(",")}]}"""

    fun trip(
        id: String,
        title: String = "Paris",
        myRole: String = "owner",
        days: List<String> = listOf(day("d0", 0, "2026-06-01")),
        startDate: String? = "2026-06-01",
        endDate: String? = "2026-06-01",
        destination: String = "null",
    ) = """
        {"id":"$id","title":"$title","startDate":${startDate?.let { "\"$it\"" } ?: "null"},
         "endDate":${endDate?.let { "\"$it\"" } ?: "null"},"visibility":"private","owner":$OWNER,"myRole":"$myRole",
         "members":[{"user":$OWNER,"role":"owner"},{"user":$EDITOR,"role":"editor"}],
         "likeCount":0,"likedByMe":false,"copyCount":0,"copiedFrom":null,"destination":$destination,"days":[${days.joinToString(",")}],
         "createdAt":"2026-10-04T09:00:00.000Z","updatedAt":"2026-10-04T10:00:00.000Z"}
    """.trimIndent()

    fun error(code: String, message: String, details: String = "{}") =
        """{"error":{"code":"$code","message":"$message","details":$details}}"""
}

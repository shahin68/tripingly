package com.falcon.tripingly.core.network.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.MarkReadDto
import com.falcon.tripingly.core.network.model.NewActivityDto
import com.falcon.tripingly.core.network.model.RegisterDeviceDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** This device's push registration, the notifications push opens, the stops they leave marked, and the notification center. */
interface PushApi {
    @PUT("me/devices/{fcmToken}")
    @Headers("Content-Type: application/json")
    suspend fun registerDevice(
        @Path("fcmToken") fcmToken: String,
        @Body body: RegisterDeviceDto,
    ): AppResult<Unit, DataError.Network>

    @DELETE("me/devices/{fcmToken}")
    suspend fun removeDevice(@Path("fcmToken") fcmToken: String): AppResult<Unit, DataError.Network>

    @POST("notifications/read")
    @Headers("Content-Type: application/json")
    suspend fun markRead(@Body body: MarkReadDto): AppResult<Unit, DataError.Network>

    /** The notification center, newest first. */
    @GET("notifications")
    suspend fun notifications(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<NotificationCenterPageDto, DataError.Network>

    /** The notification center was opened: what's in it counts as seen. */
    @POST("notifications/seen")
    suspend fun markCenterSeen(): AppResult<Unit, DataError.Network>

    /** Takes one of my notifications out of the center (never what it is about). */
    @DELETE("notifications/{id}")
    suspend fun removeNotification(@Path("id") notificationId: String): AppResult<Unit, DataError.Network>

    /** Empties my notification center. */
    @DELETE("notifications")
    suspend fun clearNotifications(): AppResult<Unit, DataError.Network>

    /** The stops of a trip with unread notifications for me, shown as "new" marks. */
    @GET("trips/{id}/new-activity")
    suspend fun newActivity(@Path("id") tripId: String): AppResult<NewActivityDto, DataError.Network>
}

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

/** This device's push registration, the notifications push opens, and the stops they leave marked. */
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

    /** The stops of a trip with unread notifications for me, shown as "new" marks. */
    @GET("trips/{id}/new-activity")
    suspend fun newActivity(@Path("id") tripId: String): AppResult<NewActivityDto, DataError.Network>
}

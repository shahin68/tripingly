package com.falcon.tripingly.core.network.config

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.AppConfigDto
import de.jensklingenberg.ktorfit.http.GET

/** Settings the server sends to steer the app. */
interface AppConfigApi {
    /** Public; read once per launch. */
    @GET("app-config")
    suspend fun appConfig(): AppResult<AppConfigDto, DataError.Network>
}

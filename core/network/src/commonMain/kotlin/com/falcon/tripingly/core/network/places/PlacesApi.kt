package com.falcon.tripingly.core.network.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.SearchResponseDto
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/** Places from OpenStreetMap: our own places and Photon addresses and cities. */
interface PlacesApi {
    /** Our places first, then Photon results. `q` is 2–100 characters; 60 requests a minute per user. */
    @GET("places/search")
    suspend fun search(@Query("q") query: String): AppResult<SearchResponseDto, DataError.Network>
}

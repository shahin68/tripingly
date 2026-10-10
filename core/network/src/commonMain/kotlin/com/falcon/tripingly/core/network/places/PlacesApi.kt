package com.falcon.tripingly.core.network.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.PlaceDetailDto
import com.falcon.tripingly.core.network.model.SearchResponseDto
import com.falcon.tripingly.core.network.model.TilesResponseDto
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** Places from OpenStreetMap: our own places and Photon addresses and cities. */
interface PlacesApi {
    /**
     * Our places first, then Photon results. `q` is 1–100 characters (one letter: Photon only); 60 requests
     * a minute per user. With [lat] and [lng], results near that point rank first.
     */
    @GET("places/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): AppResult<SearchResponseDto, DataError.Network>

    /**
     * Places to draw for up to 16 map squares at once, each answered on its own. [tiles] is a comma-separated
     * list of `level/x/y` squares (side `360 / 2^level` degrees); [limit] (10–200) caps each square. Below
     * zoom 14 only Tripinly places (clustered when there are many) and notable OpenStreetMap places; from 14
     * OpenStreetMap places too.
     */
    @GET("places/tiles")
    suspend fun tiles(
        @Query("tiles") tiles: String,
        @Query("zoom") zoom: Int,
        @Query("limit") limit: Int,
    ): AppResult<TilesResponseDto, DataError.Network>

    /** One place, with its OpenStreetMap details. */
    @GET("places/{id}")
    suspend fun place(@Path("id") id: String): AppResult<PlaceDetailDto, DataError.Network>
}

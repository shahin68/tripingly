package com.falcon.tripingly.core.network.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.AroundResponseDto
import com.falcon.tripingly.core.network.model.NearbyPageDto
import com.falcon.tripingly.core.network.model.PlaceDetailDto
import com.falcon.tripingly.core.network.model.SearchResponseDto
import com.falcon.tripingly.core.network.model.SpotPageDto
import com.falcon.tripingly.core.network.model.TilesResponseDto
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** Places from OpenStreetMap: our own places and Photon addresses and cities. */
interface PlacesApi {
    /**
     * Our places first, then Photon results. `q` is 1–100 characters (one letter: Photon only); 60 requests
     * a minute per user. With [lat] and [lng], results near that point rank first. [scope] `destination`
     * returns only countries, regions, cities and districts (Photon), cities first.
     */
    @GET("places/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("scope") scope: String? = null,
    ): AppResult<SearchResponseDto, DataError.Network>

    /**
     * Places to draw for up to 16 map squares at once, each answered on its own. [tiles] is a comma-separated
     * list of `level/x/y` squares (side `360 / 2^level` degrees); [limit] (10–200) caps each square. Below
     * zoom 14 only Tripinly places (clustered when there are many) and notable OpenStreetMap places; from 14
     * OpenStreetMap places too. [categories] (comma-separated, null: all) keeps only places of those kinds. [name]
     * (2–100 characters) keeps only places whose name matches as in search, at any zoom and without clusters, and
     * ignores [categories].
     */
    @GET("places/tiles")
    suspend fun tiles(
        @Query("tiles") tiles: String,
        @Query("zoom") zoom: Int,
        @Query("limit") limit: Int,
        @Query("categories") categories: String? = null,
        @Query("q") name: String? = null,
    ): AppResult<TilesResponseDto, DataError.Network>

    /** One place, with its OpenStreetMap details. */
    @GET("places/{id}")
    suspend fun place(@Path("id") id: String): AppResult<PlaceDetailDto, DataError.Network>

    /**
     * Popular places within 5 km of [lat], [lng], each with its distance; the
     * first page is topped up with OpenStreetMap sights where Tripinly has few. The point isn't stored.
     */
    @GET("places/nearby")
    suspend fun nearby(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<NearbyPageDto, DataError.Network>

    /**
     * Our spots whose name matches [query] (2–100 characters), from anywhere: hot spots first, then OpenStreetMap
     * places; no Photon. With [lat], [lng] nearer spots rank higher and carry their distance. The point isn't stored.
     */
    @GET("places/spots")
    suspend fun spots(
        @Query("q") query: String,
        @Query("lat") lat: Double?,
        @Query("lng") lng: Double?,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<SpotPageDto, DataError.Network>

    /**
     * Tripinly Pro (`403 PREMIUM_REQUIRED` otherwise): every place within [radiusMeters] (50–5000) of [lat], [lng],
     * hot first, Tripinly spots before OpenStreetMap, then nearest, at most the server's `APP_AROUND_MAX_PINS` (default 150), spread over the ring; [categories] (comma-separated,
     * null: all) keeps only those kinds, while `categoryCounts` always counts every kind; [excludePlaceId], the pin the
     * ring is around, is left out of both. The point isn't stored.
     */
    @GET("places/around")
    suspend fun around(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusMeters") radiusMeters: Int,
        @Query("categories") categories: String?,
        @Query("excludePlaceId") excludePlaceId: String?,
    ): AppResult<AroundResponseDto, DataError.Network>
}

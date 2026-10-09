package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.PlaceSearchResult

/** Place search on the server (our places, then OpenStreetMap addresses and cities). */
interface PlaceRepository {
    /** From the first letter; a blank query returns nothing without asking the server. */
    suspend fun search(query: String): AppResult<List<PlaceSearchResult>, DataError.Network>
}

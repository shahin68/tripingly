package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import kotlinx.coroutines.flow.Flow

/**
 * Where the map's place pins come from: the places of map squares, loaded together and kept by
 * [PlaceRepository.placesIn]. The map asks for the squares it draws, at the zoom it draws them.
 * Places that are already stops of the trip ([hiddenPlaceIds]) show as the stop only.
 */
data class MapPlaceSource(
    private val repository: PlaceRepository,
    val hiddenPlaceIds: Set<String>,
) {
    /** Squares whose places changed when they were reloaded in the background; redraw them. */
    val changes: Flow<PlaceSquare> get() = repository.changedSquares

    /** The places of [squares], or null when they can't be loaded now. */
    suspend fun placesIn(squares: List<PlaceSquare>): List<PlacesInView>? {
        val result = repository.placesIn(squares) as? AppResult.Success ?: return null
        return result.data.values.map { square ->
            if (hiddenPlaceIds.isEmpty()) square else square.copy(places = square.places.filter { it.id !in hiddenPlaceIds })
        }
    }
}

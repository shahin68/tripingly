package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.model.PlaceClusterDto
import com.falcon.tripingly.core.network.model.PlaceDetailDto
import com.falcon.tripingly.core.network.model.PlaceItemDto
import com.falcon.tripingly.core.network.model.SearchResultDto
import com.falcon.tripingly.core.network.places.PlacesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal class DefaultPlaceRepository(
    private val api: PlacesApi,
    private val appConfig: AppConfigRepository,
    /** Requests outlive the caller that started them, so an answer nobody waits for anymore is still kept. */
    private val scope: CoroutineScope,
) : PlaceRepository {

    private val lock = Mutex()

    /** Squares loaded or being loaded, least recently used first. */
    private val squares = LinkedHashMap<PlaceSquare, LoadedSquare>()

    /** Squares to load or reload; they leave together once callers stop adding. */
    private val waiting = mutableListOf<Pair<PlaceSquare, LoadedSquare>>()
    private var sending: Job? = null

    private val _changedSquares = MutableSharedFlow<PlaceSquare>(extraBufferCapacity = MAX_SQUARES)
    override val changedSquares: Flow<PlaceSquare> = _changedSquares.asSharedFlow()

    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<PlaceSearchResult>, DataError.Network> {
        val trimmed = query.trim().take(MAX_QUERY_LENGTH)
        if (trimmed.isEmpty()) return AppResult.Success(emptyList())
        return api.search(trimmed, near?.lat, near?.lng).map { response ->
            response.items.map { PlaceSearchResult(it.name, it.address, GeoPoint(it.location.lat, it.location.lng), it.toType()) }
        }
    }

    override suspend fun placesIn(squares: Collection<PlaceSquare>): AppResult<Map<PlaceSquare, PlacesInView>, DataError.Network> {
        val refreshAfter = appConfig.config.value.placesRefresh
        val answers = lock.withLock {
            squares.distinct().associateWith { square ->
                val loaded = this.squares.remove(square)
                    ?: LoadedSquare().also { waiting += square to it }
                // Back in as the most recently used.
                this.squares[square] = loaded
                val loadedAt = loaded.loadedAt
                if (loadedAt != null && loadedAt.elapsedNow() >= refreshAfter && !loaded.isRefreshing) {
                    // Shown as it is; reloaded in the background.
                    loaded.isRefreshing = true
                    waiting += square to loaded
                }
                loaded.first
            }.also {
                while (this.squares.size > MAX_SQUARES) this.squares.remove(this.squares.keys.first())
                if (waiting.isNotEmpty() && sending == null) sending = scope.launch { send() }
            }
        }
        return answers.mapValues { (square, first) ->
            when (val result = first.await()) {
                // The latest answer: a background reload may have replaced the first one.
                is AppResult.Success -> lock.withLock { this.squares[square]?.places } ?: result.data
                is AppResult.Error -> return result
            }
        }.let { AppResult.Success(it) }
    }

    override suspend fun loadedPlacesIn(squares: Collection<PlaceSquare>): Map<PlaceSquare, PlacesInView> = lock.withLock {
        squares.mapNotNull { square ->
            this.squares[square]?.places?.let { square to it }
        }.toMap()
    }

    /** Sends the waiting squares, one request per zoom and per 16 squares, once callers stop adding for a moment. */
    private suspend fun send() {
        delay(GATHER_FOR)
        val batch = lock.withLock {
            sending = null
            waiting.toList().also { waiting.clear() }
        }
        batch.groupBy { (square, _) -> square.zoom }.forEach { (zoom, group) ->
            group.chunked(MAX_SQUARES_PER_REQUEST).forEach { chunk ->
                scope.launch {
                    val result = api.tiles(chunk.joinToString(",") { (square, _) -> square.id }, zoom, PLACES_PER_SQUARE)
                        .map { response -> response.tiles.associate { it.tile to PlacesInView(it.places.map { place -> place.toMapPlace() }, it.clusters.map { cluster -> cluster.toPlaceCluster() }) } }
                    val changed = lock.withLock {
                        chunk.mapNotNull { (square, loaded) ->
                            loaded.isRefreshing = false
                            when (result) {
                                is AppResult.Success -> {
                                    val places = result.data[square.id] ?: EMPTY
                                    val previous = loaded.places
                                    loaded.places = places
                                    loaded.loadedAt = TimeSource.Monotonic.markNow()
                                    loaded.first.complete(AppResult.Success(places))
                                    square.takeIf { previous != null && previous != places }
                                }
                                is AppResult.Error -> {
                                    // A square never loaded isn't kept, so the next caller asks again; a
                                    // loaded one keeps its places and is reloaded the next time it's asked for.
                                    if (loaded.places == null && squares[square] === loaded) squares.remove(square)
                                    loaded.first.complete(result)
                                    null
                                }
                            }
                        }
                    }
                    changed.forEach { _changedSquares.tryEmit(it) }
                }
            }
        }
    }

    override suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network> =
        api.place(id).map { it.toPlaceDetails() }

    private fun PlaceClusterDto.toPlaceCluster() = PlaceCluster(count.toInt(), GeoPoint(location.lat, location.lng))

    private fun PlaceItemDto.toMapPlace() = MapPlace(
        id = id,
        name = name,
        category = category.toCategory(),
        location = GeoPoint(location.lat, location.lng),
        isTripinly = isTripinly,
        likeCount = likeCount.toInt(),
    )

    private fun SearchResultDto.toType() = when {
        source == SearchResultDto.Source.PLACE -> SearchResultType.Place
        else -> when (type) {
            "house" -> SearchResultType.House
            "street" -> SearchResultType.Street
            "locality" -> SearchResultType.Locality
            "district" -> SearchResultType.District
            "city" -> SearchResultType.City
            "county" -> SearchResultType.County
            "state" -> SearchResultType.State
            "country" -> SearchResultType.Country
            else -> SearchResultType.Other
        }
    }

    private fun PlaceDetailDto.toPlaceDetails() = PlaceDetails(
        id = id,
        name = name,
        category = PlaceItemDto.Category.valueOf(category.name).toCategory(),
        likeCount = likeCount.toInt(),
        openingHours = tags.openingHours,
        website = tags.website,
    )

    private fun PlaceItemDto.Category.toCategory() = when (this) {
        PlaceItemDto.Category.CAFE -> PlaceCategory.Cafe
        PlaceItemDto.Category.RESTAURANT -> PlaceCategory.Restaurant
        PlaceItemDto.Category.BAR -> PlaceCategory.Bar
        PlaceItemDto.Category.ATTRACTION -> PlaceCategory.Attraction
        PlaceItemDto.Category.MUSEUM -> PlaceCategory.Museum
        PlaceItemDto.Category.HISTORIC -> PlaceCategory.Historic
        PlaceItemDto.Category.PARK -> PlaceCategory.Park
        PlaceItemDto.Category.NATURE -> PlaceCategory.Nature
        PlaceItemDto.Category.LANDMARK -> PlaceCategory.Landmark
        PlaceItemDto.Category.OTHER -> PlaceCategory.Other
    }

    private class LoadedSquare {
        /** Completes with the first answer; later answers only replace [places]. */
        val first = CompletableDeferred<AppResult<PlacesInView, DataError.Network>>()
        var places: PlacesInView? = null
        var loadedAt: TimeSource.Monotonic.ValueTimeMark? = null
        var isRefreshing = false
    }

    private companion object {
        // The server's limit for `q`.
        const val MAX_QUERY_LENGTH = 100
        // The server's limit of squares per request.
        const val MAX_SQUARES_PER_REQUEST = 16
        // A square is about four map tiles a side; 200 (the server's most) draws them about as densely as
        // a screenful of 100 did.
        const val PLACES_PER_SQUARE = 200
        // Squares kept, however old (older ones are reloaded in the background when asked for): a few
        // hundred places each, a few screens of moving around at a few zooms.
        const val MAX_SQUARES = 200
        // The map asks for the tiles of a screen within a few milliseconds of each other.
        val GATHER_FOR = 30.milliseconds
        val EMPTY = PlacesInView(emptyList(), emptyList())
    }
}

package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import kotlinx.collections.immutable.persistentListOf
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.generated.resources.map_route_minutes
import com.falcon.tripingly.feature.map.presentation.component.previewAlongTheWay
import com.falcon.tripingly.feature.map.presentation.component.previewCafe
import com.falcon.tripingly.feature.map.presentation.component.previewPlace
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

private val previewStops = persistentListOf(
    MapMarker(
        id = "1",
        position = Coordinates(48.8584, 2.2945),
        title = UiText.Server("Eiffel Tower"),
        orderNumber = 1,
        canDelete = true,
    ),
    MapMarker(
        id = "2",
        position = Coordinates(48.8606, 2.3376),
        title = UiText.Server("Louvre Museum"),
        orderNumber = 2,
        canDelete = true,
    ),
)

private val previewDays = DayTabsState(
    days = persistentListOf(1, 2, 3).let { numbers ->
        persistentListOf(*numbers.map { DayTab("d$it", it, UiText.Resource(Res.string.map_day_label_no_date, it)) }.toTypedArray())
    },
)

private val withStops = MapUiState(
    header = HeaderState(tripName = "Summer Vacation in Paris", editorMenu = EditorMenu(canDeleteDay = true)),
    dayTabs = previewDays,
    stopsCard = StopsCardState(
        stops = previewStops,
        selectedStopId = null,
        canClear = true,
        isRouteOn = false,
        route = null,
        canFindBestOrder = false,
        isFindingBestOrder = false,
    ),
    layers = MapLayersState(stops = previewStops),
    camera = CameraState(isReady = true, target = Coordinates.Paris, zoom = 13f),
)

/** The map with stops, while the trip loads, a day without stops, and a trip that couldn't load. */
internal class MapScreenPreviewStates : PreviewParameterProvider<MapUiState> {
    override val values = sequenceOf(
        withStops,
        MapUiState(header = HeaderState(isBusy = true)),
        withStops.copy(stopsCard = null, layers = MapLayersState()),
        MapUiState(message = MessageState(UiText.Resource(Res.string.map_error_load_trip), canRetry = false)),
    )
}

internal val previewRoute = RouteSummaryState(
    mode = TravelMode.Walking,
    durationSeconds = 2_700,
    distanceMeters = 3_400,
    isEstimated = false,
    isLoading = false,
    alongTheWay = previewAlongTheWay,
    addToDayNumber = 1,
)

private val previewStopsCard = withStops.stopsCard!!

/** A day's stops, with the route on and the times between them, finding the best order, and a viewer's with the owner's stops. */
internal class StopsCardPreviewStates : PreviewParameterProvider<StopsCardState> {
    override val values = sequenceOf(
        previewStopsCard,
        previewStopsCard.copy(
            isRouteOn = true,
            route = previewRoute,
            legLabels = persistentMapOf("2" to UiText.Resource(Res.string.map_route_minutes, 25)),
            selectedStopId = "2",
        ),
        previewStopsCard.copy(
            stops = (previewStops + previewStops.map { it.copy(id = "${it.id}b", orderNumber = it.orderNumber + 2) }).toPersistentList(),
            canFindBestOrder = true,
            isFindingBestOrder = true,
        ),
        previewStopsCard.copy(
            stops = previewStops.map { it.copy(canDelete = false, isByTripOwner = true) }.toPersistentList(),
            canClear = false,
            isRouteOn = null,
        ),
    )
}

/** A route from one place to another: loaded, loading, and with estimated times. */
internal class PointRouteCardPreviewStates : PreviewParameterProvider<PointRouteCardState> {
    override val values = sequenceOf(
        PointRouteCardState(previewPlace, previewCafe, previewRoute),
        PointRouteCardState(previewPlace, previewCafe, previewRoute.copy(durationSeconds = null, distanceMeters = null, isLoading = true, alongTheWay = persistentListOf())),
        PointRouteCardState(previewCafe, previewPlace, previewRoute.copy(mode = TravelMode.Cycling, isEstimated = true, addToDayNumber = null)),
    )
}

/** Several days with the second open, and a trip of one day. */
internal class DayTabsPreviewStates : PreviewParameterProvider<DayTabsState> {
    override val values = sequenceOf(
        previewDays.copy(activeIndex = 1),
        DayTabsState(persistentListOf(DayTab("d1", 1, UiText.Resource(Res.string.map_day_label_no_date, 1)))),
    )
}

private val previewSearchResults = persistentListOf(
    PlaceSearchResult(name = previewPlace.name, address = null, location = previewPlace.location, type = SearchResultType.Place, place = previewPlace),
    PlaceSearchResult(name = "Rue de Rivoli", address = "75004 Paris, France", location = GeoPoint(48.8556, 2.3601), type = SearchResultType.Street),
    PlaceSearchResult(name = "Paris", address = "Île-de-France, France", location = GeoPoint(48.8566, 2.3522), type = SearchResultType.City),
)

/** An editor's header with requests to join, a viewer's with the owner, a header while busy, and the search open with results. */
internal class MapHeaderPreviewStates : PreviewParameterProvider<MapHeaderPreview> {
    override val values = sequenceOf(
        MapHeaderPreview(withStops.header.copy(pendingJoinRequestCount = 2)),
        MapHeaderPreview(
            HeaderState(
                tripName = "A long weekend in Budapest with the whole family",
                ownerLabel = UiText.Server("by anna"),
                viewerMenu = ViewerMenu(isSavedByMe = true, myJoinRequest = null, ownerUsername = "anna"),
            ),
        ),
        MapHeaderPreview(withStops.header.copy(isBusy = true)),
        MapHeaderPreview(withStops.header, SearchState(query = "Eiff", results = previewSearchResults, isSearching = true)),
    )
}

internal data class MapHeaderPreview(val header: HeaderState, val search: SearchState? = null)

/** Every dialog of the map screen. */
internal class MapDialogPreviewStates : PreviewParameterProvider<MapDialog> {
    override val values = sequenceOf(
        MapDialog.ClearDay(dayNumber = 2, stopCount = 4),
        MapDialog.DeleteDay(dayNumber = 3, stopCount = 1),
        MapDialog.RemoveMyContent,
        MapDialog.DeleteOthersContent(UiText.Server("This stop has 3 photos and 2 comments by other people.")),
        MapDialog.Report(isSending = true),
        MapDialog.NamePin,
        MapDialog.Location(LocationPrompt.Explain),
        MapDialog.Location(LocationPrompt.OpenSettings),
        MapDialog.BestOrder(BestOrderPreview(previewStops.reversed().toPersistentList(), byTravelTime = true, savedMinutes = 18)),
        MapDialog.BestOrder(BestOrderPreview(previewStops, byTravelTime = false, savedMinutes = null)),
    )
}

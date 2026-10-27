package com.falcon.tripingly.feature.map.presentation.map.preview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import com.falcon.tripingly.feature.map.generated.resources.map_route_minutes
import com.falcon.tripingly.feature.map.presentation.component.MapLayersState
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.BestOrderPreview
import com.falcon.tripingly.feature.map.presentation.map.CameraState
import com.falcon.tripingly.feature.map.presentation.map.DayTab
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.EditorMenu
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import com.falcon.tripingly.feature.map.presentation.map.LocationPrompt
import com.falcon.tripingly.feature.map.presentation.map.MapDialog
import com.falcon.tripingly.feature.map.presentation.map.MapUiState
import com.falcon.tripingly.feature.map.presentation.map.MembersEntry
import com.falcon.tripingly.feature.map.presentation.map.MessageState
import com.falcon.tripingly.feature.map.presentation.map.PointRouteCardState
import com.falcon.tripingly.feature.map.presentation.map.RouteSummaryState
import com.falcon.tripingly.feature.map.presentation.map.SearchState
import com.falcon.tripingly.feature.map.presentation.map.StopsCardState
import com.falcon.tripingly.feature.map.presentation.map.ViewerMenu
import kotlinx.collections.immutable.persistentListOf
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
        persistentListOf(*numbers.map { DayTab("d$it", it, date = "${18 + it} Oct") }.toTypedArray())
    },
)

private val withStops = MapUiState(
    header = HeaderState(
        tripName = "Summer Vacation in Paris",
        dates = "19 – 23 Oct 2026",
        stopCount = 6,
        members = MembersEntry(count = 4, requestCount = 1),
        editorMenu = EditorMenu(canDeleteDay = true),
    ),
    dayTabs = previewDays,
    plan = StopsCardState(
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

/** The map with stops, with requests to join, while the trip loads, a day without stops, and a trip that couldn't load. */
internal class MapScreenPreviewStates : PreviewParameterProvider<MapUiState> {
    override val values = sequenceOf(
        withStops,
        MapUiState(header = HeaderState(isLoading = true)),
        withStops.copy(plan = null, layers = MapLayersState()),
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

private val previewStopsCard = withStops.plan as StopsCardState

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
            stops = previewStops.map { it.copy(canDelete = false) }.toPersistentList(),
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
        DayTabsState(persistentListOf(DayTab("d1", 1, date = null))),
    )
}

private val previewSearchResults = persistentListOf(
    PlaceSearchResult(name = previewPlace.name, address = null, location = previewPlace.location, type = SearchResultType.Place, place = previewPlace),
    PlaceSearchResult(name = "Rue de Rivoli", address = "75004 Paris, France", location = GeoPoint(48.8556, 2.3601), type = SearchResultType.Street),
    PlaceSearchResult(name = "Paris", address = "Île-de-France, France", location = GeoPoint(48.8566, 2.3522), type = SearchResultType.City),
)

/** An editor's header, a viewer's with the owner, a header while busy, and the search open with results. */
internal class MapHeaderPreviewStates : PreviewParameterProvider<MapHeaderPreview> {
    override val values = sequenceOf(
        MapHeaderPreview(withStops.header),
        MapHeaderPreview(
            HeaderState(
                tripName = "A long weekend in Budapest with the whole family",
                ownerLabel = UiText.Server("by anna"),
                viewerMenu = ViewerMenu(isSavedByMe = true, myJoinRequest = null, ownerUsername = "anna"),
            ),
        ),
        MapHeaderPreview(withStops.header.copy(isBusy = true)),
        MapHeaderPreview(withStops.header, SearchState(query = TextFieldState("Eiff"), results = previewSearchResults, isSearching = true)),
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
        MapDialog.Report(isSending = true, details = TextFieldState()),
        MapDialog.NamePin(TextFieldState()),
        MapDialog.Location(LocationPrompt.Explain),
        MapDialog.Location(LocationPrompt.OpenSettings),
        MapDialog.BestOrder(BestOrderPreview(previewStops.reversed().toPersistentList(), byTravelTime = true, savedMinutes = 18)),
        MapDialog.BestOrder(BestOrderPreview(previewStops, byTravelTime = false, savedMinutes = null)),
    )
}

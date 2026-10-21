package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.MapLine
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The trip's map, one part per thing on screen: each section of [MapScreen] takes its own part. A part
 * that is null is closed.
 */
@Immutable
data class MapUiState(
    val header: HeaderState = HeaderState(),
    /** The header shows the search field instead of the trip's name. */
    val search: SearchState? = null,
    val dayTabs: DayTabsState = DayTabsState(),
    /** The open day's stops; closed while the day has none or a route from A to B shows. */
    val stopsCard: StopsCardState? = null,
    /** A route from one place to another, in the stops card's place. */
    val pointRouteCard: PointRouteCardState? = null,
    /** "Route from here" was tapped on this place: the chip that cancels it. */
    val routeStartName: String? = null,
    /** The card over the tapped place's pin, once the camera has centered it. */
    val placeCard: PlaceCardState? = null,
    /** The card over the tapped stop's pin, once the camera has centered it. */
    val stopCard: StopCardState? = null,
    val layers: MapLayersState = MapLayersState(),
    val camera: CameraState = CameraState(),
    val location: LocationState = LocationState(),
    /** Shown over the map with a route, instead of the OpenStreetMap credit. */
    val routeAttribution: String? = null,
    val sheet: MapSheet? = null,
    val dialog: MapDialog? = null,
    val message: MessageState? = null,
)

@Immutable
data class HeaderState(
    val tripName: String = "",
    /** Shown as a date under the trip's name. */
    val tripCreatedAt: Instant? = null,
    /** The trip is loading, or a day change, a save or a request to join waits for the server. */
    val isBusy: Boolean = false,
    /** Someone else's public trip: save it, ask to join, see its owner, report it. */
    val viewerMenu: ViewerMenu? = null,
    /** An owner's or editor's: add a day, delete the open one. */
    val editorMenu: EditorMenu? = null,
    /** The owner's: people waiting for an answer to join. */
    val pendingJoinRequestCount: Int = 0,
)

@Immutable
data class ViewerMenu(
    val isSavedByMe: Boolean,
    val myJoinRequest: JoinRequestStatus?,
    val ownerUsername: String?,
)

/** [canDeleteDay]: the trip has more than one day and the owner, or an editor whose stops are all of it, may delete it. */
@Immutable
data class EditorMenu(val canDeleteDay: Boolean)

@Immutable
data class SearchState(
    val query: String = "",
    val results: ImmutableList<PlaceSearchResult> = persistentListOf(),
    /** A search is waiting for the server. */
    val isSearching: Boolean = false,
)

@Immutable
data class DayTabsState(
    val days: ImmutableList<DayTab> = persistentListOf(),
    val activeIndex: Int = 0,
)

/** A day tab: [number] counts from 1; [date] is null when the trip has no dates. */
@Immutable
data class DayTab(val id: String, val number: Int, val date: LocalDate?)

@Immutable
data class StopsCardState(
    /** The open day's stops in order. */
    val stops: ImmutableList<MapMarker>,
    val selectedStopId: String?,
    /** The owner, or an editor whose stops are all of the day's: the day can be cleared. */
    val canClear: Boolean,
    /** The Route button; null with fewer than two stops, which have no route. */
    val isRouteOn: Boolean?,
    /** The route's times and the places along it, while the Route button is on. */
    val route: RouteSummaryState?,
    /** The time to each stop from the one before, by stop ID, while the route shows. */
    val legSeconds: ImmutableMap<String, Int> = persistentMapOf(),
    /** An owner or editor, with 3 to 25 stops on the day. */
    val canFindBestOrder: Boolean,
    val isFindingBestOrder: Boolean,
)

@Immutable
data class PointRouteCardState(
    val from: MapPlace,
    val to: MapPlace,
    val route: RouteSummaryState,
)

/** A route's way of travelling, its time and length, and the places along the way. */
@Immutable
data class RouteSummaryState(
    val mode: TravelMode,
    val durationSeconds: Int?,
    val distanceMeters: Int?,
    /** Real routes aren't available right now: a note says so. */
    val isEstimated: Boolean,
    val isLoading: Boolean,
    /** Places near the route, in the order the route passes them. */
    val alongTheWay: ImmutableList<AlongTheWayPlace>,
    /** The open day they are added to; null when this user can't add stops. */
    val addToDayNumber: Int?,
)

@Immutable
data class PlaceCardState(
    val place: MapPlace,
    /** Images, opening hours and website, once loaded. */
    val details: PlaceDetails?,
    /** Street, city and country when the card is for an address from search. */
    val address: String?,
    /** The open day "Add" puts the place on; null when this user can't add stops. */
    val addToDayNumber: Int?,
    /** "Route from here" was tapped on another place, so this one offers "Route to here". */
    val canRouteTo: Boolean,
)

@Immutable
data class StopCardState(
    val stop: MapMarker,
    /** The first image of the stop's place, while the stop has no cover of its own. */
    val placeImage: PlaceImage?,
    /** An owner or editor: photos can be added and taken. */
    val canEdit: Boolean,
    /** Someone else's public trip: the stop can be reported. */
    val canReport: Boolean,
    /** Someone else's stop on a trip I edit: I can't delete it, only my photos, comments and likes on it. */
    val canRemoveMyContent: Boolean,
)

/** What the map draws. */
@Immutable
data class MapLayersState(
    /** The open day's stops, numbered. */
    val stops: ImmutableList<MapMarker> = persistentListOf(),
    /** Place pins in view, the most liked and best known first, and the places along a route. */
    val placePins: ImmutableList<PlacePin> = persistentListOf(),
    /** Below this camera zoom the place pins are drawn as dots (server value). */
    val pinDotBelowZoom: Float = 14f,
    /** Bubbles of liked places when zoomed out. */
    val placeClusters: ImmutableList<PlaceCluster> = persistentListOf(),
    /** The tapped place, drawn bigger on top. */
    val selectedPlace: MapPlace? = null,
    /** A real route, or the day's stops joined in order; null for none. */
    val line: MapLine? = null,
    val isMyLocationEnabled: Boolean = false,
)

/**
 * Where a new map view starts. Later moves come from [MapViewModel.cameraCommands]; the map reports where it
 * rests, which becomes the start again (after a rotation, for instance).
 */
@Immutable
data class CameraState(
    /**
     * The map shows once the camera knows where the trip is (its stops or destination), so it opens right
     * there; only a trip without a location opens on the default ([target] starts at Paris).
     */
    val isReady: Boolean = false,
    val target: Coordinates = Coordinates.Paris,
    val zoom: Float = 13f,
)

@Immutable
data class LocationState(
    /** The location is on its way: the button shows a spinner. */
    val isLoading: Boolean = false,
)

/** At most one sheet or full-screen view over the map. */
@Immutable
sealed interface MapSheet {
    /** [canEdit]: photos can be added and managed. */
    data class StopPhotos(val stop: MapMarker, val canEdit: Boolean) : MapSheet
    /** A place's photos, which anyone can add to. */
    data class PlacePhotos(val place: MapPlace) : MapSheet
    data class StopComments(val stop: MapMarker) : MapSheet
    /** The owner's members sheet, with the requests to join. */
    data object Members : MapSheet
    /** An image of the place card, full screen in the place's photo viewer. */
    data class PlaceImage(val placeId: String, val photoId: String) : MapSheet
    /** The in-app camera for the selected stop. */
    data object PhotoCamera : MapSheet
}

@Immutable
sealed interface MapDialog {
    /** Clear the open day: [dayNumber] and its [stopCount] stops. */
    data class ClearDay(val dayNumber: Int, val stopCount: Int) : MapDialog
    data class DeleteDay(val dayNumber: Int, val stopCount: Int) : MapDialog
    /** An editor removes their photos, comments and likes from the selected stop. */
    data object RemoveMyContent : MapDialog
    /** Deleting would delete other people's photos, comments or likes too; the server's [message] says so. */
    data class DeleteOthersContent(val message: UiText) : MapDialog
    /** Reports the trip, a stop or a photo; [isSending] while the server answers. */
    data class Report(val isSending: Boolean) : MapDialog
    /** A long press dropped a pin, waiting for its name. */
    data object NamePin : MapDialog
    /** The location button can't simply ask: explain first (Android), or send the user to Settings. */
    data class Location(val prompt: LocationPrompt) : MapDialog
    /** The best order for the open day, waiting for Apply. */
    data class BestOrder(val preview: BestOrderPreview) : MapDialog
}

enum class LocationPrompt { Explain, OpenSettings }

/** The best order, for the user to apply: the stops in their new order (numbered as they are now). */
@Immutable
data class BestOrderPreview(
    val stops: ImmutableList<MapMarker>,
    /** By real travel times; by straight-line distance when they weren't available. */
    val byTravelTime: Boolean,
    val savedMinutes: Int?,
)

/** [canRetry]: the message is about stop changes that didn't save; Retry sends them again. */
@Immutable
data class MessageState(val text: UiText, val canRetry: Boolean)

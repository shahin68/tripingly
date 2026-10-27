package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.component.MapLayersState
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * The trip's map, one part per thing on screen: each section of [MapScreen] takes its own part. A part
 * that is null is closed.
 */
@Immutable
data class MapUiState(
    /** The trip couldn't load and there's nothing to show: an error screen with Retry instead of the map. */
    val loadError: UiText? = null,
    val header: HeaderState = HeaderState(),
    /** The header shows the search field instead of the trip's name. */
    val search: SearchState? = null,
    val dayTabs: DayTabsState = DayTabsState(),
    /** The plan sheet's card: a route from A to B, else the open day's stops; closed while the day has none. */
    val plan: PlanCardState? = null,
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
    /** "19 – 23 Oct 2026" under the trip's name; null without dates. */
    val dates: String? = null,
    /** All the trip's stops, after the dates. */
    val stopCount: Int = 0,
    /** "by Anna" after them, on someone else's trip. */
    val ownerLabel: UiText? = null,
    /** The trip is loading for the first time: the title shows as its skeleton. */
    val isLoading: Boolean = false,
    /** A day change, a save or a request to join waits for the server. */
    val isBusy: Boolean = false,
    /** "Trip members · 4" at the top of the ⋮ menu, which opens the members sheet. */
    val members: MembersEntry? = null,
    /** Someone else's public trip: save it, ask to join, see its owner, report it. */
    val viewerMenu: ViewerMenu? = null,
    /**
     * An owner's or editor's day actions: add a day, clear and delete the open one. They're in the plan sheet's ⋯,
     * and in the ⋮ menu while the day has no stops (and so no sheet).
     */
    val editorMenu: EditorMenu? = null,
)

/** [requestCount]: people waiting for the owner's answer to join, as a badge; 0 for everyone else. */
@Immutable
data class MembersEntry(val count: Int, val requestCount: Int)

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
    val query: TextFieldState = TextFieldState(),
    val results: ImmutableList<PlaceSearchResult> = persistentListOf(),
    /** A search is waiting for the server. */
    val isSearching: Boolean = false,
)

@Immutable
data class DayTabsState(
    val days: ImmutableList<DayTab> = persistentListOf(),
    val activeIndex: Int = 0,
)

/** A day tab: "Day 2" ([number] counts from 1) over its [date] ("Jun 2"), null when the trip has no dates. */
@Immutable
data class DayTab(val id: String, val number: Int, val date: String?)

/** What the plan sheet shows. */
@Immutable
sealed interface PlanCardState

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
    /** The time to each stop from the one before ("12 min"), by stop ID, while the route shows. */
    val legLabels: ImmutableMap<String, UiText> = persistentMapOf(),
    /** An owner or editor, with 3 to 25 stops on the day. */
    val canFindBestOrder: Boolean,
    val isFindingBestOrder: Boolean,
) : PlanCardState

@Immutable
data class PointRouteCardState(
    val from: MapPlace,
    val to: MapPlace,
    val route: RouteSummaryState,
) : PlanCardState

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
    /** The cover's credit, when it is one of the place's Wikimedia Commons images. */
    val coverCredit: ImageCredit?,
    /** An owner or editor: photos can be added and taken. */
    val canEdit: Boolean,
    /** Someone else's public trip: the stop can be reported. */
    val canReport: Boolean,
    /** Someone else's stop on a trip I edit: I can't delete it, only my photos, comments and likes on it. */
    val canRemoveMyContent: Boolean,
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
    /** A new map opens already fitted to this area (the open day's stops) instead of on [target] and [zoom]. */
    val openingBounds: GeoBounds? = null,
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
    /** Reports the trip, a stop or a photo; [isSending] while the server answers; [details] is the text field. */
    data class Report(val isSending: Boolean, val details: TextFieldState) : MapDialog
    /** A long press dropped a pin, waiting for its [name] (the text field). */
    data class NamePin(val name: TextFieldState) : MapDialog
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

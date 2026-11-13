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
import com.falcon.tripingly.core.model.trip.UserSummary
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
    /** The owner's pill under the header while people they haven't seen here ask to join; closed while searching. */
    val joinRequests: JoinRequestsState? = null,
    /** The header shows the search field instead of the trip's name. */
    val search: SearchState? = null,
    val dayTabs: DayTabsState = DayTabsState(),
    /** The plan sheet's card: a route from A to B, else the open day's stops; closed while the day has none. */
    val plan: PlanCardState? = null,
    /** Route from was tapped, or a route between two pins or from where I am shows: the header is the from/to bar. */
    val routeBar: RouteBarState? = null,
    /** The card over the tapped place's pin, once the camera has centered it; it stays under a sheet. */
    val placeCard: PlaceCardState? = null,
    /** The card over the tapped stop's pin, once the camera has centered it; it stays under a sheet. */
    val stopCard: StopCardState? = null,
    val layers: MapLayersState = MapLayersState(),
    val camera: CameraState = CameraState(),
    val location: LocationState = LocationState(),
    /** The places button is off: only the trip's stops and its route's places show. */
    val arePlacesHidden: Boolean = false,
    /** Shown over the map with a route, instead of the OpenStreetMap credit. */
    val routeAttribution: String? = null,
    val sheet: MapSheet? = null,
    val dialog: MapDialog? = null,
    val message: MessageState? = null,
)

/** "@[people] first and [othersCount] others want to join", with [people]'s faces stacked. */
@Immutable
data class JoinRequestsState(val people: ImmutableList<UserSummary>, val othersCount: Int)

@Immutable
data class HeaderState(
    val tripName: String = "",
    /** The trip's owner, as the avatar before its name that opens their profile; null while the trip loads. */
    val owner: UserSummary? = null,
    /** What the trip card shows when the title is tapped. */
    val card: TripCardState = TripCardState(),
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

/**
 * The trip card that grows out of the title: the owner and the whole name, the details ([dates] as short as possible,
 * "Oct 19 – 23", null without dates; the likes, saves and who sees it) and a strip of the members' first [faces] (the
 * owner first, at most three) with how many there are. [hasNewJoinRequests]: the owner hasn't opened the members since
 * someone asked to join, a red dot on that strip.
 */
@Immutable
data class TripCardState(
    val dates: String? = null,
    val likeCount: Int = 0,
    val saveCount: Int = 0,
    val isPublic: Boolean = true,
    val faces: ImmutableList<UserSummary> = persistentListOf(),
    val memberCount: Int = 0,
    /** Beside the faces: "and N more" past them, or "Only Anna" for a trip of one. */
    val membersNote: UiText? = null,
    val hasNewJoinRequests: Boolean = false,
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

/**
 * A day tab: "Day 2" ([number] counts from 1) over its [date] ("Jun 2"), null when the trip has no dates. [hasNew]: one
 * of its stops has a "new" mark. [canDelete]: a long press offers to delete it (see [EditorMenu.canDeleteDay]).
 */
@Immutable
data class DayTab(val id: String, val number: Int, val date: String?, val hasNew: Boolean = false, val canDelete: Boolean = false)

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
    /** The route is on but couldn't be had: the Route button turns into an orange Retry. */
    val isRouteFailed: Boolean = false,
    /** The route's times and the places along it, while the Route button is on. */
    val route: RouteSummaryState?,
    /** The time to each stop from the one before ("12 min"), by stop ID, while the route shows. */
    val legLabels: ImmutableMap<String, UiText> = persistentMapOf(),
    /** The route card and leg times animate; off for a route left on from last time, which shows at once on opening. */
    val isRouteAnimated: Boolean = true,
    /** An owner or editor, with 3 to 25 stops on the day. */
    val canFindBestOrder: Boolean,
    val isFindingBestOrder: Boolean,
) : PlanCardState

@Immutable
data class PointRouteCardState(
    val from: MapPlace,
    val to: MapPlace,
    val route: RouteSummaryState,
    /** The stops' titles, for ends pulled from or onto a stop. */
    val fromLabel: UiText? = null,
    val toLabel: UiText? = null,
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
    /** The place along the way whose pin is picked on the map, kept in view in the list. */
    val selectedPlaceId: String?,
    /** The open day they are added to; null when this user can't add stops. */
    val addToDayNumber: Int?,
)

/**
 * A place's card and details sheet: ⓘ opens the details, + adds the place to the open day or one of my trips ([add]), and
 * from OpenStreetMap (once [details] say so) it credits "© OpenStreetMap contributors".
 */
@Immutable
data class PlaceCardState(
    val place: MapPlace,
    /** Images, opening hours, website and where it's from, once loaded. */
    val details: PlaceDetails?,
    /** Street, city and country when the card is for an address from search. */
    val address: String?,
    /** What its + does; null when this user can't add it anywhere (an editor's trip without days). */
    val add: PinAdd?,
    val routes: PinRoutesState,
    /** Who made it, for a custom spot. */
    val addedBy: AddedBy? = null,
)

/**
 * Who added a stop or made a custom spot: [user]'s face and @handle after the type on the big card, "Added by" in the
 * details (a tap opens the profile), or "you" ([isMe]; [user] may not be known yet for a stop added offline).
 */
@Immutable
data class AddedBy(val user: UserSummary?, val isMe: Boolean)

/**
 * A stop's card and details sheet: ⓘ opens the details, Delete shows for whoever may delete it ([MapMarker.canDelete]).
 * Its place ([place], once loaded) gives the credit (OpenStreetMap, a Commons cover's) and the hours and website.
 */
@Immutable
data class StopCardState(
    val stop: MapMarker,
    /** The cover's credit, when it is one of the place's Wikimedia Commons images. */
    val coverCredit: ImageCredit?,
    /** The stop's place, once loaded. */
    val place: PlaceDetails?,
    /** An owner or editor: photos can be added and managed. */
    val canEdit: Boolean,
    /** Someone else's public trip: the stop can be reported (in its details). */
    val canReport: Boolean,
    val routes: PinRoutesState = PinRoutesState(),
    /** [PinAdd.ToMyTrip] on a trip I only view; null otherwise. */
    val add: PinAdd? = null,
    /** Who put it on the trip; null on a trip only its owner edits, after a deleted account or with a block. */
    val addedBy: AddedBy? = null,
    /** Who made its custom spot, when that's someone else than [addedBy]. */
    val spotBy: AddedBy? = null,
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
    /** Opened from another sheet (the details, the trips): a back arrow takes its ✕'s place and goes back to it. */
    val hasBack: Boolean get() = false

    /** [canEdit]: photos can be added and managed. */
    data class StopPhotos(val stop: MapMarker, val canEdit: Boolean, override val hasBack: Boolean = false) : MapSheet
    /** A place's photos, which anyone can add to. */
    data class PlacePhotos(val place: MapPlace, override val hasBack: Boolean = false) : MapSheet
    /** [people]: the trip's owner and members, who can be @mentioned. */
    data class StopComments(val stop: MapMarker, val people: ImmutableList<UserSummary>, override val hasBack: Boolean = false) : MapSheet
    /** The owner's members sheet, with the requests to join. */
    data object Members : MapSheet
    /** An image of the place card, full screen in the place's photo viewer. */
    data class PlaceImage(val placeId: String, val photoId: String) : MapSheet
    /** The selected stop's details, half high. Its photos and comments open in their own sheets in its place. */
    data class StopDetails(val card: StopCardState) : MapSheet
    /** The selected place's details, half high. Its photos open in their own sheet in its place. */
    data class PlaceDetails(val card: PlaceCardState) : MapSheet
    data class AddToTrip(val picker: AddToTripState, override val hasBack: Boolean = false) : MapSheet
}

@Immutable
sealed interface MapDialog {
    /** Clear the open day: [dayNumber] and its [stopCount] stops. */
    data class ClearDay(val dayNumber: Int, val stopCount: Int) : MapDialog
    data class DeleteDay(val dayNumber: Int, val stopCount: Int) : MapDialog
    /** The owner deletes a stop someone else added: [stopName] and everything on it goes for everyone. */
    data class DeleteOthersStop(val stopName: UiText) : MapDialog
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

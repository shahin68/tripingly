package com.falcon.tripingly.feature.map.presentation.map.state

import androidx.compose.foundation.text.input.TextFieldState
import com.falcon.tripingly.core.data.push.StopNews
import com.falcon.tripingly.core.data.trips.MarkerChangeFailure
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesAround
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TransitTime
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import com.falcon.tripingly.feature.map.presentation.map.LocationPrompt
import com.falcon.tripingly.feature.map.presentation.map.TransitNote
import kotlinx.datetime.LocalTime

/**
 * What [MapViewModel] keeps besides the trip itself, grouped like [MapUiState]: each part has one owner
 * (the view model, or one of its helpers) that changes it.
 */
internal data class MapModel(
    val isLoadingTrip: Boolean = true,
    /** The trip couldn't load and there's none on the device: the screen shows the error instead of the map. */
    val loadError: UiText? = null,
    /** A day change is waiting for the server. Stop changes don't wait. */
    val isSaving: Boolean = false,
    /** Saving or joining the trip is waiting for the server. */
    val isUpdatingSharing: Boolean = false,
    val activeDayIndex: Int = 0,
    val camera: CameraModel = CameraModel(),
    val selection: Selection? = null,
    /**
     * Stops' places, by place ID, loaded when a stop is tapped: the credit of its cover (OpenStreetMap, a Commons image)
     * and its opening hours and website in the details sheet.
     */
    val stopPlaces: Map<String, PlaceDetails> = emptyMap(),
    /** Stops' photo grids and places' galleries, loaded when their card opens: its photo swipes through them. */
    val cardPhotos: Map<PhotoOwner, List<Photo>> = emptyMap(),
    val search: SearchModel? = null,
    val route: RouteModel = RouteModel(),
    val location: LocationModel = LocationModel(),
    val pins: PinsModel = PinsModel(),
    val sheet: Sheet? = null,
    val dialog: Dialog? = null,
    val message: UiText? = null,
    /** Stop changes undone because the connection failed; Retry sends them again. */
    val failedChanges: List<MarkerChangeFailure> = emptyList(),
    /**
     * News about stops I haven't seen, by where it is seen ([MapNewActivity]): their pins and days carry a "new" mark for
     * any but flames; the comments and photos buttons a dot for theirs; flames pop the open stop's flame.
     */
    val news: Set<StopNews> = emptySet(),
    /** The places button: only the trip's stops and its route's places show, nothing loads ([MapPins]). Every map opens with them shown. */
    val arePlacesHidden: Boolean = false,
    /** The trip's newest people asking to join, for its owner ([MapJoinRequests]); the pill shows while one isn't [dismissedJoinRequesterIds]. */
    val joinRequesters: List<UserSummary> = emptyList(),
    val dismissedJoinRequesterIds: Set<String> = emptySet(),
    /** The requesters the pill has shown in full, on this device (null until read): for them it stays a small circle. */
    val seenJoinRequesterIds: Set<String>? = null,
    /** The join pill shows its seen people in full again, after a long press, until it shrinks once more. */
    val isJoinRequestsShownAgain: Boolean = false,
    /** The map's filter by kind of place ([MapPlaceFilter]). */
    val placeFilter: PlaceFilterModel = PlaceFilterModel(),
    /** The places around a pin, while its ring shows ([MapAround]). */
    val around: AroundModel? = null,
)

/**
 * The map's filter: its pill is [isOpen]; only places of [categories] show (none picked: every kind). The trip's stops
 * always show (Shahin, 2026-11-17).
 */
internal data class PlaceFilterModel(val isOpen: Boolean = false, val categories: Set<PlaceCategory> = emptySet(), val isLoading: Boolean = false)

/**
 * The places around a pin (Tripinly Pro): the ring around [center] has [radiusMeters], dragged by its handle; only places
 * of [categories] list (none picked: every kind). [result] is the last answer, kept while the next one loads.
 */
internal data class AroundModel(
    val center: GeoPoint,
    /** The pin's own place, left out of what is around it and never a dot (Shahin, 2026-11-17). */
    val pinPlaceId: String?,
    val radiusMeters: Int,
    val categories: Set<PlaceCategory> = emptySet(),
    val result: PlacesAround? = null,
    val isLoading: Boolean = false,
)

/** Where the camera rests, as the map last reported it, or where it was last sent. */
internal data class CameraModel(
    val isReady: Boolean = false,
    val target: Coordinates = Coordinates.Paris,
    val zoom: Float = 13f,
    /** The area a new map opens fitted to, until the map first rests. */
    val openingBounds: GeoBounds? = null,
)

/** The tapped stop or place, or a spot being made. Its card shows once the camera has centered it ([isCardShown]). */
internal sealed interface Selection {
    val isCardShown: Boolean

    data class Stop(val markerId: String, override val isCardShown: Boolean) : Selection

    data class Place(
        val place: MapPlace,
        /** The address picked from search the card is for; [place] is drawn from it. */
        val address: PlaceSearchResult? = null,
        val details: PlaceDetails? = null,
        /** The image of [details] open full screen. */
        val imageIndex: Int? = null,
        override val isCardShown: Boolean,
    ) : Selection

    /**
     * A spot being made where the map was long-pressed: its [name] (the card's text field), its kind of place (Spot
     * until another is picked) and an optional [photo], uploaded once the stop is saved.
     */
    data class NewSpot(
        val location: Coordinates,
        val name: TextFieldState = TextFieldState(),
        val category: PlaceCategory = PlaceCategory.Other,
        val photo: PickedImage? = null,
        override val isCardShown: Boolean,
    ) : Selection
}

internal data class SearchModel(
    /** The search field; a new search starts empty. */
    val query: TextFieldState = TextFieldState(),
    val results: List<PlaceSearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

internal data class RouteModel(
    /** The Route button: the open day's route is drawn. */
    val isDayRouteShown: Boolean = false,
    /**
     * The user switched the route on or off on this screen: from then on the route card and leg times animate. A route
     * left on from last time shows at once, as the map opens and loads, without animating the lists (too costly then).
     */
    val isAnimated: Boolean = false,
    val travelMode: TravelMode = TravelMode.Walking,
    /**
     * Route from was tapped on this pin ([startLabel]: a stop's title): the header asks where to, and every other pin's
     * card offers Route to here.
     */
    val start: MapPlace? = null,
    val startLabel: UiText? = null,
    /** A route from A to B (from where I am: [MY_LOCATION_ID]), shown instead of the day's. */
    val between: PointRoute? = null,
    val plan: RoutePlan? = null,
    /** How [plan] is travelled and the stops it goes through, as asked: its line's color and ends. */
    val planMode: TravelMode = TravelMode.Walking,
    val planStops: List<GeoPoint> = emptyList(),
    /** The route couldn't be had: the day keeps its own lines, without times. */
    val isUnavailable: Boolean = false,
    val isLoading: Boolean = false,
    /** Retry was tapped this many times: each asks for the same route again. */
    val attempt: Int = 0,
    val isFindingBestOrder: Boolean = false,
    /**
     * A tapped end of [between] is being picked again (Shahin, 2026-11-13): the route, its line and both pins stay until
     * a new place is picked; ✕ or back puts the end back as it was.
     */
    val editing: RouteSide? = null,
    /**
     * Places tapped while a route shows: their pins stay on the map, as pins, until the route closes, whatever the zoom,
     * the way of travelling or the places loaded (Shahin, 2026-11-13).
     */
    val kept: List<MapPlace> = emptyList(),
    /**
     * By public transport, the time picked with "Leave at" on the open day (Shahin, 2026-11-15); null leaves at the clock's
     * time now, on the open day when it's ahead, else today.
     */
    val transitPick: TransitPick? = null,
    /** By public transport: when [plan] was asked to leave or arrive, which the "Leave at" button shows. */
    val transitAt: TransitTime? = null,
    /** By public transport, why there is no route: a note in the card, never a banner. */
    val transitNote: TransitNote? = null,
    /** The kinds of place along the way picked with the card's chips (none: every kind); kept until the route closes. */
    val alongCategories: Set<PlaceCategory> = emptySet(),
)

/** A time picked for public transport: it leaves then, or arrives by then ([isArriveBy], only from A to B). */
internal data class TransitPick(val time: LocalTime, val isArriveBy: Boolean = false)

/** The route bar's search picks an end: where to go from a start, or an end of a route picked again. */
internal val RouteModel.isPicking get() = start != null || (between != null && editing != null)

/** One end of a route between two points. */
internal enum class RouteSide { From, To }

/** A route shows, or one is being set up: the day's, one between two points, or a start waiting for where to go. */
internal val RouteModel.isActive get() = isDayRouteShown || start != null || between != null

/** A route between two places; a stop's end carries its stop's title ([fromLabel], [toLabel]) for the card. */
internal data class PointRoute(val from: MapPlace, val to: MapPlace, val fromLabel: UiText? = null, val toLabel: UiText? = null) {
    val isFromMe get() = from.id == MY_LOCATION_ID
}

/** The id of the start of a Go here route: where I was when it was asked for. */
internal const val MY_LOCATION_ID = "my-location"

/** Go here's route to the pin at [at] shows. */
internal fun RouteModel.isGoingTo(at: GeoPoint) = between?.isFromMe == true && between.to.location == at

/**
 * The pin at [at] is the start picked with Route from, or an end of a route between two pins, but not the end being
 * picked again: its Route button is on.
 */
internal fun RouteModel.isRouteEnd(at: GeoPoint) = start?.location == at ||
    (between != null && !between.isFromMe && ((editing != RouteSide.From && between.from.location == at) || (editing != RouteSide.To && between.to.location == at)))

/**
 * The pin at [at]'s Route button says Route to here: another pin is the start, or the route's "to" is picked again, or
 * it is where the route goes.
 */
internal fun RouteModel.isRouteTo(at: GeoPoint) = (start != null && start.location != at) ||
    (between != null && editing == RouteSide.To && between.from.location != at) ||
    (between != null && editing == null && !between.isFromMe && between.to.location == at)

/**
 * The pin at [at]'s Route from here replaces the start of [between] while that is picked again.
 */
internal fun RouteModel.isRouteFrom(at: GeoPoint) = between != null && editing == RouteSide.From && between.to.location != at

internal data class LocationModel(
    /** Null until the screen has read it. */
    val permission: LocationPermission? = null,
    val current: Coordinates? = null,
    val isLoading: Boolean = false,
    /** The location button was tapped: the camera moves to the first location that comes. */
    val isWaitingForFirst: Boolean = false,
    /**
     * The location button is on (Shahin, 2026-11-13): the camera follows me as I move, until a finger moves the map,
     * the screen shows something else or the button is tapped again.
     */
    val isFollowing: Boolean = false,
    /** Following me, but no location came for a while (a tunnel, underground): a quiet note says it waits for one. */
    val isSignalLost: Boolean = false,
    /** The device's location (GPS) is switched off (Shahin, 2026-11-13): while the button is on, a note offers to turn it on. */
    val isSwitchedOff: Boolean = false,
)

internal data class PinsModel(
    val pins: List<PlacePin> = emptyList(),
    val clusters: List<PlaceCluster> = emptyList(),
    /** The view the pins are for, once the camera has rested. */
    val area: PinArea? = null,
)

internal data class PinArea(val bounds: GeoBounds, val squares: List<PlaceSquare>)

/**
 * One sheet at a time (Shahin, 2026-11-12). A sheet opened from the details ([fromDetails]) takes their place, and
 * closing it brings the details back.
 */
internal sealed interface Sheet {
    val fromDetails: Boolean get() = false

    data class StopPhotos(val markerId: String, override val fromDetails: Boolean = false) : Sheet
    /** It stays open when the place's card closes. */
    data class PlacePhotos(val place: MapPlace, override val fromDetails: Boolean = false) : Sheet
    data class StopComments(val markerId: String, override val fromDetails: Boolean = false) : Sheet
    data object Members : Sheet
    /** The selected stop's or place's details. */
    data object Details : Sheet
    /** The + on a trip I only view: one of my [trips], then the picked [trip]'s [days] (null while they load). */
    /** The public transport route's walks and rides, opened from the route card; its back arrow goes back to the plan. */
    data object TransitSteps : Sheet
    data class AddToTrip(
        val item: CopyItem,
        val trips: List<Trip>,
        val trip: Trip? = null,
        val days: List<TripDay>? = null,
        override val fromDetails: Boolean = false,
    ) : Sheet
}

/** What the + on a trip I only view puts on one of my trips. */
internal sealed interface CopyItem {
    /** Copied by the server, which keeps where it came from. */
    data class Stop(val markerId: String) : CopyItem
    /** A place, or an address from search, added like a stop added on the map. */
    data class Place(val marker: NewMarker) : CopyItem
}

internal sealed interface Dialog {
    data object ClearDay : Dialog
    data object DeleteDay : Dialog
    /** The owner deletes a stop someone else added, with everything on it, once they agree. */
    data class DeleteOthersStop(val markerId: String) : Dialog
    /** A delete waits for the user to agree that other people's content goes too. */
    sealed interface OthersContent : Dialog {
        val message: UiText

        data class Change(override val message: UiText, val failure: MarkerChangeFailure) : OthersContent
        data class Day(override val message: UiText, val dayId: String) : OthersContent
    }
    /** [details] is the report dialog's text field. */
    data class Report(val target: ReportTarget, val id: String, val isSending: Boolean = false, val details: TextFieldState = TextFieldState()) : Dialog
    data class Location(val prompt: LocationPrompt) : Dialog
    data class BestOrder(val order: com.falcon.tripingly.core.model.route.BestOrder) : Dialog
    /** "Leave at": the time public transport leaves or arrives by, on the open day. */
    data object LeaveAt : Dialog
}

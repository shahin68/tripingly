package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.data.trips.MarkerChangeFailure
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.component.PlacePin

/**
 * What [MapViewModel] keeps besides the trip itself, grouped like [MapUiState]: each part has one owner
 * (the view model, or one of its helpers) that changes it.
 */
internal data class MapModel(
    val isLoadingTrip: Boolean = true,
    /** A day change is waiting for the server. Stop changes don't wait. */
    val isSaving: Boolean = false,
    /** Saving or joining the trip is waiting for the server. */
    val isUpdatingSharing: Boolean = false,
    val activeDayIndex: Int = 0,
    val camera: CameraModel = CameraModel(),
    val selection: Selection? = null,
    /** The images of stops' places, by place ID, for the credit of a stop's Commons cover. */
    val stopPlaceImages: Map<String, List<PlaceImage>> = emptyMap(),
    val search: SearchModel? = null,
    val route: RouteModel = RouteModel(),
    val location: LocationModel = LocationModel(),
    val pins: PinsModel = PinsModel(),
    val sheet: Sheet? = null,
    val dialog: Dialog? = null,
    val message: UiText? = null,
    /** Stop changes undone because the connection failed; Retry sends them again. */
    val failedChanges: List<MarkerChangeFailure> = emptyList(),
)

/** Where the camera rests, as the map last reported it, or where it was last sent. */
internal data class CameraModel(
    val isReady: Boolean = false,
    val target: Coordinates = Coordinates.Paris,
    val zoom: Float = 13f,
    /** The area a new map opens fitted to, until the map first rests. */
    val openingBounds: GeoBounds? = null,
)

/** The tapped stop or place. Its card shows once the camera has centered it ([isCardShown]). */
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
}

internal data class SearchModel(
    val query: String = "",
    val results: List<PlaceSearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

internal data class RouteModel(
    /** The Route button: the open day's route is drawn. */
    val isDayRouteShown: Boolean = false,
    val travelMode: TravelMode = TravelMode.Walking,
    /** "Route from here" was tapped on this place; the next place's card offers "Route to here". */
    val start: MapPlace? = null,
    /** A route from A to B, shown instead of the day's. */
    val between: PointRoute? = null,
    val plan: RoutePlan? = null,
    /** How [plan] is travelled and the stops it goes through, as asked: its line's color and ends. */
    val planMode: TravelMode = TravelMode.Walking,
    val planStops: List<GeoPoint> = emptyList(),
    /** The route couldn't be had: the day keeps its own lines, without times. */
    val isUnavailable: Boolean = false,
    val isLoading: Boolean = false,
    val isFindingBestOrder: Boolean = false,
)

/** A route from one place to another. */
internal data class PointRoute(val from: MapPlace, val to: MapPlace)

internal data class LocationModel(
    /** Null until the screen has read it. */
    val permission: LocationPermission? = null,
    val current: Coordinates? = null,
    val isLoading: Boolean = false,
    /** The location button was tapped: the camera moves to the first location that comes. */
    val isWaitingForFirst: Boolean = false,
)

internal data class PinsModel(
    val pins: List<PlacePin> = emptyList(),
    val clusters: List<PlaceCluster> = emptyList(),
    /** The view the pins are for, once the camera has rested. */
    val area: PinArea? = null,
)

internal data class PinArea(val bounds: GeoBounds, val squares: List<PlaceSquare>)

internal sealed interface Sheet {
    data class StopPhotos(val markerId: String) : Sheet
    /** It stays open when the place's card closes. */
    data class PlacePhotos(val place: MapPlace) : Sheet
    data class StopComments(val markerId: String) : Sheet
    data object Members : Sheet
    /** The in-app camera, for the selected stop. */
    data object PhotoCamera : Sheet
}

internal sealed interface Dialog {
    data object ClearDay : Dialog
    data object DeleteDay : Dialog
    /** About the selected stop. */
    data object RemoveMyContent : Dialog
    /** A delete waits for the user to agree that other people's content goes too. */
    sealed interface OthersContent : Dialog {
        val message: UiText

        data class Change(override val message: UiText, val failure: MarkerChangeFailure) : OthersContent
        data class Day(override val message: UiText, val dayId: String) : OthersContent
    }
    data class Report(val target: ReportTarget, val id: String, val isSending: Boolean = false) : Dialog
    /** Where a long press dropped a pin that waits for its name. */
    data class NamePin(val location: Coordinates) : Dialog
    data class Location(val prompt: LocationPrompt) : Dialog
    data class BestOrder(val order: com.falcon.tripingly.core.model.route.BestOrder) : Dialog
}

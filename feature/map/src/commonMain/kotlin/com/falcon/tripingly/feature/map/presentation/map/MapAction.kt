package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.feature.map.presentation.component.MapMarker

/** What the user did on the trip's map, grouped by the UI element it happened on (as [MapUiState] is). */
sealed interface MapAction {
    /** On the map itself. */
    sealed interface MapView : MapAction {
        /** A tap on the map, not on a pin. */
        data class Click(val coordinates: Coordinates) : MapView
        data class LongClick(val coordinates: Coordinates) : MapView
        data class PlacePinClick(val place: MapPlace) : MapView
        /** A tapped cluster opens up into its places. */
        data class ClusterClick(val cluster: PlaceCluster) : MapView
        /** A stop's pin, or its chip on the plan. */
        data class MarkerClick(val marker: MapMarker) : MapView
        /** The camera came to rest. */
        data class CameraRest(val coordinates: Coordinates, val zoom: Float, val bounds: GeoBounds? = null) : MapView
        /** A touch began anywhere outside the place card, on the map or on the screen's other controls. */
        data object ScreenTouch : MapView
    }

    /** The top bar and its menu. */
    sealed interface Header : MapAction {
        data object Back : Header
        /** Loads the trip again from the error screen. */
        data object Retry : Header
        data object AddDay : Header
        data object DeleteDay : Header
        /** Saves someone else's trip to My Trips, or removes it. */
        data object ToggleSave : Header
        /** Asks the owner to let me edit their trip with them. */
        data object RequestToJoin : Header
        data object CancelJoinRequest : Header
        /** "Trip members" in the ⋮ menu opens the members sheet (where the owner answers requests to join). */
        data object OpenMembers : Header
        data object OpenOwnerProfile : Header
        data object ReportTrip : Header
    }

    sealed interface Search : MapAction {
        data object Open : Search
        data class QueryChanged(val query: String) : Search
        data class ResultClick(val result: PlaceSearchResult) : Search
        data object Close : Search
    }

    /** The location button and the permission it needs. */
    sealed interface Location : MapAction {
        data class PermissionChecked(val permission: LocationPermission) : Location
        data class PermissionResult(val permission: LocationPermission) : Location
        data object ConfirmPrompt : Location
        data object DismissPrompt : Location
        data object CenterOnUser : Location
    }

    sealed interface Days : MapAction {
        /** Opens the tapped day; the camera stays where it is. */
        data class Select(val dayIndex: Int) : Days
        /** The button above the location button: fits the camera to the open day. */
        data object FitDay : Days
    }

    /** The open day's plan in its sheet: the stops card, or a route from A to B. */
    sealed interface Plan : MapAction {
        data class RemoveStop(val markerId: String) : Plan
        data object ClearAll : Plan
        /** The Route button: shows or hides the open day's route. */
        data object ToggleRoute : Plan
        data class SelectTravelMode(val mode: TravelMode) : Plan
        data object FindBestOrder : Plan
        data class AlongTheWayClick(val place: MapPlace) : Plan
        /** Adds a place along the way to the open day. */
        data class AddAlongTheWay(val place: MapPlace) : Plan
        data object ClosePointRoute : Plan
        data object CancelRouteStart : Plan
    }

    /** The card over the selected stop. */
    sealed interface StopCard : MapAction {
        data class ToggleHot(val markerId: String) : StopCard
        /** The stop's photos open in a sheet. */
        data object OpenPhotos : StopCard
        /** The stop's comments open in a sheet. */
        data object OpenComments : StopCard
        /** Photos picked or taken for the stop. */
        data class AddPhotos(val images: List<PickedImage>) : StopCard
        /** The in-app camera opens for the stop; its photo comes back as [AddPhotos]. */
        data object OpenCamera : StopCard
        /** An editor removes their photos, comments and likes from a stop they can't delete. */
        data object RemoveMyContent : StopCard
        data object Report : StopCard
    }

    /** The card over the selected place. */
    sealed interface PlaceCard : MapAction {
        data object AddToDay : PlaceCard
        /** The place's photos, which anyone can add to (not for an address from search). */
        data object OpenPhotos : PlaceCard
        /** A tapped image opens full screen. */
        data class OpenImage(val index: Int) : PlaceCard
        /** A route from here to the next place picked. */
        data object RouteFrom : PlaceCard
        /** The route from the place picked first to this one. */
        data object RouteTo : PlaceCard
    }

    /** The sheet or full-screen viewer open over the map. */
    sealed interface Sheet : MapAction {
        data object CloseStopPhotos : Sheet
        data object ClosePlacePhotos : Sheet
        data object CloseStopComments : Sheet
        data object CloseMembers : Sheet
        data object ClosePlaceImage : Sheet
        data object CloseCamera : Sheet
        /** Reports the photo open in a viewer: a Commons image as the place's image, anything else as a photo. */
        data class ReportPhoto(val photo: Photo) : Sheet
    }

    sealed interface Dialog : MapAction {
        data class ConfirmPinName(val name: String) : Dialog
        data object DismissPinName : Dialog
        data object Confirm : Dialog
        data object DismissConfirm : Dialog
        /** Deletes the stop or day although other people's content goes with it. */
        data object DeleteAnyway : Dialog
        data object DismissDeleteAnyway : Dialog
        data class SendReport(val reason: ReportReason, val details: String) : Dialog
        data object DismissReport : Dialog
        data object ApplyBestOrder : Dialog
        data object DismissBestOrder : Dialog
    }

    /** The banner at the bottom. */
    sealed interface Message : MapAction {
        /** The banner has the message; failed changes stay for the next Retry. */
        data object Shown : Message
        /** Sends every stop change that failed for a connection problem again. */
        data object RetryFailedChanges : Message
    }
}

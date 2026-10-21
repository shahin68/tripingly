package com.falcon.tripingly.feature.map.presentation.screen

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
import com.falcon.tripingly.feature.map.domain.model.MapMarker

/** What the user did on the trip's map. */
sealed interface MapAction {
    data class OnPermissionChecked(val permission: LocationPermission) : MapAction
    data class OnPermissionResult(val permission: LocationPermission) : MapAction
    data object OnConfirmLocationPrompt : MapAction
    data object OnDismissLocationPrompt : MapAction
    data object CenterOnUserLocation : MapAction
    /** A tap on the map, not on a pin. */
    data class OnMapClick(val coordinates: Coordinates) : MapAction
    data class OnPlacePinClick(val place: MapPlace) : MapAction
    /** A tapped cluster opens up into its places. */
    data class OnClusterClick(val cluster: PlaceCluster) : MapAction
    data class OnMapLongClick(val coordinates: Coordinates) : MapAction
    data class OnConfirmPinName(val name: String) : MapAction
    data object OnDismissPinName : MapAction
    data class OnMarkerClick(val marker: MapMarker) : MapAction
    data class OnRemoveMarker(val markerId: String) : MapAction
    data object ClearAllMarkers : MapAction
    data object AddDay : MapAction
    data object DeleteDay : MapAction
    data object OnConfirm : MapAction
    data object OnDismissConfirm : MapAction
    /** An editor removes their photos, comments and likes from the selected stop they can't delete. */
    data object OnRemoveMyContent : MapAction
    /** Deletes the stop or day although other people's content goes with it. */
    data object OnDeleteAnyway : MapAction
    data object OnDismissDeleteAnyway : MapAction
    /** The banner has the message; failed changes stay for the next Retry. */
    data object OnMessageShown : MapAction
    data object RetryFailedChanges : MapAction
    data class OnCameraMove(val coordinates: Coordinates, val zoom: Float, val bounds: GeoBounds? = null) : MapAction
    /** A touch began anywhere outside the place card, on the map or on the screen's other controls. */
    data object OnScreenTouch : MapAction
    data object OnAddPlaceToDay : MapAction
    /** The selected stop's photos open in a sheet. */
    data object OnOpenStopPhotos : MapAction
    data object OnCloseStopPhotos : MapAction
    /** The selected place's photos, which anyone can add to (not for an address from search). */
    data object OnOpenPlacePhotos : MapAction
    data object OnClosePlacePhotos : MapAction
    /** Photos picked or taken from the stop card for the selected stop. */
    data class OnAddStopPhotos(val images: List<PickedImage>) : MapAction
    /** The in-app camera opens for the selected stop; its photo comes back as [OnAddStopPhotos]. */
    data object OnOpenPhotoCamera : MapAction
    data object OnClosePhotoCamera : MapAction
    data object OnOpenSearch : MapAction
    data class OnSearchQueryChanged(val query: String) : MapAction
    data class OnSearchResultClick(val result: PlaceSearchResult) : MapAction
    data object OnCloseSearch : MapAction
    data class OnDaySelected(val dayIndex: Int) : MapAction
    data object OnBackClick : MapAction
    data class OnToggleStopLike(val markerId: String) : MapAction
    /** The selected stop's comments open in a sheet. */
    data object OnOpenStopComments : MapAction
    data object OnCloseStopComments : MapAction
    /** Saves someone else's trip to My Trips, or removes it. */
    data object OnToggleSaveTrip : MapAction

    /** Asks the owner to let me edit their trip with them. */
    data object OnRequestToJoin : MapAction
    data object OnCancelJoinRequest : MapAction

    /** The owner opens the members sheet to answer requests to join. */
    data object OnOpenJoinRequests : MapAction
    data object OnCloseJoinRequests : MapAction
    data object OnOpenOwnerProfile : MapAction
    data object OnReportTrip : MapAction
    /** Reports the selected stop. */
    data object OnReportStop : MapAction
    data class OnSendReport(val reason: ReportReason, val details: String) : MapAction
    data object OnDismissReport : MapAction
    /** A tapped image on the place card opens full screen. */
    data class OnOpenPlaceImage(val index: Int) : MapAction
    data object OnClosePlaceImage : MapAction
    /** Reports the photo open in a viewer: a Commons image as the place's image, anything else as a photo. */
    data class OnReportPhoto(val photo: Photo) : MapAction
    /** The Route button: shows or hides the open day's route. */
    data object OnToggleDayRoute : MapAction
    data class OnTravelModeSelected(val mode: TravelMode) : MapAction
    /** The open place's card: a route from here to the next place picked. */
    data object OnRouteFromPlace : MapAction
    /** The open place's card: the route from the place picked first to this one. */
    data object OnRouteToPlace : MapAction
    data object OnCancelRouteStart : MapAction
    data object OnClosePointRoute : MapAction
    data class OnAlongTheWayClick(val place: MapPlace) : MapAction
    /** Adds a place along the way to the open day. */
    data class OnAddAlongTheWay(val place: MapPlace) : MapAction
    data object OnFindBestOrder : MapAction
    data object OnApplyBestOrder : MapAction
    data object OnDismissBestOrder : MapAction
}

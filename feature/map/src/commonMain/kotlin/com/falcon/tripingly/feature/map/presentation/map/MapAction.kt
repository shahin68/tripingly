package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.PinEnd

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
        /** The camera moves: what the whole view shows now, every frame. */
        data class CameraMoving(val bounds: GeoBounds, val zoom: Float) : MapView
        /** The camera came to rest. */
        data class CameraRest(val coordinates: Coordinates, val zoom: Float, val bounds: GeoBounds? = null) : MapView
        /** A pin was pulled onto another and let go there: the route from one to the other. */
        data class PinDrop(val from: PinEnd, val to: PinEnd) : MapView
        /** A touch began anywhere outside the place card, on the map or on the screen's other controls. */
        data object ScreenTouch : MapView
        /** A finger started moving the camera: it stops following me. */
        data object UserMove : MapView
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
        /** The "wants to join" pill or the trip card's members strip: the pill and the strip's red dot go, the members sheet opens. */
        data object OpenJoinRequests : Header
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
        /** The device's location (GPS) is on or off, read on open, on return and whenever it's switched. */
        data class LocationSwitched(val isOn: Boolean) : Location
        /** "Turn on" on the location-off note. */
        data object TurnOn : Location
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
        /** The top map button: hides or shows the places that aren't the trip's stops or its route's. */
        data object TogglePlaces : Days
    }

    /** The open day's plan in its sheet: the stops card, or a route from A to B. */
    sealed interface Plan : MapAction {
        data class RemoveStop(val markerId: String) : Plan
        data object ClearAll : Plan
        /** The Route button: shows or hides the open day's route. */
        data object ToggleRoute : Plan
        /** The Route button's Retry, after the route failed. */
        data object RetryRoute : Plan
        data class SelectTravelMode(val mode: TravelMode) : Plan
        data object FindBestOrder : Plan
        data class AlongTheWayClick(val place: MapPlace) : Plan
        /** Adds a place along the way to the open day. */
        data class AddAlongTheWay(val place: MapPlace) : Plan
        data object ClosePointRoute : Plan
    }

    /** The route buttons of a stop's or a place's card and details, and the header's from/to bar. */
    sealed interface PinRoute : MapAction {
        /** From where I am to the pin. */
        data object GoHere : PinRoute
        /** Route from, or Route to here while another pin is the start; on, it closes the route. */
        data object Route : PinRoute
        /** The from/to bar's ✕ or back. */
        data object Close : PinRoute
        /** The from/to bar's ⇅: the route the other way round. */
        data object Swap : PinRoute
        /** A tap on an end's pill in the from/to bar: that end is picked again ([isFrom]: where it starts). */
        data class EditEnd(val isFrom: Boolean) : PinRoute
        /** The keyboard's search in an end's field: its first result is picked. */
        data object SubmitEnd : PinRoute
    }

    /** The card over the selected stop, and its details sheet. */
    sealed interface StopCard : MapAction {
        data class ToggleHot(val markerId: String) : StopCard
        /** The stop's flame popped for flames I hadn't seen. */
        data class HotSeen(val markerId: String) : StopCard
        /** The stop's photos open in a sheet, where photos are added too. */
        data object OpenPhotos : StopCard
        /** The stop's comments open in a sheet. */
        data object OpenComments : StopCard
        /** ⓘ: the stop's details open in a sheet, half high. */
        data object OpenDetails : StopCard
        /** From the details sheet, on someone else's public trip. */
        data object Report : StopCard
        /** The + on a trip I only view: to one of my trips. */
        data object Add : StopCard
    }

    /** The card over the selected place. */
    sealed interface PlaceCard : MapAction {
        /** The +: to the open day, or on a trip I only view to one of my trips. */
        data object Add : PlaceCard
        /** The place's photos, which anyone can add to (not for an address from search). */
        data object OpenPhotos : PlaceCard
        /** A tapped image opens full screen. */
        data class OpenImage(val index: Int) : PlaceCard
        /** ⓘ: the place's details open in a sheet, half high. */
        data object OpenDetails : PlaceCard
    }

    /** The sheet or full-screen viewer open over the map. */
    sealed interface Sheet : MapAction {
        /** ✕, a swipe or a tap outside: every sheet closes, also those it was opened from (Shahin, 2026-11-13). */
        data object CloseAll : Sheet
        /** A sheet's back arrow or the system back: back to the sheet it was opened from. */
        data object Back : Sheet
        data object ClosePlaceImage : Sheet
        /** The add-to-trip sheet's rows: one of my trips, then its day. */
        data class PickTrip(val tripId: String) : Sheet
        data class PickDay(val dayId: String) : Sheet
        /** Reports the photo open in a viewer: a Commons image as the place's image, anything else as a photo. */
        data class ReportPhoto(val photo: Photo) : Sheet
        /** "Added by" in a pin's details: that person's profile. */
        data class OpenProfile(val username: String) : Sheet
    }

    sealed interface Dialog : MapAction {
        data object ConfirmPinName : Dialog
        data object DismissPinName : Dialog
        data object Confirm : Dialog
        data object DismissConfirm : Dialog
        /** Deletes the stop or day although other people's content goes with it. */
        data object DeleteAnyway : Dialog
        data object DismissDeleteAnyway : Dialog
        data class SendReport(val reason: ReportReason) : Dialog
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

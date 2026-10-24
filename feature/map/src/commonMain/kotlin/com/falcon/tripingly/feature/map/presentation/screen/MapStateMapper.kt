package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_day_label
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.generated.resources.map_stop_title_format
import com.falcon.tripingly.feature.map.generated.resources.map_trip_created
import com.falcon.tripingly.feature.map.generated.resources.map_trip_owner
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import com.falcon.tripingly.feature.map.presentation.component.durationText
import com.falcon.tripingly.feature.map.presentation.component.placePinSize
import com.falcon.tripingly.feature.map.presentation.component.routeLine
import com.falcon.tripingly.feature.map.presentation.component.stopPathShape
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate

/** Builds [MapUiState] from the trip and the screen's [MapModel], one part per thing on screen. */
internal class MapStateMapper(
    private val details: TripDetails?,
    private val model: MapModel,
    private val config: AppConfig,
    private val myUserId: String?,
) {
    private val days = details?.days.orEmpty()
    private val activeIndex = activeDayIndex(days.size, model.activeDayIndex)
    private val role = details?.trip?.role
    private val canEdit = role?.canEdit == true
    /** Someone else's trip: the owner's name and stops show in the owner's colour. */
    private val otherOwnerId = details?.trip?.owner?.id?.takeIf { role != TripRole.OWNER }
    private val stops = days.getOrNull(activeIndex)?.markers.orEmpty().mapIndexed { index, it -> it.toMapMarker(index + 1) }
    private val dayNumber = days.getOrNull(activeIndex)?.let { it.position + 1 }
    private val route = model.route
    /** Only a real route is drawn; without one the day keeps its own lines between the stops. */
    private val plan = route.plan?.takeIf { !it.isEstimated }
    private val isDayRoute = route.between == null && route.isDayRouteShown
    private val routePoints = plan?.route?.points.orEmpty().map { Coordinates(it.lat, it.lng) }
        .takeIf { isDayRoute || route.between != null }.orEmpty()
    private val alongTheWay = stopPlaceIds(details).let { stopIds ->
        // A place added from the list is a stop now, until the route reloads without it.
        plan?.alongTheWay.orEmpty().filter { it.place.id !in stopIds }
    }

    fun map(): MapUiState = MapUiState(
        header = header(),
        search = model.search?.let { SearchState(it.query, it.results.toImmutableList(), it.isSearching) },
        dayTabs = DayTabsState(days.map { DayTab(it.id, it.position + 1, dayLabel(it.position + 1, it.date)) }.toImmutableList(), activeIndex),
        stopsCard = stopsCard(),
        pointRouteCard = route.between?.let { PointRouteCardState(it.from, it.to, routeSummary()) },
        routeStartName = route.start?.name,
        placeCard = placeCard(),
        stopCard = stopCard(),
        layers = layers(),
        camera = CameraState(model.camera.isReady, model.camera.target, model.camera.zoom, model.camera.openingBounds),
        location = LocationState(isLoading = model.location.isLoading),
        routeAttribution = plan?.attribution?.takeIf { routePoints.isNotEmpty() },
        sheet = sheet(),
        dialog = dialog(),
        message = model.message?.let { MessageState(it, canRetry = model.failedChanges.isNotEmpty()) },
    )

    private fun header(): HeaderState {
        val trip = details?.trip
        return HeaderState(
            tripName = trip?.name.orEmpty(),
            createdLabel = trip?.createdAt?.let { UiText.Resource(Res.string.map_trip_created, DateUtils.formatFormal(it)) },
            ownerLabel = trip?.owner?.takeIf { otherOwnerId != null }?.let { UiText.Resource(Res.string.map_trip_owner, it.displayName) },
            isBusy = model.isSaving || model.isUpdatingSharing || (days.isEmpty() && model.isLoadingTrip),
            viewerMenu = trip?.takeIf { role == TripRole.VIEWER }?.let {
                ViewerMenu(isSavedByMe = it.savedByMe, myJoinRequest = details?.myJoinRequest, ownerUsername = it.owner?.username)
            },
            editorMenu = EditorMenu(canDeleteDay = days.size > 1 && canDeleteDay()).takeIf { canEdit },
            pendingJoinRequestCount = details?.pendingJoinRequestCount ?: 0,
        )
    }

    /** Like the server: an editor only when every stop on the day is theirs. */
    private fun canDeleteDay() = role == TripRole.OWNER || (canEdit && stops.all { it.canDelete })

    private fun stopsCard(): StopsCardState? {
        if (stops.isEmpty() || route.between != null) return null
        val isRouteShown = route.isDayRouteShown && stops.size >= 2
        return StopsCardState(
            stops = stops.toImmutableList(),
            selectedStopId = (model.selection as? Selection.Stop)?.markerId,
            canClear = canDeleteDay(),
            // A route needs two stops.
            isRouteOn = route.isDayRouteShown.takeIf { stops.size >= 2 },
            route = routeSummary().takeIf { isRouteShown },
            legLabels = if (isRouteShown && isDayRoute) {
                plan?.route?.legs.orEmpty().mapNotNull { leg -> leg.toMarkerId?.let { it to durationText(leg.durationSeconds) } }.toMap().toImmutableMap()
            } else {
                emptyMap<String, UiText>().toImmutableMap()
            },
            canFindBestOrder = canEdit && stops.size in MIN_BEST_ORDER_STOPS..MAX_BEST_ORDER_STOPS,
            isFindingBestOrder = route.isFindingBestOrder,
        )
    }

    private fun routeSummary() = RouteSummaryState(
        mode = route.travelMode,
        durationSeconds = plan?.route?.durationSeconds,
        distanceMeters = plan?.route?.distanceMeters,
        isEstimated = route.isUnavailable || route.plan?.isEstimated == true,
        isLoading = route.isLoading,
        alongTheWay = alongTheWay.toImmutableList(),
        addToDayNumber = dayNumber?.takeIf { canEdit },
    )

    private fun placeCard(): PlaceCardState? {
        val selection = model.selection as? Selection.Place ?: return null
        if (!selection.isCardShown) return null
        return PlaceCardState(
            place = selection.place,
            details = selection.details,
            address = selection.address?.address,
            addToDayNumber = dayNumber?.takeIf { canEdit },
            canRouteTo = route.start != null && route.start.location != selection.place.location,
        )
    }

    private fun stopCard(): StopCardState? {
        val selection = model.selection as? Selection.Stop ?: return null
        if (!selection.isCardShown) return null
        val stop = stops.firstOrNull { it.id == selection.markerId } ?: return null
        return StopCardState(
            stop = stop,
            placeImage = stop.placeId?.takeIf { stop.coverPhotoId == null }?.let { model.stopPlaceImages[it] },
            canEdit = canEdit,
            canReport = role == TripRole.VIEWER,
            canRemoveMyContent = canEdit && !stop.canDelete,
        )
    }

    private fun layers(): MapLayersState {
        val pins = model.pins.pins
        val pinnedIds = pins.mapTo(HashSet()) { it.place.id }
        val alongIds = alongTheWay.mapTo(HashSet()) { it.place.id }
        // The places along the way show on the map too, also where the map's own pins leave them out, in their own color.
        val placePins = pins.map { if (it.place.id in alongIds) it.copy(isAlongTheWay = true) else it } +
            alongTheWay.filter { it.place.id !in pinnedIds }.map { it.toPin() }
        val stopPath = stops.map { it.position }.takeIf { routePoints.isEmpty() && route.between == null }.orEmpty()
        return MapLayersState(
            stops = stops.toImmutableList(),
            placePins = placePins.toImmutableList(),
            pinDotBelowZoom = config.pinDotBelowZoom,
            placeClusters = model.pins.clusters.toImmutableList(),
            selectedPlace = (model.selection as? Selection.Place)?.place,
            // A real route in its way of travelling's color, or else the stops joined in order.
            line = when {
                routePoints.size > 1 -> routeLine(routePoints, route.planStops.map { Coordinates(it.lat, it.lng) }, route.planMode)
                stopPath.size > 1 -> stopPathShape(stopPath)
                else -> null
            },
            isMyLocationEnabled = model.location.permission == LocationPermission.Granted,
        )
    }

    private fun AlongTheWayPlace.toPin() = PlacePin(place, placePinSize(place, config), isAlongTheWay = true)

    private fun sheet(): MapSheet? = when (val sheet = model.sheet) {
        is Sheet.StopPhotos -> stops.firstOrNull { it.id == sheet.markerId }?.let { MapSheet.StopPhotos(it, canEdit) }
        is Sheet.PlacePhotos -> MapSheet.PlacePhotos(sheet.place)
        is Sheet.StopComments -> stops.firstOrNull { it.id == sheet.markerId }?.let(MapSheet::StopComments)
        Sheet.Members -> MapSheet.Members
        Sheet.PhotoCamera -> MapSheet.PhotoCamera.takeIf { stops.any { it.id == (model.selection as? Selection.Stop)?.markerId } }
        null -> placeImage()
    }

    /** A card image opens in the place's photo viewer, so its heart is the same like as in the Photos sheet. */
    private fun placeImage(): MapSheet? {
        val selection = model.selection as? Selection.Place ?: return null
        val details = selection.details?.takeIf { it.id == selection.place.id } ?: return null
        val image = selection.imageIndex?.let { details.images.getOrNull(it) } ?: return null
        return MapSheet.PlaceImage(details.id, image.id)
    }

    private fun dialog(): MapDialog? = when (val dialog = model.dialog) {
        Dialog.ClearDay -> MapDialog.ClearDay(dayNumber ?: 1, stops.size)
        Dialog.DeleteDay -> MapDialog.DeleteDay(dayNumber ?: 1, stops.size)
        Dialog.RemoveMyContent -> MapDialog.RemoveMyContent
        is Dialog.OthersContent -> MapDialog.DeleteOthersContent(dialog.message)
        is Dialog.Report -> MapDialog.Report(dialog.isSending)
        is Dialog.NamePin -> MapDialog.NamePin
        is Dialog.Location -> MapDialog.Location(dialog.prompt)
        is Dialog.BestOrder -> MapDialog.BestOrder(
            BestOrderPreview(
                stops = dialog.order.markerIds.mapNotNull { id -> stops.firstOrNull { it.id == id } }.toImmutableList(),
                byTravelTime = dialog.order.byTravelTime,
                savedMinutes = dialog.order.savedMinutes,
            ),
        )
        null -> null
    }

    /**
     * [orderNumber] is the place in the day's list, which stays 1, 2, 3 while a delete waits for the reload.
     * Only the stop's creator and the trip's owner may delete it.
     */
    private fun TripMarker.toMapMarker(orderNumber: Int) = MapMarker(
        id = id,
        position = Coordinates(location.lat, location.lng),
        title = if (name.isBlank()) UiText.Resource(Res.string.map_stop_title_format, orderNumber) else UiText.Server(name),
        orderNumber = orderNumber,
        snippet = time,
        coverPhotoId = coverPhotoId,
        coverThumbUrl = coverThumbUrl,
        photoCount = photoCount,
        hotCount = hotCount,
        hotByMe = hotByMe,
        canMarkHot = canMarkHot,
        commentCount = commentCount,
        placeId = placeId,
        canDelete = role == TripRole.OWNER || (canEdit && myUserId != null && createdById == myUserId),
        isByTripOwner = otherOwnerId != null && createdById == otherOwnerId,
    )

    private fun dayLabel(number: Int, date: LocalDate?): UiText =
        if (date != null) {
            UiText.Resource(Res.string.map_day_label, number, DateUtils.formatAbbreviated(date))
        } else {
            UiText.Resource(Res.string.map_day_label_no_date, number)
        }

    private companion object {
        /** With two stops there's only one order (the first stays first); the server orders up to 25. */
        const val MIN_BEST_ORDER_STOPS = 3
        const val MAX_BEST_ORDER_STOPS = 25
    }
}

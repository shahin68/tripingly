package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_stop_title_format
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_more
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_only
import com.falcon.tripingly.feature.map.presentation.component.MapLayersState
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import com.falcon.tripingly.feature.map.presentation.component.placePinSize
import com.falcon.tripingly.feature.map.presentation.component.routeLine
import com.falcon.tripingly.feature.map.presentation.component.stopPathShape
import com.falcon.tripingly.feature.map.presentation.map.state.Dialog
import com.falcon.tripingly.feature.map.presentation.map.state.MapModel
import com.falcon.tripingly.feature.map.presentation.map.state.Selection
import com.falcon.tripingly.feature.map.presentation.map.state.Sheet
import com.falcon.tripingly.feature.map.presentation.map.state.activeDayIndex
import com.falcon.tripingly.feature.map.presentation.map.state.stopPlaceIds
import com.falcon.tripingly.feature.map.presentation.map.ui.durationText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

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
    /** Someone else's trip: the header says whose. */
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

    /** [layers] can be ones built before from the same trip and model; see [MapViewModel]. */
    fun map(layers: MapLayersState = layers()): MapUiState = MapUiState(
        loadError = model.loadError,
        header = header(),
        joinRequests = joinRequests(),
        search = model.search?.let { SearchState(it.query, it.results.toImmutableList(), it.isSearching) },
        dayTabs = DayTabsState(days.map { day -> DayTab(day.id, day.position + 1, day.date?.let(DateUtils::formatAbbreviated), hasNew = day.markers.any { it.id in model.newStopIds || it.id in model.newCommentStopIds }, canDelete = canEdit && days.size > 1 && canDeleteDay(day.markers)) }.toImmutableList(), activeIndex),
        plan = route.between?.let { PointRouteCardState(it.from, it.to, routeSummary(), it.fromLabel, it.toLabel) } ?: stopsCard(),
        routeBar = route.routeBar(),
        // The card stays open under a sheet, so closing the sheet comes back to it.
        placeCard = placeCard(),
        stopCard = stopCard(),
        layers = layers,
        camera = CameraState(model.camera.isReady, model.camera.target, model.camera.zoom, model.camera.openingBounds),
        location = LocationState(isLoading = model.location.isLoading),
        arePlacesHidden = model.arePlacesHidden,
        routeAttribution = plan?.attribution?.takeIf { routePoints.isNotEmpty() },
        sheet = sheet(),
        dialog = dialog(),
        message = model.message?.let { MessageState(it, canRetry = model.failedChanges.isNotEmpty()) },
    )

    private fun header(): HeaderState {
        val trip = details?.trip
        val faces = details?.members.orEmpty().sortedByDescending { it.role == TripRole.OWNER }.take(TRIP_CARD_FACES).map { it.user }.toImmutableList()
        val memberCount = details?.members?.size ?: 0
        return HeaderState(
            tripName = trip?.name.orEmpty(),
            owner = trip?.owner,
            card = TripCardState(
                dates = trip?.let { DateUtils.formatShortRange(it.startDate, it.endDate) },
                likeCount = details?.likeCount ?: 0,
                saveCount = details?.saveCount ?: 0,
                isPublic = trip?.visibility != TripVisibility.PRIVATE,
                faces = faces,
                memberCount = memberCount,
                membersNote = when {
                    memberCount > faces.size -> UiText.Resource(Res.string.map_trip_card_more, memberCount - faces.size)
                    memberCount == 1 -> faces.firstOrNull()?.let { UiText.Resource(Res.string.map_trip_card_only, it.displayName) }
                    else -> null
                },
                hasNewJoinRequests = model.joinRequesters.any { it.id !in model.dismissedJoinRequesterIds },
            ),
            members = details?.members?.takeIf { it.isNotEmpty() }?.let { MembersEntry(it.size, details.pendingJoinRequestCount) },
            isLoading = days.isEmpty() && model.isLoadingTrip,
            isBusy = model.isSaving || model.isUpdatingSharing,
            viewerMenu = trip?.takeIf { role == TripRole.VIEWER }?.let {
                ViewerMenu(isSavedByMe = it.savedByMe, myJoinRequest = details.myJoinRequest, ownerUsername = it.owner.username)
            },
            editorMenu = EditorMenu(canDeleteDay = days.size > 1 && canDeleteDay()).takeIf { canEdit },
        )
    }

    private fun joinRequests(): JoinRequestsState? {
        val people = model.joinRequesters
        if (model.search != null || people.all { it.id in model.dismissedJoinRequesterIds }) return null
        val count = maxOf(details?.pendingJoinRequestCount ?: 0, people.size)
        return JoinRequestsState(people.toImmutableList(), othersCount = count - 1)
    }

    /** Like the server: an editor only when every stop on the day is theirs. */
    private fun canDeleteDay(markers: List<TripMarker> = days.getOrNull(activeIndex)?.markers.orEmpty()) =
        role == TripRole.OWNER || (canEdit && myUserId != null && markers.all { it.createdById == myUserId })

    private fun stopsCard(): StopsCardState? {
        if (stops.isEmpty() || route.between != null) return null
        val isRouteShown = route.isDayRouteShown && stops.size >= 2
        return StopsCardState(
            stops = stops.toImmutableList(),
            selectedStopId = (model.selection as? Selection.Stop)?.markerId,
            canClear = canDeleteDay(),
            // A route needs two stops.
            isRouteOn = route.isDayRouteShown.takeIf { stops.size >= 2 },
            isRouteFailed = isRouteShown && route.isUnavailable && !route.isLoading,
            route = routeSummary().takeIf { isRouteShown },
            legLabels = if (isRouteShown && isDayRoute) {
                plan?.route?.legs.orEmpty().mapNotNull { leg -> leg.toMarkerId?.let { it to durationText(leg.durationSeconds) } }.toMap().toImmutableMap()
            } else {
                emptyMap<String, UiText>().toImmutableMap()
            },
            isRouteAnimated = route.isAnimated,
            canFindBestOrder = canEdit && stops.size in MIN_BEST_ORDER_STOPS..MAX_BEST_ORDER_STOPS,
            isFindingBestOrder = route.isFindingBestOrder,
        )
    }

    private fun placeCard(): PlaceCardState? {
        val selection = model.selection as? Selection.Place ?: return null
        if (!selection.isCardShown) return null
        return placeDetails(selection)
    }

    private fun placeDetails(selection: Selection.Place) = PlaceCardState(
        place = selection.place,
        details = selection.details,
        address = selection.address?.address,
        addToDayNumber = dayNumber?.takeIf { canEdit },
        routes = pinRoutes(selection.place.location),
    )

    private fun stopCard(): StopCardState? {
        val selection = model.selection as? Selection.Stop ?: return null
        if (!selection.isCardShown) return null
        return stopDetails(selection)
    }

    private fun stopDetails(selection: Selection.Stop): StopCardState? {
        val stop = stops.firstOrNull { it.id == selection.markerId } ?: return null
        val place = stop.placeId?.let { model.stopPlaces[it] }
        return StopCardState(
            stop = stop,
            coverCredit = place?.images?.firstOrNull { it.id == stop.coverPhotoId }?.credit,
            place = place,
            canEdit = canEdit,
            canReport = role == TripRole.VIEWER,
            routes = pinRoutes(GeoPoint(stop.position.latitude, stop.position.longitude)),
        )
    }

    private fun routeSummary() = route.summary(plan, alongTheWay, addToDayNumber = dayNumber?.takeIf { canEdit })

    private fun pinRoutes(at: GeoPoint) = route.pinRoutes(at, hasLocation = model.location.current != null)

    fun layers(): MapLayersState {
        val pins = model.pins.pins
        val pinnedIds = pins.mapTo(HashSet()) { it.place.id }
        val alongIds = alongTheWay.mapTo(HashSet()) { it.place.id }
        // The places along the way show on the map too, also where the map's own pins leave them out, in their own color.
        // While a route shows, the other pins are dots, so the route, its two ends, the stops and the places along it stand out.
        val routeShows = routePoints.size > 1
        val routeEndIds = route.between?.let { setOf(it.from.id, it.to.id) }.orEmpty()
        // Hidden places come first: only the route's places stay, then the route makes the rest dots as always.
        val visible = if (model.arePlacesHidden) pins.filter { it.place.id in alongIds || it.place.id in routeEndIds } else pins
        val placePins = visible.map {
            when {
                it.place.id in alongIds -> it.copy(isAlongTheWay = true)
                routeShows && it.place.id !in routeEndIds -> it.copy(isDot = true)
                else -> it
            }
        } +
            alongTheWay.filter { it.place.id !in pinnedIds }.map { it.toPin() }
        val stopPath = stops.map { it.position }.takeIf { routePoints.isEmpty() && route.between == null }.orEmpty()
        return MapLayersState(
            stops = stops.toImmutableList(),
            placePins = placePins.toImmutableList(),
            pinDotBelowZoom = config.pinDotBelowZoom,
            placeClusters = model.pins.clusters.takeUnless { model.arePlacesHidden }.orEmpty().toImmutableList(),
            // A place from search knows no cover image, so its pin's place draws it, photo included.
            selectedPlace = (model.selection as? Selection.Place)?.place?.let { place -> placePins.firstOrNull { it.place.id == place.id }?.place ?: place },
            // A real route in its way of travelling's color, or else the stops joined in order.
            line = when {
                routePoints.size > 1 -> routeLine(routePoints, route.planStops.map { Coordinates(it.lat, it.lng) }, route.planMode)
                stopPath.size > 1 -> stopPathShape(stopPath)
                else -> null
            },
            isMyLocationEnabled = model.location.permission == LocationPermission.Granted,
            routeStart = route.startRing(),
        )
    }

    private fun AlongTheWayPlace.toPin() = PlacePin(place, placePinSize(place, config), isAlongTheWay = true)

    private fun sheet(): MapSheet? = when (val sheet = model.sheet) {
        is Sheet.StopPhotos -> stops.firstOrNull { it.id == sheet.markerId }?.let { MapSheet.StopPhotos(it, canEdit) }
        is Sheet.PlacePhotos -> MapSheet.PlacePhotos(sheet.place)
        is Sheet.StopComments -> stops.firstOrNull { it.id == sheet.markerId }?.let { MapSheet.StopComments(it, tripPeople()) }
        Sheet.Members -> MapSheet.Members
        is Sheet.Details -> when (val selection = model.selection) {
            is Selection.Stop -> stopDetails(selection)?.let { MapSheet.StopDetails(it) }
            is Selection.Place -> MapSheet.PlaceDetails(placeDetails(selection))
            null -> null
        }
        null -> placeImage()
    }

    /** The trip's owner and members but me: the people my comment can @mention. */
    private fun tripPeople(): ImmutableList<UserSummary> =
        (listOfNotNull(details?.trip?.owner) + details?.members.orEmpty().map { it.user }).distinctBy { it.id }.filter { it.id != myUserId }.toImmutableList()

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
        is Dialog.DeleteOthersStop -> stops.firstOrNull { it.id == dialog.markerId }?.let { MapDialog.DeleteOthersStop(it.title) }
        is Dialog.OthersContent -> MapDialog.DeleteOthersContent(dialog.message)
        is Dialog.Report -> MapDialog.Report(dialog.isSending, dialog.details)
        is Dialog.NamePin -> MapDialog.NamePin(dialog.name)
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
        isNew = id in model.newStopIds || id in model.newCommentStopIds,
    )

    private companion object {
        /** With two stops there's only one order (the first stays first); the server orders up to 25. */
        const val MIN_BEST_ORDER_STOPS = 3
        const val MAX_BEST_ORDER_STOPS = 25
    }
}

/** The trip card shows at most this many members' faces; the rest as "and 2 more". */
private const val TRIP_CARD_FACES = 3

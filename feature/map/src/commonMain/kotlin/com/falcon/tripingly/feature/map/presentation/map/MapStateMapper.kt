package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.data.push.NewsKind
import com.falcon.tripingly.core.data.push.StopNews
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceImageSource
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_more
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_only
import com.falcon.tripingly.feature.map.presentation.component.COVER_PIN_SIZE
import com.falcon.tripingly.feature.map.presentation.component.MapLayersState
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.PIN_SIZE
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import com.falcon.tripingly.feature.map.presentation.component.RouteEndMark
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.component.lineColor
import com.falcon.tripingly.feature.map.presentation.component.placePinSize
import com.falcon.tripingly.feature.map.presentation.component.routeLine
import com.falcon.tripingly.feature.map.presentation.component.stopPathShape
import com.falcon.tripingly.feature.map.presentation.map.state.Dialog
import com.falcon.tripingly.feature.map.presentation.map.state.MapModel
import com.falcon.tripingly.feature.map.presentation.map.state.Selection
import com.falcon.tripingly.feature.map.presentation.map.state.Sheet
import com.falcon.tripingly.feature.map.presentation.map.state.activeDayIndex
import com.falcon.tripingly.feature.map.presentation.map.state.isActive
import com.falcon.tripingly.feature.map.presentation.map.state.stopPlaceIds
import com.falcon.tripingly.feature.map.presentation.map.state.toState
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
    /**
     * Stops with news that marks their pin and day tab: any kind, flames too, until the flame has shown in the
     * expanded card or the details (Shahin, 2026-11-13). Set before [stops], which reads it.
     */
    private val markedStopIds = model.news.mapTo(HashSet()) { it.markerId }
    private val stops = days.getOrNull(activeIndex)?.markers.orEmpty().mapIndexed { index, it -> it.toMapMarker(index + 1) }
    private val dayNumber = days.getOrNull(activeIndex)?.let { it.position + 1 }
    /** A viewer's + puts a stop or place on one of my trips; an editor's puts a place on the open day. */
    private val copyAdd = PinAdd.ToMyTrip.takeIf { role != null && !canEdit }
    private val route = model.route
    /** Only a real route is drawn; without one the day keeps its own lines between the stops. */
    private val plan = route.plan?.takeIf { !it.isEstimated }
    private val isDayRoute = route.between == null && route.isDayRouteShown
    private val routePoints = plan?.route?.points.orEmpty().map { Coordinates(it.lat, it.lng) }
        .takeIf { isDayRoute || route.between != null }.orEmpty()
    private val alongTheWay = stopPlaceIds(days.getOrNull(activeIndex)).let { stopIds ->
        // A place added from the list is a stop now, until the route reloads without it; the route's own ends never are
        // (an older server lists them).
        val endIds = route.endPlaces().mapTo(HashSet()) { (place, _) -> place.id }
        plan?.alongTheWay.orEmpty().filter { it.place.id !in stopIds && it.place.id !in endIds }
    }

    /** [layers] can be ones built before from the same trip and model; see [MapViewModel]. */
    fun map(layers: MapLayersState = layers()): MapUiState = MapUiState(
        loadError = model.loadError,
        header = header(),
        joinRequests = joinRequests(),
        search = model.search?.let { SearchState(it.query, it.results.toImmutableList(), it.isSearching) },
        dayTabs = DayTabsState(days.map { day -> DayTab(day.id, day.position + 1, day.date?.let(DateUtils::formatAbbreviated), hasNew = day.markers.any { it.id in markedStopIds }, canDelete = canEdit && days.size > 1 && canDeleteDay(day.markers)) }.toImmutableList(), activeIndex),
        plan = route.between?.let { PointRouteCardState(it.from, it.to, routeSummary(), it.fromLabel, it.toLabel) } ?: stopsCard(),
        routeBar = route.routeBar(),
        // The card stays open under a sheet, so closing the sheet comes back to it.
        placeCard = placeCard(),
        stopCard = stopCard(),
        layers = layers,
        camera = CameraState(model.camera.isReady, model.camera.target, model.camera.zoom, model.camera.openingBounds),
        location = model.location.let {
            val isSwitchedOff = it.isSwitchedOff && (it.isFollowing || it.isWaitingForFirst)
            LocationState(isLoading = it.isLoading && !it.isSwitchedOff, isFollowing = it.isFollowing, isSignalLost = it.isSignalLost && !isSwitchedOff, isSwitchedOff = isSwitchedOff)
        },
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
        add = copyAdd ?: dayNumber?.takeIf { canEdit }?.let(PinAdd::ToDay),
        routes = pinRoutes(selection.place.location),
        addedBy = selection.details?.addedBy?.let { AddedBy(it, isMe = it.id == myUserId) },
        images = placeImages(selection),
    )

    private fun stopCard(): StopCardState? {
        val selection = model.selection as? Selection.Stop ?: return null
        if (!selection.isCardShown) return null
        return stopDetails(selection)
    }

    private fun stopDetails(selection: Selection.Stop): StopCardState? {
        val stop = stops.firstOrNull { it.id == selection.markerId } ?: return null
        val place = stop.placeId?.let { model.stopPlaces[it] }
        val marker = days.getOrNull(activeIndex)?.markers?.firstOrNull { it.id == stop.id }
        val creatorId = marker?.createdById
        return StopCardState(
            stop = stop,
            images = stopImages(stop, model.cardPhotos[PhotoOwner.Stop(stop.id)], place?.images.orEmpty()),
            place = place,
            canEdit = canEdit,
            canReport = role == TripRole.VIEWER,
            routes = pinRoutes(GeoPoint(stop.position.latitude, stop.position.longitude)),
            add = copyAdd,
            addedBy = addedBy(creatorId, marker?.createdBy),
            spotBy = place?.addedBy?.takeIf { it.id != creatorId }?.let { AddedBy(it, isMe = it.id == myUserId) },
            news = StopCardNews(
                comments = StopNews(stop.id, NewsKind.Comments) in model.news,
                photos = StopNews(stop.id, NewsKind.Photos) in model.news,
                hot = StopNews(stop.id, NewsKind.Hot) in model.news,
            ),
        )
    }

    /**
     * Who added a stop, shown once a trip has editors or the stop isn't the owner's (on a trip only its owner edits,
     * every stop would name the header's owner); nothing when the server names no one (a deleted account, a block).
     */
    private fun me(): UserSummary? = details?.members?.firstOrNull { it.user.id == myUserId }?.user ?: details?.trip?.owner?.takeIf { it.id == myUserId }

    private fun addedBy(creatorId: String?, creator: UserSummary?): AddedBy? {
        if (creatorId == null || (creatorId == details?.trip?.owner?.id && details?.members.orEmpty().none { it.role == TripRole.EDITOR })) return null
        return when {
            // Mine added a moment ago comes without me from the server yet: my face from the trip's people.
            creatorId == myUserId -> AddedBy(creator ?: me(), isMe = true)
            creator != null -> AddedBy(creator, isMe = false)
            else -> null
        }
    }

    private fun routeSummary() = route.summary(plan, alongTheWay, selectedPlaceId = (model.selection as? Selection.Place)?.place?.id, addToDayNumber = dayNumber?.takeIf { canEdit })

    private fun pinRoutes(at: GeoPoint) = route.pinRoutes(at, hasLocation = model.location.current != null)

    fun layers(): MapLayersState {
        val pins = model.pins.pins
        val alongIds = alongTheWay.mapTo(HashSet()) { it.place.id }
        // The route's ends: a stop keeps its own pin and gets the ring round it; a place's pin grows (Shahin, 2026-11-13).
        val ends = route.endPlaces().map { (place, isStart) -> RouteEnd(place, isStart, stopAt(place)) }
        val endPlaces = ends.filter { it.stop == null }.associateBy { it.place.id }
        // Places tapped while routing stay pins until the route closes.
        val kept = route.kept.takeIf { route.isActive }.orEmpty().associateBy { it.id }
        // The places along the way show on the map too, also where the map's own pins leave them out, in their own color.
        // While a route shows, the other pins are dots, so the route, its two ends, the stops and the places along it stand out.
        val routeShows = routePoints.size > 1
        // Hidden places come first: only the route's places stay, then the route makes the rest dots as always.
        val isRoutes = { id: String -> id in alongIds || id in endPlaces || id in kept }
        val visible = if (model.arePlacesHidden) pins.filter { isRoutes(it.place.id) } else pins
        val shown = visible.mapTo(HashSet()) { it.place.id }
        val placePins = visible.map {
            when {
                it.place.id in endPlaces -> it.copy(size = SelectedPinSize, isAlongTheWay = false, isKept = true)
                it.place.id in kept -> it.copy(isAlongTheWay = it.place.id in alongIds, isKept = true)
                it.place.id in alongIds -> it.copy(isAlongTheWay = true)
                routeShows -> it.copy(isDot = true)
                else -> it
            }
        } +
            endPlaces.values.filter { it.place.id !in shown }.map { PlacePin(it.place, SelectedPinSize, isKept = true) } +
            kept.values.filter { it.id !in shown && it.id !in endPlaces }.map { PlacePin(it, placePinSize(it, config), isAlongTheWay = it.id in alongIds, isKept = true) } +
            alongTheWay.filter { it.place.id !in shown && it.place.id !in endPlaces && it.place.id !in kept }.map { it.toPin() }
        val stopPath = stops.map { it.position }.takeIf { routePoints.isEmpty() && route.between == null }.orEmpty()
        val endColor = route.travelMode.lineColor
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
            routeEnds = ends.map { end ->
                val size = end.stop?.let { if (it.coverPhotoId != null) COVER_PIN_SIZE else PIN_SIZE } ?: SelectedPinSize
                RouteEndMark(end.place.location, end.isStart, size, endColor)
            }.toImmutableList(),
        )
    }

    /** One end of the route on the map: a stop's ([stop]) or a place's. */
    private class RouteEnd(val place: MapPlace, val isStart: Boolean, val stop: MapMarker?)

    /** The open day's stop a route's [end] stands for: a stop is an end as its place, or as itself. */
    private fun stopAt(end: MapPlace): MapMarker? = stops.firstOrNull { (it.placeId ?: it.id) == end.id }

    private fun AlongTheWayPlace.toPin() = PlacePin(place, placePinSize(place, config), isAlongTheWay = true)

    /** Opened from the details, while they're still there to go back to. */
    private val Sheet.fromOpenDetails get() = fromDetails && model.selection != null

    private fun sheet(): MapSheet? = when (val sheet = model.sheet) {
        is Sheet.StopPhotos -> stops.firstOrNull { it.id == sheet.markerId }?.let { MapSheet.StopPhotos(it, canEdit, sheet.fromOpenDetails) }
        is Sheet.PlacePhotos -> MapSheet.PlacePhotos(sheet.place, sheet.fromOpenDetails)
        is Sheet.StopComments -> stops.firstOrNull { it.id == sheet.markerId }?.let { MapSheet.StopComments(it, tripPeople(), sheet.fromOpenDetails) }
        Sheet.Members -> MapSheet.Members
        // The days go back to the trips, the trips to the details they were opened from.
        is Sheet.AddToTrip -> MapSheet.AddToTrip(sheet.toState(stops), hasBack = sheet.trip != null || sheet.fromOpenDetails)
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
        val image = selection.imageIndex?.let { placeImages(selection)?.getOrNull(it) } ?: return null
        return MapSheet.PlaceImage(selection.place.id, image.id)
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
     * A stop's images: its cover first, then the rest of its photo grid's ready photos ([gallery]: its trip's, its
     * place's popular ones and its Commons images), or its place's images until the grid is loaded. A cover found in
     * neither is shown from its thumbnail, the only size the stop knows.
     */
    private fun stopImages(stop: MapMarker, gallery: List<Photo>?, placeImages: List<PlaceImage>): List<PlaceImage> {
        val shown = gallery?.readyImages() ?: placeImages
        val coverId = stop.coverPhotoId ?: return shown
        val cover = (shown + placeImages).firstOrNull { it.id == coverId }
            ?: PlaceImage(coverId, PlaceImageSource.Trip, thumbUrl = stop.coverThumbUrl, displayUrl = null, width = null, height = null, credit = null)
        return listOf(cover) + shown.filter { it.id != coverId }
    }

    /**
     * What a place's card swipes through: its gallery, the same photos as its Photos sheet (Shahin, 2026-11-14), or its
     * own images until the gallery is loaded (null while those load too); none for an address from search.
     */
    private fun placeImages(selection: Selection.Place): List<PlaceImage>? = when {
        selection.address != null -> emptyList()
        else -> model.cardPhotos[PhotoOwner.Place(selection.place.id)]?.readyImages() ?: selection.details?.images
    }

    private fun List<Photo>.readyImages() = filter { it.status == PhotoStatus.Ready }.map { it.toPlaceImage() }

    private fun Photo.toPlaceImage() = PlaceImage(
        id = id,
        source = if (credit != null) PlaceImageSource.Commons else PlaceImageSource.Trip,
        thumbUrl = thumbUrl,
        displayUrl = displayUrl,
        width = width,
        height = height,
        credit = credit,
        uploader = uploader,
    )

    /**
     * [orderNumber] is the place in the day's list, which stays 1, 2, 3 while a delete waits for the reload.
     * Only the stop's creator and the trip's owner may delete it.
     */
    private fun TripMarker.toMapMarker(orderNumber: Int) = MapMarker(
        id = id,
        position = Coordinates(location.lat, location.lng),
        title = UiText.Server(name),
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
        isNew = id in markedStopIds,
    )

    private companion object {
        /** With two stops there's only one order (the first stays first); the server orders up to 25. */
        const val MIN_BEST_ORDER_STOPS = 3
        const val MAX_BEST_ORDER_STOPS = 25
    }
}

/** The trip card shows at most this many members' faces; the rest as "and 2 more". */
private const val TRIP_CARD_FACES = 3

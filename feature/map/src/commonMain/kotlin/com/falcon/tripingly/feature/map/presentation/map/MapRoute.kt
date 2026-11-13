package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsRoute
import com.falcon.tripingly.feature.map.presentation.map.ui.MapDialogs
import com.falcon.tripingly.feature.map.presentation.map.ui.PlaceDetailsContent
import com.falcon.tripingly.feature.map.presentation.map.ui.StopDetailsContent
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetValue
import com.falcon.tripingly.feature.map.presentation.map.ui.rememberPlanSheetState
import com.falcon.tripingly.feature.map.presentation.photos.PhotoStripRoute
import com.falcon.tripingly.feature.map.presentation.photos.PhotoViewerRoute
import com.falcon.tripingly.feature.map.presentation.photos.PhotosRoute
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MapRoute(
    tripId: String,
    onNavigateBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    /** Tripinly Pro's paywall, when a free user taps a route. */
    onOpenPaywall: () -> Unit,
    /** The trip's members sheet (from the trips feature), where the owner answers requests to join. */
    membersContent: @Composable (tripId: String, onDismiss: () -> Unit) -> Unit,
    /** The stop a push opened the trip on, if any. */
    stopId: String? = null,
    /** A push about a request to join: the Members sheet opens. */
    showMembers: Boolean = false,
    /** A push about [stopId]'s comments: its comments open. */
    showComments: Boolean = false,
    // Keyed by trip: the view model store is shared across screens, and the class alone would
    // hand every trip the first trip's view model.
    viewModel: MapViewModel = koinViewModel(key = "map:$tripId") { parametersOf(tripId, stopId, showMembers, showComments) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onAction = viewModel::onAction

    val locationPermission = rememberLocationPermissionController { permission ->
        onAction(MapAction.Location.PermissionResult(permission))
    }
    // On open and on every return to the app, e.g. from Settings.
    LifecycleResumeEffect(locationPermission) {
        onAction(MapAction.Location.PermissionChecked(locationPermission.status()))
        onPauseOrDispose {}
    }

    val planSheet = rememberPlanSheetState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MapEvent.RequestPermission -> locationPermission.request()
            MapEvent.OpenAppSettings -> locationPermission.openSettings()
            MapEvent.NavigateBack -> onNavigateBack()
            is MapEvent.OpenProfile -> onOpenProfile(event.username)
            // A locked feature (street routes without Pro) is felt as a no before the paywall.
            MapEvent.OpenPaywall -> {
                haptics.error()
                onOpenPaywall()
            }
            MapEvent.ShowWholePlan -> scope.launch { planSheet.animateTo(PlanSheetValue.Expanded) }
            MapEvent.StopAdded -> {
                haptics.success()
                scope.launch { planSheet.animateTo(PlanSheetValue.Expanded) }
            }
        }
    }

    MapScreen(
        state = state,
        cameraCommands = viewModel.cameraCommands,
        planSheet = planSheet,
        onAction = onAction,
    )

    // Photos, comments and members slide up as sheets; the image viewer is full screen.
    val sheet = state.sheet
    val onReportPhoto: (Photo) -> Unit = { photo -> onAction(MapAction.Sheet.ReportPhoto(photo)) }
    AppBottomSheet(value = sheet?.takeUnless { it is MapSheet.PlaceImage || it.isDetails }, onDismiss = { sheet?.let { onAction(it.closeAction()) } }) { shown, close ->
        when (shown) {
            is MapSheet.StopPhotos -> PhotosRoute(
                owner = PhotoOwner.Stop(shown.stop.id),
                title = shown.stop.title.asString(),
                canAdd = shown.canEdit,
                canManage = shown.canEdit,
                coverPhotoId = shown.stop.coverPhotoId,
                onReport = onReportPhoto,
                onDismiss = close,
            )
            is MapSheet.PlacePhotos -> PlacePhotos(shown.place, onReportPhoto, onDismiss = close)
            is MapSheet.StopComments -> StopCommentsRoute(marker = shown.stop, people = shown.people, onOpenProfile = onOpenProfile, onDismiss = close)
            MapSheet.Members -> membersContent(tripId, close)
            is MapSheet.PlaceImage, is MapSheet.StopDetails, is MapSheet.PlaceDetails -> Unit
        }
    }
    // A pin's details open half high, in a sheet of their own. Its photos and comments open in their own sheets in its
    // place, one sheet at a time, and closing them brings the details back.
    AppBottomSheet(value = sheet?.takeIf { it.isDetails }, onDismiss = { onAction(MapAction.Sheet.CloseDetails) }, halfHigh = true) { shown, close ->
        when (shown) {
            is MapSheet.StopDetails -> StopDetailsContent(shown.card, onAction, onClose = close) {
                PhotoStripRoute(PhotoOwner.Stop(shown.card.stop.id), onShowAll = { onAction(MapAction.StopCard.OpenPhotos) }, onReport = onReportPhoto)
            }
            is MapSheet.PlaceDetails -> PlaceDetailsContent(shown.card, onAction, onClose = close) {
                // An address from search isn't one of our places: it has no photos.
                if (shown.card.address == null) {
                    PhotoStripRoute(PhotoOwner.Place(shown.card.place.id), onShowAll = { onAction(MapAction.PlaceCard.OpenPhotos) }, onReport = onReportPhoto)
                }
            }
            else -> Unit
        }
    }
    if (sheet is MapSheet.PlaceImage) {
        PhotoViewerRoute(
            owner = PhotoOwner.Place(sheet.placeId),
            photoId = sheet.photoId,
            onReport = onReportPhoto,
            onDismiss = { onAction(MapAction.Sheet.ClosePlaceImage) },
        )
    }

    state.dialog?.let { dialog -> MapDialogs(dialog, onAction) }
}

/** A place's photos, which anyone can add to. */
@Composable
private fun PlacePhotos(place: MapPlace, onReport: (Photo) -> Unit, onDismiss: () -> Unit) {
    PhotosRoute(
        owner = PhotoOwner.Place(place.id),
        title = place.name,
        canAdd = true,
        canManage = false,
        coverPhotoId = null,
        onReport = onReport,
        onDismiss = onDismiss,
    )
}

private val MapSheet.isDetails get() = this is MapSheet.StopDetails || this is MapSheet.PlaceDetails

private fun MapSheet.closeAction(): MapAction = when (this) {
    is MapSheet.StopPhotos -> MapAction.Sheet.CloseStopPhotos
    is MapSheet.PlacePhotos -> MapAction.Sheet.ClosePlacePhotos
    is MapSheet.StopComments -> MapAction.Sheet.CloseStopComments
    MapSheet.Members -> MapAction.Sheet.CloseMembers
    is MapSheet.PlaceImage -> MapAction.Sheet.ClosePlaceImage
    is MapSheet.StopDetails, is MapSheet.PlaceDetails -> MapAction.Sheet.CloseDetails
}

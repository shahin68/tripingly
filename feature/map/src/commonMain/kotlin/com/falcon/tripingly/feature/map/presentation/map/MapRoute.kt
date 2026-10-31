package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsRoute
import com.falcon.tripingly.feature.map.presentation.map.ui.MapDialogs
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetValue
import com.falcon.tripingly.feature.map.presentation.map.ui.rememberPlanSheetState
import com.falcon.tripingly.feature.map.presentation.photos.PhotoCameraDialog
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
    // Keyed by trip: the view model store is shared across screens, and the class alone would
    // hand every trip the first trip's view model.
    viewModel: MapViewModel = koinViewModel(key = "map:$tripId") { parametersOf(tripId) }
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
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MapEvent.RequestPermission -> locationPermission.request()
            MapEvent.OpenAppSettings -> locationPermission.openSettings()
            MapEvent.NavigateBack -> onNavigateBack()
            is MapEvent.OpenProfile -> onOpenProfile(event.username)
            MapEvent.OpenPaywall -> onOpenPaywall()
            MapEvent.ShowWholePlan -> scope.launch { planSheet.animateTo(PlanSheetValue.Expanded) }
        }
    }

    MapScreen(
        state = state,
        cameraCommands = viewModel.cameraCommands,
        planSheet = planSheet,
        onAction = onAction,
    )

    // Photos, comments and members slide up as sheets; the image viewer and the camera are full screen.
    val sheet = state.sheet
    AppBottomSheet(value = sheet?.takeUnless { it is MapSheet.PlaceImage || it is MapSheet.PhotoCamera }, onDismiss = { sheet?.let { onAction(it.closeAction()) } }) { shown, close ->
        when (shown) {
            is MapSheet.StopPhotos -> PhotosRoute(
                owner = PhotoOwner.Stop(shown.stop.id),
                title = shown.stop.title.asString(),
                canAdd = shown.canEdit,
                canManage = shown.canEdit,
                coverPhotoId = shown.stop.coverPhotoId,
                onReport = { photo -> onAction(MapAction.Sheet.ReportPhoto(photo)) },
                onDismiss = close,
            )
            is MapSheet.PlacePhotos -> PhotosRoute(
                owner = PhotoOwner.Place(shown.place.id),
                title = shown.place.name,
                canAdd = true,
                canManage = false,
                coverPhotoId = null,
                onReport = { photo -> onAction(MapAction.Sheet.ReportPhoto(photo)) },
                onDismiss = close,
            )
            is MapSheet.StopComments -> StopCommentsRoute(marker = shown.stop, onOpenProfile = onOpenProfile, onDismiss = close)
            MapSheet.Members -> membersContent(tripId, close)
            is MapSheet.PlaceImage, MapSheet.PhotoCamera -> Unit
        }
    }
    when (sheet) {
        is MapSheet.PlaceImage -> PhotoViewerRoute(
            owner = PhotoOwner.Place(sheet.placeId),
            photoId = sheet.photoId,
            onReport = { photo -> onAction(MapAction.Sheet.ReportPhoto(photo)) },
            onDismiss = { onAction(MapAction.Sheet.ClosePlaceImage) },
        )
        MapSheet.PhotoCamera -> PhotoCameraDialog(
            onPhoto = { onAction(MapAction.StopCard.AddPhotos(listOf(it))) },
            onDismiss = { onAction(MapAction.Sheet.CloseCamera) },
        )
        else -> Unit
    }

    state.dialog?.let { dialog -> MapDialogs(dialog, onAction) }
}

private fun MapSheet.closeAction(): MapAction = when (this) {
    is MapSheet.StopPhotos -> MapAction.Sheet.CloseStopPhotos
    is MapSheet.PlacePhotos -> MapAction.Sheet.ClosePlacePhotos
    is MapSheet.StopComments -> MapAction.Sheet.CloseStopComments
    MapSheet.Members -> MapAction.Sheet.CloseMembers
    is MapSheet.PlaceImage -> MapAction.Sheet.ClosePlaceImage
    MapSheet.PhotoCamera -> MapAction.Sheet.CloseCamera
}

package com.falcon.tripingly.feature.map.presentation.screen

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
import com.falcon.tripingly.feature.map.presentation.photos.PhotoCameraDialog
import com.falcon.tripingly.feature.map.presentation.photos.PhotoViewerRoute
import com.falcon.tripingly.feature.map.presentation.photos.PhotosRoute
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch
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

    when (val sheet = state.sheet) {
        is MapSheet.StopPhotos -> PhotosRoute(
            owner = PhotoOwner.Stop(sheet.stop.id),
            title = sheet.stop.title.asString(),
            canAdd = sheet.canEdit,
            canManage = sheet.canEdit,
            coverPhotoId = sheet.stop.coverPhotoId,
            onReport = { photo -> onAction(MapAction.Sheet.ReportPhoto(photo)) },
            onDismiss = { onAction(MapAction.Sheet.CloseStopPhotos) },
        )
        is MapSheet.PlacePhotos -> PhotosRoute(
            owner = PhotoOwner.Place(sheet.place.id),
            title = sheet.place.name,
            canAdd = true,
            canManage = false,
            coverPhotoId = null,
            onReport = { photo -> onAction(MapAction.Sheet.ReportPhoto(photo)) },
            onDismiss = { onAction(MapAction.Sheet.ClosePlacePhotos) },
        )
        is MapSheet.StopComments -> StopCommentsRoute(
            marker = sheet.stop,
            onOpenProfile = onOpenProfile,
            onDismiss = { onAction(MapAction.Sheet.CloseStopComments) },
        )
        MapSheet.Members -> membersContent(tripId) { onAction(MapAction.Sheet.CloseJoinRequests) }
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
        null -> Unit
    }

    state.dialog?.let { dialog -> MapDialogs(dialog, onAction) }
}

package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_cancel
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_delete
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_remove
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_delete_others_content_title
import com.falcon.tripingly.feature.map.generated.resources.map_location_continue
import com.falcon.tripingly.feature.map.generated.resources.map_location_explain_message
import com.falcon.tripingly.feature.map.generated.resources.map_location_explain_title
import com.falcon.tripingly.feature.map.generated.resources.map_location_open_settings
import com.falcon.tripingly.feature.map.generated.resources.map_location_settings_message
import com.falcon.tripingly.feature.map.generated.resources.map_location_settings_title
import com.falcon.tripingly.feature.map.generated.resources.map_pin_add
import com.falcon.tripingly.feature.map.generated.resources.map_pin_name_label
import com.falcon.tripingly.feature.map.generated.resources.map_pin_name_title
import com.falcon.tripingly.feature.map.generated.resources.map_remove_my_content_message
import com.falcon.tripingly.feature.map.generated.resources.map_remove_my_content_title
import com.falcon.tripingly.feature.map.generated.resources.map_stop_title_format
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsRoute
import com.falcon.tripingly.feature.map.presentation.photos.PhotoCameraDialog
import com.falcon.tripingly.feature.map.presentation.photos.PhotoViewerRoute
import com.falcon.tripingly.feature.map.presentation.photos.PhotosRoute
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Confirm
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.LocationPrompt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MapRoute(
    tripId: String,
    onNavigateBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    /** The trip's members sheet (from the trips feature), where the owner answers requests to join. */
    membersContent: @Composable (tripId: String, onDismiss: () -> Unit) -> Unit,
    // Keyed by trip: the view model store is shared across screens, and the class alone would
    // hand every trip the first trip's view model.
    viewModel: MapViewModel = koinViewModel(key = "map:$tripId") { parametersOf(tripId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val locationPermission = rememberLocationPermissionController { permission ->
        viewModel.onAction(Action.OnPermissionResult(permission))
    }
    // On open and on every return to the app, e.g. from Settings.
    LifecycleResumeEffect(locationPermission) {
        viewModel.onAction(Action.OnPermissionChecked(locationPermission.status()))
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is Event.RequestPermission -> locationPermission.request()
                is Event.OpenAppSettings -> locationPermission.openSettings()
                is Event.NavigateBack -> onNavigateBack()
                is Event.OpenProfile -> onOpenProfile(event.username)
                is Event.AnimateCamera -> {
                    // Reactive
                }
            }
        }
    }

    MapScreen(
        state = state,
        onAction = viewModel::onAction
    )

    if (state.isMembersOpen) {
        membersContent(tripId) { viewModel.onAction(Action.OnCloseJoinRequests) }
    }

    state.photosMarker?.let { marker ->
        PhotosRoute(
            owner = PhotoOwner.Stop(marker.id),
            title = marker.title.ifBlank { stringResource(Res.string.map_stop_title_format, marker.orderNumber) },
            canAdd = state.canEdit,
            canManage = state.canEdit,
            coverPhotoId = marker.coverPhotoId,
            onReport = { photo -> viewModel.onAction(Action.OnReportPhoto(photo)) },
            onDismiss = { viewModel.onAction(Action.OnCloseStopPhotos) },
        )
    }

    state.photosPlace?.let { place ->
        PhotosRoute(
            owner = PhotoOwner.Place(place.id),
            title = place.name,
            canAdd = true,
            canManage = false,
            coverPhotoId = null,
            onReport = { photo -> viewModel.onAction(Action.OnReportPhoto(photo)) },
            onDismiss = { viewModel.onAction(Action.OnClosePlacePhotos) },
        )
    }

    state.commentsMarker?.let { marker ->
        StopCommentsRoute(
            marker = marker,
            onOpenProfile = onOpenProfile,
            onDismiss = { viewModel.onAction(Action.OnCloseStopComments) },
        )
    }

    // A card image opens in the place's photo viewer, so its heart is the same like as in the Photos sheet.
    val place = state.selectedPlaceDetails
    val placeImage = state.placeImageIndex?.let { place?.images?.getOrNull(it) }
    if (place != null && placeImage != null) {
        PhotoViewerRoute(
            owner = PhotoOwner.Place(place.id),
            photoId = placeImage.id,
            onReport = { photo -> viewModel.onAction(Action.OnReportPhoto(photo)) },
            onDismiss = { viewModel.onAction(Action.OnClosePlaceImage) },
        )
    }

    if (state.isReportOpen) {
        ReportDialog(
            onReport = { reason, details -> viewModel.onAction(Action.OnSendReport(reason, details)) },
            onDismiss = { viewModel.onAction(Action.OnDismissReport) },
            isSending = state.isReporting,
        )
    }

    if (state.isPhotoCameraOpen) {
        PhotoCameraDialog(
            onPhoto = { viewModel.onAction(Action.OnAddStopPhotos(listOf(it))) },
            onDismiss = { viewModel.onAction(Action.OnClosePhotoCamera) },
        )
    }

    state.locationPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissLocationPrompt) },
            title = {
                Text(
                    when (prompt) {
                        LocationPrompt.Explain -> stringResource(Res.string.map_location_explain_title)
                        LocationPrompt.OpenSettings -> stringResource(Res.string.map_location_settings_title)
                    },
                )
            },
            text = {
                Text(
                    when (prompt) {
                        LocationPrompt.Explain -> stringResource(Res.string.map_location_explain_message)
                        LocationPrompt.OpenSettings -> stringResource(Res.string.map_location_settings_message)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnConfirmLocationPrompt) }) {
                    Text(
                        when (prompt) {
                            LocationPrompt.Explain -> stringResource(Res.string.map_location_continue)
                            LocationPrompt.OpenSettings -> stringResource(Res.string.map_location_open_settings)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissLocationPrompt) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }

    state.confirm?.let { confirm ->
        val dayNumber = state.days.getOrNull(state.activeDayIndex)?.number ?: 1
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissConfirm) },
            title = {
                Text(
                    when (confirm) {
                        Confirm.ClearDay -> stringResource(Res.string.map_clear_day_title, dayNumber)
                        Confirm.DeleteDay -> stringResource(Res.string.map_delete_day_title, dayNumber)
                        Confirm.RemoveMyContent -> stringResource(Res.string.map_remove_my_content_title)
                    },
                )
            },
            text = {
                Text(
                    when (confirm) {
                        Confirm.ClearDay -> stringResource(Res.string.map_clear_day_message, state.markers.size)
                        Confirm.DeleteDay -> stringResource(Res.string.map_delete_day_message, state.markers.size)
                        Confirm.RemoveMyContent -> stringResource(Res.string.map_remove_my_content_message)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnConfirm) }) {
                    Text(
                        stringResource(if (confirm == Confirm.RemoveMyContent) Res.string.map_confirm_remove else Res.string.map_confirm_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissConfirm) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }

    state.othersContentMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissDeleteAnyway) },
            title = { Text(stringResource(Res.string.map_delete_others_content_title)) },
            text = { Text(message.asString()) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDeleteAnyway) }) {
                    Text(stringResource(Res.string.map_confirm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissDeleteAnyway) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }

    if (state.isNamingPin) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissPinName) },
            title = { Text(stringResource(Res.string.map_pin_name_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_PIN_NAME_LENGTH) },
                    label = { Text(stringResource(Res.string.map_pin_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onAction(Action.OnConfirmPinName(name)) },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(Res.string.map_pin_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissPinName) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }
}

/** The server's limit for a stop's name. */
private const val MAX_PIN_NAME_LENGTH = 120

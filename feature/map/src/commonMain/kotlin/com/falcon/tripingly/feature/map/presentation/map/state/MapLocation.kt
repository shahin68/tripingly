package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.error_location_services_disabled
import com.falcon.tripingly.feature.map.generated.resources.error_location_unavailable
import com.falcon.tripingly.feature.map.generated.resources.error_location_unexpected
import com.falcon.tripingly.feature.map.generated.resources.error_location_unknown
import com.falcon.tripingly.feature.map.presentation.map.LocationPrompt
import com.falcon.tripingly.feature.map.presentation.map.MapEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The user's location: its dot on the map and the location button. Only the button asks for the permission;
 * once granted, the dot shows without moving the camera, and only the button moves the camera to the user.
 * The button switches following on and off (Shahin, 2026-11-13): on, the camera goes to me and moves with every new
 * location, until a finger moves the map, the screen shows something else ([MapCamera.moveTo]) or the button is tapped
 * again. When no location comes for [SIGNAL_LOST_MILLIS] (a tunnel, underground) a quiet note says it's waiting, and
 * the first location after that carries on following.
 */
internal class MapLocation(
    private val context: MapContext,
    private val camera: MapCamera,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val sendEvent: (MapEvent) -> Unit,
) {
    /** The permission as read when the map opens or comes back to the front (e.g. from Settings). */
    fun onPermissionChecked(permission: LocationPermission) {
        val before = context.model.value.location.permission
        update { it.copy(permission = permission) }
        if (permission == LocationPermission.Granted && before != LocationPermission.Granted) startUpdates()
    }

    /** The user answered the system prompt. */
    fun onPermissionResult(permission: LocationPermission) {
        val before = context.model.value.location
        update { it.copy(permission = permission) }
        if (permission == LocationPermission.Granted) {
            if (before.permission != LocationPermission.Granted) startUpdates()
        } else if (permission == LocationPermission.Blocked && before.isWaitingForFirst) {
            // The location button asked, but the system can't show its prompt any more.
            context.update { it.copy(dialog = Dialog.Location(LocationPrompt.OpenSettings)) }
        } else {
            context.update {
                it.copy(
                    location = it.location.copy(isWaitingForFirst = false),
                    message = UiText.Resource(Res.string.error_location_denied_manual),
                )
            }
        }
    }

    /**
     * The location button: following on (the camera goes to me, asking for the permission first when it can), or off
     * when it's on.
     */
    fun centerOnUser() {
        val location = context.model.value.location
        if (location.isFollowing) return camera.stopFollowing()
        when (location.permission) {
            LocationPermission.Granted -> {
                update { it.copy(isFollowing = true, isSignalLost = false) }
                val current = location.current
                if (current != null) camera.follow(current, maxOf(camera.zoom, USER_ZOOM)) else update { it.copy(isWaitingForFirst = true) }
                watchSignal()
            }
            LocationPermission.NotAsked, null -> {
                update { it.copy(isWaitingForFirst = true) }
                sendEvent(MapEvent.RequestPermission)
            }
            LocationPermission.ShouldExplain -> prompt(LocationPrompt.Explain)
            LocationPermission.Blocked -> prompt(LocationPrompt.OpenSettings)
        }
    }

    /** Go here needs where I am: asks for the permission where it can, and the camera stays where it is. */
    fun askForRoute() {
        when (context.model.value.location.permission) {
            LocationPermission.NotAsked, null -> sendEvent(MapEvent.RequestPermission)
            LocationPermission.ShouldExplain -> context.update { it.copy(dialog = Dialog.Location(LocationPrompt.Explain)) }
            LocationPermission.Blocked -> context.update { it.copy(dialog = Dialog.Location(LocationPrompt.OpenSettings)) }
            // Still finding where I am: the note asks to try again in a moment.
            LocationPermission.Granted -> Unit
        }
    }

    fun confirmPrompt() {
        val prompt = (context.model.value.dialog as? Dialog.Location)?.prompt ?: return
        context.update { it.copy(dialog = null) }
        when (prompt) {
            LocationPrompt.Explain -> sendEvent(MapEvent.RequestPermission)
            LocationPrompt.OpenSettings -> sendEvent(MapEvent.OpenAppSettings)
        }
    }

    fun dismissPrompt() = context.update { it.copy(dialog = null, location = it.location.copy(isWaitingForFirst = false)) }

    private fun prompt(prompt: LocationPrompt) =
        context.update { it.copy(location = it.location.copy(isWaitingForFirst = true), dialog = Dialog.Location(prompt)) }

    private fun startUpdates() {
        context.scope.launch {
            context.update { it.copy(location = it.location.copy(isLoading = true), message = null) }
            try {
                getCurrentLocationUseCase().collect { result ->
                    when (result) {
                        is AppResult.Success -> {
                            val before = context.model.value.location
                            update { it.copy(current = result.data, isLoading = false, isWaitingForFirst = false, isSignalLost = false) }
                            when {
                                // The button asked for the permission first: following starts now.
                                before.isWaitingForFirst -> {
                                    update { it.copy(isFollowing = true) }
                                    camera.follow(result.data, maxOf(camera.zoom, USER_ZOOM))
                                }
                                before.isFollowing -> camera.follow(result.data, camera.zoom)
                            }
                            if (context.model.value.location.isFollowing) watchSignal()
                        }
                        is AppResult.Error -> {
                            val resource = when (result.error) {
                                is DataError.Location.PermissionDenied -> Res.string.error_location_permission_required
                                is DataError.Location.ServiceDisabled -> Res.string.error_location_services_disabled
                                is DataError.Location.Unavailable -> Res.string.error_location_unavailable
                                is DataError.Location.Unknown -> Res.string.error_location_unknown
                            }
                            context.update { it.copy(location = it.location.copy(isLoading = false), message = UiText.Resource(resource)) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.update { it.copy(location = it.location.copy(isLoading = false), message = UiText.Resource(Res.string.error_location_unexpected)) }
            }
        }
    }

    /** While following, no new location within [SIGNAL_LOST_MILLIS] says the signal is lost; each location starts it again. */
    private fun watchSignal() {
        signalWatch?.cancel()
        signalWatch = context.scope.launch {
            delay(SIGNAL_LOST_MILLIS)
            if (context.model.value.location.isFollowing) update { it.copy(isSignalLost = true) }
        }
    }

    private var signalWatch: Job? = null

    private fun update(change: (LocationModel) -> LocationModel) = context.update { it.copy(location = change(it.location)) }

    private companion object {
        /** Streets around the user. */
        const val USER_ZOOM = 15f
        /** Locations come every 2 to 3 s while the signal is there; this long without one, it's gone. */
        const val SIGNAL_LOST_MILLIS = 12_000L
    }
}

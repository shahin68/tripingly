package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.floatingBottom
import com.falcon.tripingly.core.designsystem.component.safeSides
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.fittedCamera
import com.falcon.tripingly.feature.map.presentation.component.isSplitScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where a map screen's panel (its header and cards) goes and what of the map it covers, shared by the trip map and the
 * spot map: the top and the bottom in portrait, the left half in landscape, like Google Maps (Shahin, 2026-11-14).
 * The camera centers and fits within the rest; a tapped pin goes a little below the middle of it ([pinLowering]).
 */
internal class MapPanelLayout(
    /** Landscape on a full screen: the panel takes the left half. */
    val isWide: Boolean,
    /** What the header and the cards cover of the map. */
    val covered: PaddingValues,
    /** The map's padding for the camera: [covered], with a tapped pin lowered by [pinLowering]. */
    val cameraPadding: PaddingValues,
    /** How far below the middle of the free map the camera puts a tapped pin, twice (the padding grows on one side only). */
    val pinLowering: Dp,
    /** The status bar and the header. */
    val coveredTop: Dp,
    /** The header and the cards: full width, or the left half in landscape; never under the camera cutout. */
    val panel: Modifier,
    private val width: Dp,
    private val height: Dp,
) {
    /**
     * The camera that frames [bounds] in the map that shows right now: above a sheet [pulledUp] that far, in the middle
     * of what's free.
     */
    fun fit(bounds: GeoBounds, pulledUp: Dp = 0.dp): Pair<Coordinates, Float> {
        val freeWidth = width - covered.calculateLeftPadding(LayoutDirection.Ltr) - covered.calculateRightPadding(LayoutDirection.Ltr)
        val freeHeight = height - covered.calculateTopPadding() - covered.calculateBottomPadding() - pulledUp
        return fittedCamera(bounds, freeWidth, freeHeight, raise = pulledUp / 2 + pinLowering / 2)
    }
}

/**
 * The map screen's [MapPanelLayout] for [headerHeight] (under the status bar) and the [coveredBottom] that always shows
 * in portrait. A rotation (or a split screen) changes the free part of the map: [onLayoutChange] then, so a fit still
 * showing is fitted again.
 */
@Composable
internal fun BoxWithConstraintsScope.rememberMapPanelLayout(headerHeight: Dp, coveredBottom: Dp, onLayoutChange: () -> Unit): MapPanelLayout {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val coveredTop = statusBar + headerHeight
    // Landscape only on a full screen: a split screen has no room for two halves and keeps the portrait layout.
    val isWide = maxWidth > maxHeight && !isSplitScreen()
    // Saved, since Android builds the screen anew on a rotation.
    var shownWide by rememberSaveable { mutableStateOf(isWide) }
    LaunchedEffect(isWide) {
        if (isWide != shownWide) {
            shownWide = isWide
            onLayoutChange()
        }
    }
    val sides = WindowInsets.safeSides.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val start = sides.calculateStartPadding(layoutDirection)
    val end = sides.calculateEndPadding(layoutDirection)
    val panelWidth = (maxWidth - start - end) / 2
    // In landscape the map's free part ends where the bottom card does, so its Google logo lines up with the card's
    // bottom (Shahin, 2026-11-14), and the map buttons float down to that line.
    val cardBottom = WindowInsets.floatingBottom.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.small
    val covered = if (isWide) {
        PaddingValues(start = start + panelWidth, top = statusBar, end = end, bottom = cardBottom)
    } else {
        PaddingValues(top = coveredTop, bottom = coveredBottom)
    }
    // Where the camera puts a tapped pin, with its card above it: a little below the middle of what's free, so the
    // card has more room (Shahin, 2026-11-14: "centring a pin" always means this), and in landscape low down, so
    // the card above the pin has the height it needs (Shahin, 2026-11-08). Only the camera's padding moves; fits
    // still use all that's free ([MapPanelLayout.fit] puts them back in its middle).
    val pinLowering = if (isWide) (maxHeight - LandscapePinArea - statusBar - cardBottom).coerceAtLeast(0.dp) else PortraitPinDrop * 2
    val cameraPadding = if (isWide) {
        PaddingValues(start = start + panelWidth, top = statusBar + pinLowering, end = end, bottom = cardBottom)
    } else {
        PaddingValues(top = coveredTop + pinLowering, bottom = coveredBottom)
    }
    val panel = Modifier.windowInsetsPadding(WindowInsets.safeSides).then(if (isWide) Modifier.width(panelWidth) else Modifier.fillMaxWidth())
    return MapPanelLayout(isWide, covered, cameraPadding, pinLowering, coveredTop, panel, maxWidth, maxHeight)
}

/**
 * The camera's commands for the map view, a fit worked out by [fit] once what changed with it (the route bar, the
 * route's card coming or growing) is laid out, so the fit keeps out of them as they are now, not as they were
 * (Shahin, 2026-11-15). A [CameraCommand.MoveAfterLayout] waits the same way.
 */
@Composable
internal fun rememberCameraMoves(cameraCommands: Flow<CameraCommand>, fit: () -> (GeoBounds) -> Pair<Coordinates, Float>): Flow<CameraCommand.Move> =
    remember(cameraCommands) {
        cameraCommands.map { command ->
            when (command) {
                is CameraCommand.Move -> command
                is CameraCommand.Fit -> {
                    repeat(FIT_LAYOUT_FRAMES) { withFrameNanos { } }
                    fit()(command.bounds).let { (target, zoom) -> CameraCommand.MoveTo(target, zoom, upright = true) }
                }
                is CameraCommand.MoveAfterLayout -> {
                    repeat(FIT_LAYOUT_FRAMES) { withFrameNanos { } }
                    CameraCommand.MoveTo(command.target, command.zoom)
                }
            }
        }
    }

/**
 * In landscape, the strip at the bottom of the free map whose middle a tapped pin goes to: 48 dp above the card's
 * bottom, so the stop card above it has more room (Shahin, 2026-11-14; 64 dp before).
 */
private val LandscapePinArea = 96.dp

/** In portrait, how far below the middle of the free map a tapped pin goes. */
private val PortraitPinDrop = 24.dp

/**
 * Frames a fit waits before it is worked out: the change that came with it is composed, laid out (the header's and
 * the sheet's new sizes measured) and composed again with those sizes.
 */
private const val FIT_LAYOUT_FRAMES = 3

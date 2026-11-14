package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The content padding of a screen under floating bars: below the [topBar] (its `Scaffold`'s padding), above the
 * [bottomBar] (the tab shell's padding; none on screens without it) plus [extraBottom] for a floating button,
 * with the standard side gutter beside [WindowInsets.Companion.safeSides]. Content scrolls behind the glass bars and
 * comes to rest clear of them, and in landscape it never goes under the camera cutout.
 */
@Composable
fun screenContentPadding(
    topBar: PaddingValues,
    bottomBar: PaddingValues = PaddingValues(),
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val spacing = MaterialTheme.spacing
    val sides = WindowInsets.safeSides.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = sides.calculateStartPadding(layoutDirection) + spacing.medium,
        end = sides.calculateEndPadding(layoutDirection) + spacing.medium,
        top = topBar.calculateTopPadding() + spacing.small,
        bottom = maxOf(bottomBar.calculateBottomPadding(), topBar.calculateBottomPadding()) + spacing.medium + extraBottom,
    )
}

/**
 * The screen's sides that content keeps clear of: the camera cutout and the system bars in landscape, only on the side
 * they're on (Shahin, 2026-11-08). Android reports them so; iOS pads both sides alike in landscape, so there only the
 * notch's side keeps its padding (Shahin, 2026-11-14). Use it for anything laid over an edge-to-edge screen: bars,
 * cards, buttons.
 */
expect val WindowInsets.Companion.safeSides: WindowInsets

/** The top of the screen (status bar, notch) plus [WindowInsets.Companion.safeSides]: for headers. */
val WindowInsets.Companion.safeTopAndSides: WindowInsets
    @Composable get() = safeDrawing.only(WindowInsetsSides.Top).union(safeSides)

/**
 * Where a floating bottom card (the Home bottom bar, the map's day tabs) stands off the bottom, with 8 dp of its own
 * padding on top: 8 dp over Android's navigation bar, and 16 dp from the edge where there's no bar (iOS), as far as
 * from the sides (Shahin, 2026-11-08).
 */
val WindowInsets.Companion.floatingBottom: WindowInsets
    @Composable get() = bottomSystemBar.union(WindowInsets(bottom = MaterialTheme.spacing.small))

/**
 * Android's navigation bar at the bottom (the gesture handle or the buttons), which bottom controls stay above. None on
 * iOS: there the home indicator sits over the app, and bottom controls keep only their own padding (Shahin, 2026-11-08).
 */
expect val WindowInsets.Companion.bottomSystemBar: WindowInsets

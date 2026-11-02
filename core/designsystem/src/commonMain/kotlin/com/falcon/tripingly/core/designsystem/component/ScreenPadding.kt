package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
    val side = WindowInsets.safeSides.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr) + spacing.medium
    return PaddingValues(
        start = side,
        end = side,
        top = topBar.calculateTopPadding() + spacing.small,
        bottom = maxOf(bottomBar.calculateBottomPadding(), topBar.calculateBottomPadding()) + spacing.medium + extraBottom,
    )
}

/**
 * The screen's sides that content keeps clear of: the camera cutout and system bars on the left or right in
 * landscape. Both sides get the larger of the two, so the content stays centred and its ends line up (on Android
 * the cutout is only on one side). Use it for anything laid over an edge-to-edge screen: bars, cards, buttons.
 */
val WindowInsets.Companion.safeSides: WindowInsets
    @Composable get() = SymmetricSides(safeDrawing)

/** The top of the screen (status bar, notch) plus [WindowInsets.Companion.safeSides]: for headers. */
val WindowInsets.Companion.safeTopAndSides: WindowInsets
    @Composable get() = safeDrawing.only(WindowInsetsSides.Top).union(safeSides)

/**
 * The bottom of the screen (navigation bar, home indicator) plus [WindowInsets.Companion.safeSides]: for bottom bars,
 * which stay put under the keyboard.
 */
val WindowInsets.Companion.safeBottomAndSides: WindowInsets
    @Composable get() = systemBars.union(displayCutout).only(WindowInsetsSides.Bottom).union(safeSides)

@Stable
private class SymmetricSides(private val insets: WindowInsets) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
        maxOf(insets.getLeft(density, layoutDirection), insets.getRight(density, layoutDirection))
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = getLeft(density, layoutDirection)
    override fun getTop(density: Density) = 0
    override fun getBottom(density: Density) = 0
    override fun equals(other: Any?) = other is SymmetricSides && other.insets == insets
    override fun hashCode() = insets.hashCode()
}

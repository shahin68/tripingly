package com.falcon.tripingly.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors Material's scheme has no role for: the glass tint and its edge (the map's own are [MapColors]). */
@Immutable
data class ExtendedColors(
    /** What glass surfaces are tinted with over the blurred content behind them. */
    val glass: Color,
    /** The thin light edge that sets glass apart from what's behind it. */
    val glassEdge: Color,
)

internal val LightExtendedColors = ExtendedColors(glass = GlassLight, glassEdge = GlassEdgeLight)
internal val DarkExtendedColors = ExtendedColors(glass = GlassDark, glassEdge = GlassEdgeDark)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

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
    /**
     * A stop's pin and the plan line joining a day's stops: the brand green, light on the dark map (Shahin's "Ivy
     * stops" on the graphite map, 2026-11-08).
     */
    val stop: Color,
    /** A stop's number and ring. */
    val onStop: Color,
    /** A place's pin (and a hot spot's) on the map: slate grey, softer on the dark map. */
    val place: Color,
    /** Something switched on that failed and can be tried again: orange, with [onAttention] on it. */
    val attention: Color,
    val onAttention: Color,
)

internal val LightExtendedColors = ExtendedColors(
    glass = GlassLight,
    glassEdge = GlassEdgeLight,
    stop = IvyLight,
    onStop = OnIvyLight,
    place = SecondaryLight,
    attention = AttentionLight,
    onAttention = OnAttentionLight,
)
internal val DarkExtendedColors = ExtendedColors(
    glass = GlassDark,
    glassEdge = GlassEdgeDark,
    stop = IvyDark,
    onStop = OnIvyDark,
    place = MapPlaceDark,
    attention = AttentionDark,
    onAttention = OnAttentionDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

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
    /** An icon on [stop] that keeps a green tint: white in light, the one deep green in dark (the drag handle's arrow). */
    val onStopIcon: Color,
    /** A place's pin (and a hot spot's) on the map: slate grey, softer on the dark map. */
    val place: Color,
    /** The fill of a tappable pill on a card (a toggle's track, the Leave at and legs buttons): stands out of the card in both themes. */
    val controlFill: Color,
    /** What is picked: a selected chip, the open stop in the plan, the lit filter circle; [onHighlight] on it. */
    val highlight: Color,
    val onHighlight: Color,
    /** Something switched on that failed and can be tried again: orange, with [onAttention] on it. */
    val attention: Color,
    val onAttention: Color,
    /**
     * An avatar without a photo, its letter in [onAvatar]: a quiet grey in light, a lighter grey in dark so faces stand out
     * on the dark map and glass (Shahin, 2026-11-17).
     */
    val avatar: Color,
    val onAvatar: Color,
    /**
     * The one invitation a screen pushes (`InviteButton`, Ask to join on someone's trip): [invite] (black in light, white
     * in dark), [onInvite] on it, edged in [inviteEdge] like glass (Shahin, 2026-11-19). A heart in it is split off by
     * [inviteDivider] and filled with [inviteLiked] once liked.
     */
    val invite: Color,
    val onInvite: Color,
    val inviteEdge: Color,
    val inviteDivider: Color,
    val inviteLiked: Color,
)

internal val LightExtendedColors = ExtendedColors(
    glass = GlassLight,
    glassEdge = GlassEdgeLight,
    stop = IvyLight,
    onStop = OnIvyLight,
    onStopIcon = OnIvyLight,
    place = SecondaryLight,
    controlFill = SurfaceContainerHighLight,
    highlight = HighlightLight,
    onHighlight = OnHighlightLight,
    attention = AttentionLight,
    onAttention = OnAttentionLight,
    avatar = SurfaceContainerHighestLight,
    onAvatar = OnSurfaceVariantLight,
    invite = InviteLight,
    onInvite = OnInviteLight,
    inviteEdge = InviteEdgeLight,
    inviteDivider = InviteDividerLight,
    inviteLiked = InviteLikedLight,
)
internal val DarkExtendedColors = ExtendedColors(
    glass = GlassDark,
    glassEdge = GlassEdgeDark,
    stop = IvyDark,
    onStop = OnIvyDark,
    onStopIcon = IvyDeepDark,
    place = MapPlaceDark,
    controlFill = ControlFillDark,
    highlight = HighlightDark,
    onHighlight = OnHighlightDark,
    attention = AttentionDark,
    onAttention = OnAttentionDark,
    avatar = AvatarDark,
    onAvatar = OnAvatarDark,
    invite = InviteDark,
    onInvite = OnInviteDark,
    inviteEdge = InviteEdgeDark,
    inviteDivider = InviteDividerDark,
    inviteLiked = InviteLikedDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

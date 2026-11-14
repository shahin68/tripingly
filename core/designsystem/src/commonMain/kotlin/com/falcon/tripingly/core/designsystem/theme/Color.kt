package com.falcon.tripingly.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Ivy: one calm dark leaf green as the brand, everything else neutral (redesign, 2026-10-26).

// Light
internal val IvyLight = Color(0xFF2E5E3E)
internal val OnIvyLight = Color(0xFFFFFFFF)
internal val IvyContainerLight = Color(0xFFDCE7DF)
internal val OnIvyContainerLight = Color(0xFF0F2A18)
internal val SecondaryLight = Color(0xFF4E5A52)
internal val SecondaryContainerLight = Color(0xFFE2E7E3)
internal val OnSecondaryContainerLight = Color(0xFF1B231E)
internal val BackgroundLight = Color(0xFFF1F2F0)
internal val OnBackgroundLight = Color(0xFF15171C)
internal val OnSurfaceVariantLight = Color(0xFF555B66)
internal val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val SurfaceContainerLowLight = Color(0xFFF7F8F6)
internal val SurfaceContainerLight = Color(0xFFEBECEA)
internal val SurfaceContainerHighLight = Color(0xFFE5E7E4)
internal val SurfaceContainerHighestLight = Color(0xFFDFE1DE)
internal val OutlineLight = Color(0xFF8A8F8B)
internal val OutlineVariantLight = Color(0xFFD5D8D5)
internal val ErrorLight = Color(0xFFC22A1E)
internal val ErrorContainerLight = Color(0xFFFCE3E0)
// Attention: something on that didn't work and can be tried again (the Route button's Retry), orange, not alarming red.
internal val AttentionLight = Color(0xFFC8661A)
internal val OnAttentionLight = Color(0xFFFFFFFF)
internal val OnErrorContainerLight = Color(0xFF5C0E08)

// Dark
internal val IvyDark = Color(0xFF86C79A)
internal val OnIvyDark = Color(0xFF062512)
internal val IvyContainerDark = Color(0xFF213A2A)
internal val OnIvyContainerDark = Color(0xFFCDEBD6)
internal val SecondaryDark = Color(0xFFB4C0B7)
internal val SecondaryContainerDark = Color(0xFF2A322D)
internal val OnSecondaryContainerDark = Color(0xFFD9E2DB)
internal val BackgroundDark = Color(0xFF0E100F)
internal val OnBackgroundDark = Color(0xFFEEF0EE)
internal val OnSurfaceVariantDark = Color(0xFFA9AFA9)
internal val SurfaceContainerLowestDark = Color(0xFF0A0B0B)
internal val SurfaceContainerLowDark = Color(0xFF161917)
internal val SurfaceContainerDark = Color(0xFF1B1E1C)
internal val SurfaceContainerHighDark = Color(0xFF232725)
internal val SurfaceContainerHighestDark = Color(0xFF2C302E)
internal val OutlineDark = Color(0xFF6F7570)
internal val OutlineVariantDark = Color(0xFF353A37)
internal val ErrorDark = Color(0xFFFFB4AB)
internal val ErrorContainerDark = Color(0xFF5C1A14)
internal val AttentionDark = Color(0xFFF2A65E)
internal val OnAttentionDark = Color(0xFF3A1E00)
internal val OnErrorContainerDark = Color(0xFFFFDAD5)

// A place's pin on the dark map: softer than the secondary grey, so the stops lead.
internal val MapPlaceDark = Color(0xFF8E9A92)

// Glass: what the floating bars, sheets and cards are tinted with over the blur.
internal val GlassLight = Color(0x9EFFFFFF)
internal val GlassDark = Color(0x99181A19)
internal val GlassEdgeLight = Color(0x99FFFFFF)
internal val GlassEdgeDark = Color(0x14FFFFFF)

/**
 * What the map draws the same in light and dark: the new dot and the routes. Stops, the plan line and places
 * follow the theme ([ExtendedColors.stop], [ExtendedColors.place]).
 */
object MapColors {
    /** The dot on a stop (and its day tab) with something I haven't seen: the accent red, in a white ring on pins. */
    val NewActivity = Color(0xFFE53935)
    /** Routes: one sharp jewel colour per way of travelling (emerald, amber, sapphire), plain solid lines. */
    val Walk = Color(0xFF1E9E6A)
    val Bike = Color(0xFFE07B1F)
    val Car = Color(0xFF3355CC)
}

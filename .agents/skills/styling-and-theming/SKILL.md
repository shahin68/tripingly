---
name: styling-and-theming
description: >-
  Use this skill when designing, building, or modifying the design system, styling,
  and theming foundation in Compose Multiplatform. Covers Material 3 tokens, colors,
  typography, spacing, shapes, and dark/light mode support.
---

# Compose Multiplatform Styling & Theming Foundation

This guide establishes the UI styling and theming system for `tripingly`.

---

## 1. Design System Structure

Organize theming under `core/presentation/theme/`:

```
core/presentation/theme/
├── Color.kt             # Raw color palette & brand colors
├── Spacing.kt           # Centralized spacing & padding tokens
├── Typography.kt        # Material 3 typography definitions
├── Shape.kt             # Material 3 component shapes
└── Theme.kt             # Main AppTheme composable & CompositionLocals
```

---

## 2. Spacing Tokens (`Spacing.kt`)

Avoid hardcoded dp values in screens. Define standard spacing tokens:

```kotlin
package com.falcon.tripingly.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val extraExtraLarge: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
```

---

## 3. Brand Colors & Palette (`Color.kt`)

```kotlin
package com.falcon.tripingly.core.presentation.theme

import androidx.compose.ui.graphics.Color

// Primary Brand: Adventure Cyan / Blue
val PrimaryLight = Color(0xFF00668B)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFC3E7FF)
val OnPrimaryContainerLight = Color(0xFF001D2D)

val PrimaryDark = Color(0xFF7BD0FF)
val OnPrimaryDark = Color(0xFF00344A)
val PrimaryContainerDark = Color(0xFF004C6A)
val OnPrimaryContainerDark = Color(0xFFC3E7FF)

// Neutral & Backgrounds
val SurfaceLight = Color(0xFFFBF9F9)
val OnSurfaceLight = Color(0xFF191C1D)
val SurfaceDark = Color(0xFF111415)
val OnSurfaceDark = Color(0xFFE1E3E4)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
```

---

## 4. Typography (`Typography.kt`)

Use Material 3 typography hierarchy:

```kotlin
package com.falcon.tripingly.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

## 5. Main Theme Provider (`Theme.kt`)

```kotlin
package com.falcon.tripingly.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    error = ErrorLight,
    onError = OnErrorLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorDark,
    onError = OnErrorDark
)

@Composable
fun TripinglyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
```

---

## 6. Styling Rules Checklist
1. **Never hardcode hex values directly in screens**: Always use `MaterialTheme.colorScheme.*`.
2. **Never hardcode raw margin/padding numbers**: Use `MaterialTheme.spacing.medium` (or equivalent standard dp values).
3. **Use TextStyles from `MaterialTheme.typography`**: Ensure proper scaling with system font accessibility settings.
4. **Previews**: Always wrap `@Preview` composables with `TripinglyTheme`.

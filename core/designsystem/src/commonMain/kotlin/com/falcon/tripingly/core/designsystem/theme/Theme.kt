package com.falcon.tripingly.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.falcon.tripingly.core.designsystem.component.LocalGlass
import com.falcon.tripingly.core.designsystem.component.LocalMessageBanner
import com.falcon.tripingly.core.designsystem.component.rememberMessageBanner
import dev.chrisbanes.haze.rememberHazeState

private val LightColorScheme = lightColorScheme(
    primary = IvyLight,
    onPrimary = OnIvyLight,
    primaryContainer = IvyContainerLight,
    onPrimaryContainer = OnIvyContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnIvyLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = SecondaryLight,
    onTertiary = OnIvyLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceContainerHighLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceBright = SurfaceContainerLowestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnIvyLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = IvyDark,
    onPrimary = OnIvyDark,
    primaryContainer = IvyContainerDark,
    onPrimaryContainer = OnIvyContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnIvyDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = SecondaryDark,
    onTertiary = OnIvyDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceContainerHighDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceBright = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorContainerLight,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

@Composable
fun TripinglyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
        // One backdrop for the whole app: content marked as a glass source shows blurred behind glass surfaces.
        LocalGlass provides rememberHazeState(),
        LocalMessageBanner provides rememberMessageBanner(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

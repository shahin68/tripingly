package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/** The app's one backdrop, provided by `TripinglyTheme`: what [glassSource] marks shows blurred behind [GlassSurface]s. */
internal val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

/**
 * Marks content (a list, a screen's background) that glass surfaces drawn over it blur. The floating bars
 * and sheets sit outside it, on top. A native view such as the map can't be blurred; glass over it is tinted.
 */
@Composable
fun Modifier.glassSource(): Modifier = LocalGlass.current?.let { then(Modifier.hazeSource(it)) } ?: this

/**
 * Frosted glass: the content behind it blurred and tinted, with a thin light edge and a soft shadow. Used for
 * everything that floats: the top and bottom bars, map cards and sheets. Where blur isn't available (Android
 * before 12), or with [blur] off for glass over the native map, which can't be blurred, the tint is nearly
 * opaque instead, so text stays readable.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: (() -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shadowElevation: Dp = GlassDefaults.ShadowElevation,
    blur: Boolean = true,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlass.current?.takeIf { blur }
    val colors = MaterialTheme.extendedColors
    val tint = if (glass != null && HazeBlurDefaults.isBlurEnabledByDefault()) colors.glass else colors.glass.copy(alpha = OPAQUE_TINT)
    Box(
        modifier = modifier
            .shadow(shadowElevation, shape, ambientColor = GlassDefaults.Shadow, spotColor = GlassDefaults.Shadow)
            .clip(shape)
            .then(if (glass != null) Modifier.hazeBlur(HazeInput.Sources(glass), GlassDefaults.Blur) else Modifier)
            .background(tint)
            .border(GlassDefaults.Edge, colors.glassEdge, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

object GlassDefaults {
    val ShadowElevation = 8.dp
    internal val Shadow = Color.Black.copy(alpha = 0.18f)
    internal val Edge = 1.dp
    internal val Blur = HazeBlurStyle { blurRadius(24.dp) }
}

private const val OPAQUE_TINT = 0.94f

@Composable
private fun GlassPreviewContent() {
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
        LazyColumn(Modifier.glassSource(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            items(6) {
                Box(Modifier.fillMaxWidth().size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
        GlassSurface(Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.large)) {
            Column(Modifier.padding(MaterialTheme.spacing.medium)) {
                Text("Glass", style = MaterialTheme.typography.titleMedium)
                Text("Blurs what scrolls behind it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview
@Composable
private fun GlassSurfacePreview() {
    TripinglyTheme { GlassPreviewContent() }
}

@Preview
@Composable
private fun GlassSurfaceDarkPreview() {
    TripinglyTheme(darkTheme = true) { GlassPreviewContent() }
}

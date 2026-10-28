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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The app's backdrop, provided by `TripinglyTheme`: what [glassSource] draws, recorded into one graphics layer that
 * [GlassSurface]s over it draw blurred with Compose's own blur.
 */
@Stable
internal class GlassBackdrop(val layer: GraphicsLayer) {
    /** Where the source is on screen, to line its picture up behind each glass surface. */
    var origin by mutableStateOf(Offset.Zero)

    /** Bumped each time the source draws, so the glass over it redraws with it (e.g. while a list scrolls). */
    var version by mutableIntStateOf(0)

    /** Sources on screen now. Without one (the map screen) glass has nothing to blur and is tinted instead. */
    var sources by mutableIntStateOf(0)
}

internal val LocalGlass = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Whether Compose can blur on this device (Android 12 and later, iOS). */
internal expect val isBlurSupported: Boolean

/**
 * Marks a screen's content (the tabs' pages, a pushed screen's scrolling column) that glass surfaces over it show
 * blurred. The floating bars sit outside it, on top. A native view such as the map can't be recorded, so glass over
 * the map is tinted instead.
 */
@Composable
fun Modifier.glassSource(): Modifier {
    val backdrop = LocalGlass.current ?: return this
    DisposableEffect(backdrop) {
        backdrop.sources++
        onDispose { backdrop.sources-- }
    }
    return onGloballyPositioned { backdrop.origin = it.positionOnScreen() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
            Snapshot.withoutReadObservation { backdrop.version++ }
        }
}

/**
 * Frosted glass: the content behind it blurred and tinted, with a thin light edge and a soft shadow. Used for
 * everything that floats: the top and bottom bars, map cards and sheets. Where there's nothing to blur (over the
 * native map) or blur isn't available (Android before 12), the tint is nearly opaque instead, so text stays readable.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: (() -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shadowElevation: Dp = GlassDefaults.ShadowElevation,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalGlass.current?.takeIf { it.sources > 0 && isBlurSupported }
    val colors = MaterialTheme.extendedColors
    Box(
        modifier = modifier
            .shadow(shadowElevation, shape, ambientColor = GlassDefaults.Shadow, spotColor = GlassDefaults.Shadow)
            .clip(shape)
            .then(if (backdrop != null) Modifier.blurredBackdrop(backdrop) else Modifier)
            .background(if (backdrop != null) colors.glass else colors.glass.copy(alpha = OPAQUE_TINT))
            .border(GlassDefaults.Edge, colors.glassEdge, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** Draws the part of [backdrop] behind this element, blurred, on the screen's background colour. */
@Composable
private fun Modifier.blurredBackdrop(backdrop: GlassBackdrop): Modifier {
    val layer = rememberGraphicsLayer()
    val background = MaterialTheme.colorScheme.background
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionOnScreen() }
        .drawBehind {
            backdrop.version // Redraws whenever the source does.
            val radius = GlassDefaults.BlurRadius.toPx()
            layer.renderEffect = BlurEffect(radius, radius)
            layer.record {
                drawRect(background)
                translate(backdrop.origin.x - origin.x, backdrop.origin.y - origin.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(layer)
        }
}

object GlassDefaults {
    val ShadowElevation = 8.dp
    internal val Shadow = Color.Black.copy(alpha = 0.18f)
    internal val Edge = 1.dp
    internal val BlurRadius = 24.dp
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

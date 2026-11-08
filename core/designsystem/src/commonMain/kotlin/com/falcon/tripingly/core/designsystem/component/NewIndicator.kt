package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The dot that says something new waits: on a stop's pin and its day tab, the members strip, the Profile tab and the
 * bell. A ring of [strokeColor], the colour of what lies behind the dot, goes around it, so the dot looks cut into
 * what it sits on (Shahin, 2026-11-08). [size] is the dot's own; the ring adds [strokeWidth] on every side, and a
 * zero [strokeWidth] draws no ring, for a dot with nothing to cut through.
 */
@Composable
fun NewIndicator(
    modifier: Modifier = Modifier,
    size: Dp = NewIndicatorDefaults.Size,
    color: Color = NewIndicatorDefaults.Color,
    strokeColor: Color = MaterialTheme.colorScheme.surface,
    strokeWidth: Dp = NewIndicatorDefaults.StrokeWidth,
) {
    Spacer(
        modifier
            .size(size + strokeWidth * 2)
            .drawBehind {
                if (strokeWidth > 0.dp) drawCircle(strokeColor)
                drawCircle(color, radius = size.toPx() / 2)
            },
    )
}

/**
 * Moves a [NewIndicator] aligned to its parent's top end so its centre sits on that corner's curve of [cornerRadius]:
 * cut into the edge of a pill, a strip or a round button. [inset]: how far the visible shape lies inside the parent's
 * box (a 40 dp icon button in its 48 dp touch box); [indicatorSize]: the whole dot, ring included.
 */
fun Modifier.onTopEndCorner(
    cornerRadius: Dp,
    inset: Dp = 0.dp,
    indicatorSize: Dp = NewIndicatorDefaults.Size + NewIndicatorDefaults.StrokeWidth * 2,
): Modifier {
    val shift = indicatorSize / 2 - inset - cornerRadius * (1 - CORNER_CURVE_POINT)
    return offset(x = shift, y = -shift)
}

/** How far round a corner's curve its middle point lies from the corner's edges: cos 45°. */
private const val CORNER_CURVE_POINT = 0.7071f

object NewIndicatorDefaults {
    val Size = 10.dp
    val StrokeWidth = 2.dp
    /** The accent red, the only red in the app besides errors. */
    val Color = MapColors.NewActivity
}

@Preview
@Composable
private fun NewIndicatorPreview() = TripinglyTheme { NewIndicatorSamples() }

@Preview
@Composable
private fun NewIndicatorDarkPreview() = TripinglyTheme(darkTheme = true) { NewIndicatorSamples() }

@Composable
private fun NewIndicatorSamples() {
    Row(Modifier.background(Color.Gray).padding(MaterialTheme.spacing.medium)) {
        Box(Modifier.padding(end = MaterialTheme.spacing.medium)) { NewIndicator(strokeColor = Color.Gray) }
        Box(Modifier.padding(end = MaterialTheme.spacing.medium)) { NewIndicator(size = 6.dp, strokeColor = Color.Gray) }
        NewIndicator(strokeWidth = 0.dp)
    }
}

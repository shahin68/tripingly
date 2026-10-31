package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An icon that pops once with a ring of dots flying out (Instagram-style) each time [burst] goes up: the like and
 * hot buttons raise it when I tap them on, never when the state changes elsewhere. One-shot, nothing loops.
 */
@Composable
internal fun BurstIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    burst: Int,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(1f) }
    val dots = remember { Animatable(1f) }
    LaunchedEffect(burst) {
        if (burst == 0) return@LaunchedEffect
        coroutineScope {
            launch {
                pop.snapTo(0.6f)
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
            launch {
                dots.snapTo(0f)
                dots.animateTo(1f, tween(BurstMillis, easing = FastOutSlowInEasing))
            }
        }
    }
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .drawBehind {
                val progress = dots.value
                if (progress >= 1f) return@drawBehind
                val start = size.minDimension * 0.45f
                val distance = start + size.minDimension * 0.55f * progress
                val radius = size.minDimension * 0.09f * (1f - progress)
                repeat(DotCount) { i ->
                    val angle = 2 * PI * i / DotCount - PI / 2
                    drawCircle(
                        color = tint.copy(alpha = tint.alpha * (1f - progress)),
                        radius = radius,
                        center = center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat()),
                    )
                }
            }
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    )
}

private const val DotCount = 8
private const val BurstMillis = 450

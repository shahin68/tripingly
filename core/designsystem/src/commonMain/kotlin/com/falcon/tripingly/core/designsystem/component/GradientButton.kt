package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The one invitation a screen pushes (Ask to join on someone's trip): a rounded pill in the brand's green warming into
 * pink and orange, with a thin glassy edge, as high as the map's glass buttons ([GlassButtonDefaults.Size]). Its icon
 * sits as far from the start as from the top and bottom; the label ends as far from the end (Shahin, 2026-11-19).
 * A new [text] and [icon] (asked: "Request sent") play in: the icon pops in with a little spin, the label rises, and the
 * pill's width glides to fit. Without [onClick] it only shows its state.
 */
@Composable
fun GradientButton(text: String, icon: ImageVector, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.extendedColors
    val inset = (GlassButtonDefaults.Size - IconSize) / 2
    val edge = colors.gradientEdge
    Row(
        modifier = modifier
            .height(GlassButtonDefaults.Size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.gradientStart, colors.gradientMiddle, colors.gradientEnd)))
            .border(EdgeWidth, Brush.verticalGradient(listOf(edge, edge.copy(alpha = edge.alpha * EDGE_FADE))), CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(start = inset, end = inset + MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onGradient) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn()) togetherWith
                        (scaleOut() + fadeOut()) using SizeTransform(clip = false)
                },
                label = "gradient button icon",
            ) { shown ->
                val spin = remember { Animatable(if (shown == icon && transition.isRunning) -SPIN_DEGREES else 0f) }
                LaunchedEffect(Unit) { spin.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                Icon(shown, contentDescription = null, modifier = Modifier.size(IconSize).graphicsLayer { rotationZ = spin.value })
            }
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using SizeTransform(clip = false)
                },
                label = "gradient button label",
            ) { shown ->
                Text(shown, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

private val IconSize = 20.dp

/** A hairline, as glass's edge. */
private val EdgeWidth = 0.75.dp

/** How much of the edge's light is left at the bottom. */
private const val EDGE_FADE = 0.3f

/** How far a new icon turns as it pops in. */
private const val SPIN_DEGREES = 90f

@Composable
private fun GradientButtonPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        GradientButton(text = "Ask to join", icon = Icons.Outlined.PersonAdd, onClick = {})
        GradientButton(text = "Request sent", icon = Icons.Outlined.HowToReg, onClick = null)
    }
}

@Preview
@Composable
private fun GradientButtonPreview() {
    TripinglyTheme { GradientButtonPreviewContent() }
}

@Preview
@Composable
private fun GradientButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { GradientButtonPreviewContent() }
}

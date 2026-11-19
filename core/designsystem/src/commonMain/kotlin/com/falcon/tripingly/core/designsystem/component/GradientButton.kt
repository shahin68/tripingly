package com.falcon.tripingly.core.designsystem.component

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
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The one invitation a screen pushes (Ask to join on someone's trip): a rounded pill in the brand's green gradient with
 * a thin glassy edge, as high as the map's glass buttons ([GlassButtonDefaults.Size]). Its icon sits as far from the
 * start as from the top and bottom; the label ends as far from the end (Shahin, 2026-11-19).
 */
@Composable
fun GradientButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.extendedColors
    val inset = (GlassButtonDefaults.Size - IconSize) / 2
    Row(
        modifier = modifier
            .height(GlassButtonDefaults.Size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.gradientStart, colors.gradientEnd)))
            .border(EdgeWidth, Brush.verticalGradient(listOf(colors.gradientEdge, colors.gradientEdge.copy(alpha = colors.gradientEdge.alpha * EDGE_FADE))), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = inset, end = inset + MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onGradient) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private val IconSize = 20.dp

/** A hairline, as glass's edge. */
private val EdgeWidth = 0.75.dp

/** How much of the edge's light is left at the bottom. */
private const val EDGE_FADE = 0.3f

@Composable
private fun GradientButtonPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        GradientButton(text = "Ask to join", icon = Icons.Outlined.PersonAdd, onClick = {})
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

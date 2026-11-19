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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.like
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The one invitation a screen pushes (Ask to join on someone's trip): a rounded pill, black in light and white in dark
 * (Shahin, 2026-11-19), with a thin glassy edge, as high as the map's glass buttons ([GlassButtonDefaults.Size]). Its icon
 * sits as far from the start as from the top and bottom; the label ends as far from the end (Shahin, 2026-11-19).
 * A new [text] and [icon] (asked: "Request sent") play in: the icon pops in with a little spin, the label rises, and the
 * pill's width glides to fit. Without [onClick] it only shows its state. [isLoading]: a small spinner takes the icon's
 * place while the server answers; the icon that comes after pops in as above.
 * [liked] not null: a heart leads the pill, split off by a hairline (Shahin, 2026-11-19), with no count: outlined, or
 * filled red once liked; tapping it plays the like button's pop and dots and calls [onToggleLike]. It comes and goes
 * with the pill.
 */
@Composable
fun InviteButton(
    text: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    liked: Boolean? = null,
    onToggleLike: () -> Unit = {},
) {
    val colors = MaterialTheme.extendedColors
    val inset = (GlassButtonDefaults.Size - IconSize) / 2
    val edge = colors.inviteEdge
    Row(
        modifier = modifier
            .height(GlassButtonDefaults.Size)
            .clip(CircleShape)
            .background(colors.invite)
            .border(EdgeWidth, Brush.verticalGradient(listOf(edge, edge.copy(alpha = edge.alpha * EDGE_FADE))), CircleShape)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onInvite) {
            if (liked != null) {
                InviteHeart(liked, onToggleLike)
                Box(Modifier.size(width = DividerWidth, height = DividerHeight).background(colors.inviteDivider))
            }
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                    // After the heart, as far from the line as the heart is.
                    .padding(start = if (liked != null) HeartInset else inset, end = inset + MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = icon.takeUnless { isLoading },
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn()) togetherWith
                            (scaleOut() + fadeOut()) using SizeTransform(clip = false)
                    },
                    label = "invite button icon",
                ) { shown ->
                    if (shown == null) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize), color = LocalContentColor.current, strokeWidth = SpinnerWidth)
                    } else {
                        val spin = remember { Animatable(if (transition.isRunning) -SPIN_DEGREES else 0f) }
                        LaunchedEffect(Unit) { spin.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                        Icon(shown, contentDescription = null, modifier = Modifier.size(IconSize).graphicsLayer { rotationZ = spin.value })
                    }
                }
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using SizeTransform(clip = false)
                    },
                    label = "invite button label",
                ) { shown ->
                    Text(shown, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** The pill's heart: a square as high as the pill, the heart in its middle, as far from every side. */
@Composable
private fun InviteHeart(liked: Boolean, onToggle: () -> Unit) {
    var burst by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(GlassButtonDefaults.Size)
            .toggleable(
                value = liked,
                role = Role.Checkbox,
                onValueChange = { like ->
                    if (like) burst++
                    haptics.toggle(like)
                    onToggle()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BurstIcon(
            icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(Res.string.like),
            tint = if (liked) MaterialTheme.extendedColors.inviteLiked else LocalContentColor.current,
            burst = burst,
            modifier = Modifier.size(HeartSize),
        )
    }
}

private val HeartSize = 24.dp

/** The heart's gap to the pill's start, top, bottom and the line. */
private val HeartInset = (GlassButtonDefaults.Size - HeartSize) / 2

/** The line between the heart and the invitation. */
private val DividerWidth = 1.dp
private val DividerHeight = 24.dp

private val IconSize = 20.dp

/** The spinner's ring, thin enough for an icon's place. */
private val SpinnerWidth = 2.dp

/** A hairline, as glass's edge. */
private val EdgeWidth = 0.75.dp

/** How much of the edge's light is left at the bottom. */
private const val EDGE_FADE = 0.3f

/** How far a new icon turns as it pops in. */
private const val SPIN_DEGREES = 90f

@Composable
private fun InviteButtonPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        InviteButton(text = "Ask to join", icon = Icons.Outlined.PersonAdd, onClick = {})
        InviteButton(text = "Ask to join", icon = Icons.Outlined.PersonAdd, onClick = null, isLoading = true)
        InviteButton(text = "Request sent", icon = Icons.Outlined.HowToReg, onClick = null)
        InviteButton(text = "Ask to join", icon = Icons.Outlined.PersonAdd, onClick = {}, liked = false)
        InviteButton(text = "Request sent", icon = Icons.Outlined.HowToReg, onClick = null, liked = true)
    }
}

@Preview
@Composable
private fun InviteButtonPreview() {
    TripinglyTheme { InviteButtonPreviewContent() }
}

@Preview
@Composable
private fun InviteButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { InviteButtonPreviewContent() }
}

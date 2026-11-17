package com.falcon.tripingly.core.designsystem.component

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * How big a button is: [Large] for a screen's main action at the bottom, [Medium] inline (retry, follow), [Small]
 * in a card's or sheet's row (Route, Best order, Add photos).
 */
enum class ButtonSize(internal val height: Dp, internal val padding: PaddingValues) {
    Large(56.dp, PaddingValues(horizontal = 24.dp)),
    Medium(48.dp, PaddingValues(horizontal = 24.dp)),
    Small(40.dp, PaddingValues(horizontal = 16.dp)),
}

/**
 * The screen's main action ("Create trip"): an Ivy-filled pill. While [isLoading] it shows a spinner and
 * can't be tapped.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.Large,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        contentPadding = size.padding,
        modifier = modifier.heightIn(min = size.height).skeleton(CircleShape),
    ) {
        ButtonContent(text, isLoading, size, icon)
    }
}

/** A quieter action next to or below a [PrimaryButton] ("Not now", "Edit profile"): a neutral filled pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.Large,
    icon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        contentPadding = size.padding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.heightIn(min = size.height).skeleton(CircleShape),
    ) {
        ButtonContent(text, isLoading, size, icon)
    }
}

/**
 * A button that switches something on and off (the plan's Route): neutral like [SecondaryButton] while off, filled
 * with the brand colour like [PrimaryButton] while on. Its [icon] stays the same either way; the colour says it's on.
 * [isFailed]: on, but what it shows couldn't be had; orange, and the caller gives it a "Retry" [text] and tap.
 * [tint]: the button stands for something else for now (the plan's Route turning into Around's ✕): that colour's glass
 * look (a light shade of it, a thin stroke and the icon in it), no on/off. Without [text] it is a circle with its
 * [icon] (the caller gives it a content description). Colours, stroke and content glide from one look to the next,
 * the width easing with them, so the button turns into the other one; callers keep ONE call site for all its looks,
 * since a second call is another button that can't morph.
 */
@Composable
fun ToggleButton(
    text: String?,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Small,
    icon: ImageVector? = null,
    isFailed: Boolean = false,
    tint: Color? = null,
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val isRetry = checked && isFailed && tint == null
    val isToggle = !isRetry && tint == null
    val primary = ButtonDefaults.buttonColors()
    val container = when {
        tint != null -> tint.copy(alpha = TINT_SHADE).compositeOver(colors.surface)
        isRetry -> MaterialTheme.extendedColors.attention
        checked -> primary.containerColor
        else -> colors.surfaceVariant
    }
    val content = when {
        tint != null -> tint
        isRetry -> MaterialTheme.extendedColors.onAttention
        checked -> primary.contentColor
        else -> colors.onSurface
    }
    val look = tween<Color>(MORPH_MILLIS)
    val containerColor = animateColorAsState(container, look, label = "toggle container")
    val contentColor = animateColorAsState(content, look, label = "toggle content")
    // The stroke is always there, clear unless tinted, so it fades in and out instead of changing the button's size.
    val strokeColor = animateColorAsState(tint ?: container.copy(alpha = 0f), look, label = "toggle stroke")
    // One button that only changes colour, so switching doesn't replace it (and its ripple) with another.
    Button(
        onClick = {
            if (isToggle) haptics.toggle(!checked)
            onClick()
        },
        shape = CircleShape,
        // The padding is part of the content below, so it glides with the width.
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(StrokeWidth, strokeColor.value),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor.value, contentColor = contentColor.value),
        modifier = modifier
            .then(if (isToggle) Modifier.semantics { toggleableState = ToggleableState(checked) } else Modifier)
            // As small as a circle: Material's own minimum width is wider than a 40 dp circle.
            .widthIn(min = size.height)
            .heightIn(min = size.height)
            .skeleton(CircleShape),
    ) {
        // Only a new text or icon swaps the content; the colour follows on its own above.
        AnimatedContent(
            targetState = text to icon,
            transitionSpec = { fadeIn(tween(MORPH_MILLIS)) togetherWith fadeOut(tween(MORPH_MILLIS / 2)) using SizeTransform(clip = false) { _, _ -> tween(MORPH_MILLIS) } },
            contentAlignment = Alignment.Center,
            label = "toggle content",
        ) { (shownText, shownIcon) ->
            if (shownText != null) {
                Row(Modifier.padding(size.padding), verticalAlignment = Alignment.CenterVertically) { ButtonContent(shownText, isLoading = false, size, shownIcon) }
            } else {
                Box(Modifier.size(size.height), contentAlignment = Alignment.Center) {
                    shownIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(CircleIconSize)) }
                }
            }
        }
    }
}

@Composable
private fun ButtonContent(text: String, isLoading: Boolean, size: ButtonSize, icon: ImageVector?) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(SpinnerSize),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
    } else {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize))
            Spacer(Modifier.width(MaterialTheme.spacing.small))
        }
        Text(
            text = text,
            style = if (size == ButtonSize.Large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MORPH_MILLIS = 250
private const val TINT_SHADE = 0.18f
private val StrokeWidth = 1.dp
private val CircleIconSize = 20.dp
private val SpinnerSize = 22.dp
private val IconSize = 18.dp

@Composable
private fun ButtonsPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth())
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth(), enabled = false)
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth(), isLoading = true)
        SecondaryButton("Not now", {}, Modifier.fillMaxWidth())
        PrimaryButton("Try again", {}, size = ButtonSize.Medium)
        SecondaryButton("Edit profile", {}, size = ButtonSize.Medium)
        PrimaryButton("Add photos", {}, size = ButtonSize.Small, icon = Icons.Outlined.AddPhotoAlternate)
        SecondaryButton("Best order", {}, size = ButtonSize.Small, icon = Icons.Outlined.AutoAwesome)
        ToggleButton("Route", checked = false, onClick = {}, icon = Icons.Outlined.AutoAwesome)
        ToggleButton("Route", checked = true, onClick = {}, icon = Icons.Outlined.AutoAwesome)
        ToggleButton("Retry", checked = true, onClick = {}, icon = Icons.Outlined.Refresh, isFailed = true)
        ToggleButton(null, checked = false, onClick = {}, icon = Icons.Default.Close, tint = Color(0xFF8A5AAB))
    }
}

@Preview
@Composable
private fun PrimaryButtonPreview() {
    TripinglyTheme { ButtonsPreviewContent() }
}

@Preview
@Composable
private fun PrimaryButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { ButtonsPreviewContent() }
}

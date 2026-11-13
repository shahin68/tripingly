package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.mark_hot
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing

/*
 * Buttons joined into one group, the way Material 3's connected button groups look: a few px apart, the group's two
 * outer ends round, the corners between them small. Used for the pin cards' rows and the sheet headers' actions.
 */

/** One button of a [JoinedButtons] group, drawn with the shape its place in the group gives it. */
typealias JoinedButtonSlot = @Composable (shape: Shape) -> Unit

/** [buttons] side by side, each drawn with the shape its place in the group gives it ([joinedShape]). */
@Composable
fun JoinedButtons(buttons: List<JoinedButtonSlot>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(JoinedButtonDefaults.Gap), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEachIndexed { index, button -> button(joinedShape(index, buttons.size)) }
    }
}

/** The shape of the [index]th of [count] joined buttons: round outer ends, small inner corners. */
fun joinedShape(index: Int, count: Int): Shape {
    val outer = JoinedButtonDefaults.Height / 2
    val inner = JoinedButtonDefaults.InnerCorner
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        bottomStart = if (index == 0) outer else inner,
        topEnd = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/**
 * One button of a [JoinedButtons] group: an [icon], with a [count] after it where it has one; as wide as its content,
 * with the same padding on every side (the count's line is as high as the icon).
 * [onClick] null draws it without a touch (a count to read only). Neutral unless given [colors]. [isNew]: something new
 * waits behind it (a stop's comments or photos), a [NewIndicator] on the icon's top end until it's seen.
 */
@Composable
fun JoinedButton(
    icon: ImageVector,
    contentDescription: String,
    shape: Shape,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    count: Int? = null,
    enabled: Boolean = true,
    colors: JoinedButtonColors = JoinedButtonDefaults.neutral(),
    isNew: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .padding(JoinedButtonDefaults.Padding)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(JoinedButtonDefaults.IconSize))
                // Cut into the button's own colour, half over the icon's corner.
                if (isNew) NewIndicator(Modifier.align(Alignment.TopEnd).offset(x = NewDotOffset, y = -NewDotOffset), strokeColor = colors.container)
            }
            count?.let { Text(compactCount(it), style = MaterialTheme.typography.labelLarge) }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = colors.container,
            contentColor = colors.content,
            modifier = modifier,
            content = content,
        )
    } else {
        Surface(shape = shape, color = colors.container, contentColor = colors.content, modifier = modifier, content = content)
    }
}

/**
 * The flame of a [JoinedButtons] group, which marks the place hot ([hot]: I did): tinted, filled while marked, and
 * marking it pops the flame like [HotButton] does. [isNew]: flames came that I haven't seen; the flame pops once as it
 * shows, with no dot (Shahin, 2026-11-13), and [onNewSeen] says so. The pop is one-shot and stops with the button.
 */
@Composable
fun HotJoinedButton(
    hot: Boolean,
    count: Int,
    shape: Shape,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    onNewSeen: () -> Unit = {},
) {
    var burst by remember { mutableIntStateOf(0) }
    val currentOnNewSeen by rememberUpdatedState(onNewSeen)
    LaunchedEffect(isNew) {
        if (!isNew) return@LaunchedEffect
        burst++
        currentOnNewSeen()
    }
    val haptics = rememberHaptics()
    val flame = MaterialTheme.extendedColors.attention
    Surface(
        checked = hot,
        onCheckedChange = { markHot ->
            if (markHot) burst++
            haptics.toggle(markHot)
            onToggle()
        },
        shape = shape,
        color = if (hot) flame.copy(alpha = HOT_TINT) else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(JoinedButtonDefaults.Padding),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BurstIcon(
                icon = if (hot) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
                contentDescription = stringResource(Res.string.mark_hot),
                tint = flame,
                burst = burst,
                modifier = Modifier.size(JoinedButtonDefaults.IconSize),
            )
            Text(compactCount(count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

data class JoinedButtonColors(val container: Color, val content: Color)

object JoinedButtonDefaults {
    val IconSize = 20.dp

    /** The same on every side, so an icon alone is a square and its height the touch target's 48 dp. */
    val Padding = 14.dp
    val Height = IconSize + Padding * 2
    val Gap = 3.dp
    val InnerCorner = 6.dp

    /** The quiet grey of most buttons. */
    @Composable
    fun neutral() = JoinedButtonColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)

    /** A destructive button: red on a see-through red, like glass. */
    @Composable
    fun destructive() = JoinedButtonColors(MaterialTheme.colorScheme.error.copy(alpha = DESTRUCTIVE_TINT), MaterialTheme.colorScheme.error)

    /** The one filled accent button of a group (Add). */
    @Composable
    fun accent() = JoinedButtonColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
}

private const val DISABLED_ALPHA = 0.38f
private const val DESTRUCTIVE_TINT = 0.14f
private const val HOT_TINT = 0.16f
/** How far the "new" dot sits out over the icon's top end corner. */
private val NewDotOffset = 4.dp

@Composable
private fun JoinedButtonsPreviewContent() {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        JoinedButtons(
            listOf<JoinedButtonSlot>(
                { HotJoinedButton(hot = true, count = 12, shape = it, onToggle = {}) },
                { JoinedButton(Icons.AutoMirrored.Outlined.Comment, "Comments", it, onClick = {}, count = 1_204, isNew = true) },
                { JoinedButton(Icons.Outlined.PhotoLibrary, "Photos", it, onClick = {}, count = 3, enabled = false) },
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            JoinedButtons(
                listOf<JoinedButtonSlot>(
                    { JoinedButton(Icons.Outlined.Info, "Details", it, onClick = {}) },
                    { JoinedButton(Icons.Outlined.Delete, "Delete", it, onClick = {}, colors = JoinedButtonDefaults.destructive()) },
                ),
            )
            JoinedButtons(
                listOf<JoinedButtonSlot>(
                    { JoinedButton(Icons.Outlined.Add, "Add", it, onClick = {}, colors = JoinedButtonDefaults.accent()) },
                    { JoinedButton(Icons.Default.Close, "Close", it, onClick = {}) },
                ),
            )
        }
    }
}

@Preview
@Composable
private fun JoinedButtonsPreview() {
    TripinglyTheme { JoinedButtonsPreviewContent() }
}

@Preview
@Composable
private fun JoinedButtonsDarkPreview() {
    TripinglyTheme(darkTheme = true) { JoinedButtonsPreviewContent() }
}

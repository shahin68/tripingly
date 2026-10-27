package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/** A person as a round avatar: the first letter of [name] on a quiet neutral circle (people have no colours). */
@Composable
fun AvatarCircle(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSize.Medium,
) {
    Box(
        modifier = modifier
            .size(size)
            .skeleton(CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase().orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (size >= AvatarSize.Medium) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * An avatar with the name and `@handle` below that the user taps to pick or unpick the person,
 * e.g. in a row of friends. A picked person gets a ring and a check mark.
 */
@Composable
fun AvatarChoice(
    name: String,
    handle: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .width(AvatarSize.ChoiceWidth)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Box {
            AvatarCircle(
                name = name,
                modifier = if (selected) {
                    Modifier.border(AvatarSize.Ring, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(AvatarSize.Badge)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(AvatarSize.BadgePadding),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "@$handle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

object AvatarSize {
    val Small = 24.dp
    val Row = 40.dp
    val Medium = 56.dp
    internal val ChoiceWidth = 72.dp
    internal val Ring = 2.dp
    internal val Badge = 20.dp
    internal val BadgePadding = 3.dp
}


@Preview
@Composable
private fun AvatarChoicePreview() {
    TripinglyTheme {
        Row {
            AvatarChoice(name = "Anna", handle = "anna", selected = true, onToggle = {})
            AvatarChoice(name = "Jonas", handle = "jonas.k", selected = false, onToggle = {})
            AvatarChoice(name = "Maximilian-Alexander", handle = "max", selected = false, onToggle = {})
        }
    }
}

@Preview
@Composable
private fun AvatarChoiceDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
            AvatarChoice(name = "Anna", handle = "anna", selected = true, onToggle = {})
            AvatarCircle(name = "Lena", size = AvatarSize.Small)
        }
    }
}

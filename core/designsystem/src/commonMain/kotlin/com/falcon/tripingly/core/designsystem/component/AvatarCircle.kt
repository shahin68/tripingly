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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.ProfilePhoto
import com.falcon.tripingly.core.model.trip.UserSummary

/**
 * A person as a round avatar: their profile [photo], or without one the first letter of [name] on a quiet
 * neutral circle (people have no colours). The letter also shows while the photo loads.
 */
@Composable
fun AvatarCircle(
    name: String,
    photo: ProfilePhoto?,
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
        if (photo != null) {
            val context = LocalPlatformContext.current
            AsyncImage(
                model = remember(context, photo) { photoRequest(context, photo.id, photo.thumbUrl, PhotoSize.Thumb) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** [AvatarCircle] for a [user]. */
@Composable
fun AvatarCircle(user: UserSummary, modifier: Modifier = Modifier, size: Dp = AvatarSize.Medium) =
    AvatarCircle(user.displayName, user.photo, modifier, size)

/**
 * An avatar with the name and `@handle` below that the user taps to pick or unpick the person,
 * e.g. in a row of friends. A picked person gets a ring and a check mark.
 */
@Composable
fun AvatarChoice(
    user: UserSummary,
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
                user = user,
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
                text = user.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "@${user.username}",
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
            AvatarChoice(UserSummary("1", "anna", "Anna"), selected = true, onToggle = {})
            AvatarChoice(UserSummary("2", "jonas.k", "Jonas"), selected = false, onToggle = {})
            AvatarChoice(UserSummary("3", "max", "Maximilian-Alexander"), selected = false, onToggle = {})
        }
    }
}

@Preview
@Composable
private fun AvatarChoiceDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
            AvatarChoice(UserSummary("1", "anna", "Anna"), selected = true, onToggle = {})
            AvatarCircle(name = "Lena", photo = null, size = AvatarSize.Small)
        }
    }
}

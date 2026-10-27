package com.falcon.tripingly.feature.social.presentation.profile

import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.component.skeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_follow
import com.falcon.tripingly.feature.social.generated.resources.social_followers
import com.falcon.tripingly.feature.social.generated.resources.social_following
import com.falcon.tripingly.feature.social.generated.resources.social_following_button
import com.falcon.tripingly.feature.social.generated.resources.social_follows_you
import com.falcon.tripingly.feature.social.generated.resources.social_public_trips_label
import org.jetbrains.compose.resources.stringResource

/** My profile at the top of the Profile tab: avatar, name and handle, then my counts as three tiles. */
@Composable
internal fun MyProfileHeader(profile: Profile, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(HeaderPadding), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                AvatarCircle(name = profile.user.displayName, size = AvatarSize.Medium)
                Column {
                    Text(profile.user.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
                    Text("@${profile.user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.skeletonText())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                StatTile(profile.followerCount, stringResource(Res.string.social_followers), Modifier.weight(1f))
                StatTile(profile.followingCount, stringResource(Res.string.social_following), Modifier.weight(1f))
                StatTile(profile.publicTripCount, stringResource(Res.string.social_public_trips_label), Modifier.weight(1f))
            }
        }
    }
}

/**
 * A profile opened from elsewhere: a large avatar, the name, "Follows you", the counts and Follow / Following
 * ([onToggleFollow] null on my own, opened e.g. from a trip of mine).
 */
@Composable
internal fun PersonProfileHeader(profile: Profile, onToggleFollow: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        AvatarCircle(name = profile.user.displayName, size = LargeAvatar)
        Text(profile.user.displayName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        if (profile.followsMe) {
            Text(stringResource(Res.string.social_follows_you), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
            Count(profile.followerCount, stringResource(Res.string.social_followers))
            Count(profile.followingCount, stringResource(Res.string.social_following))
        }
        if (onToggleFollow == null) return@Column
        if (profile.isFollowing) {
            SecondaryButton(stringResource(Res.string.social_following_button), onToggleFollow, size = ButtonSize.Medium)
        } else {
            PrimaryButton(stringResource(Res.string.social_follow), onToggleFollow, size = ButtonSize.Medium)
        }
    }
}

@Composable
private fun StatTile(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .skeleton(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, vertical = MaterialTheme.spacing.small),
    ) {
        Text(compactCount(count), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Count(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(compactCount(count), style = MaterialTheme.typography.titleMedium, modifier = Modifier.skeletonText())
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.skeletonText())
    }
}

private val HeaderPadding = 16.dp
private val LargeAvatar = 84.dp

@Preview
@Composable
private fun MyProfileHeaderPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) { MyProfileHeader(previewProfile) }
    }
}

@Preview
@Composable
private fun MyProfileHeaderDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) { MyProfileHeader(previewProfile) }
    }
}

@Preview
@Composable
private fun PersonProfileHeaderPreview(@PreviewParameter(ProfileHeaderPreviewStates::class) profile: Profile) {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) { PersonProfileHeader(profile = profile, onToggleFollow = {}) }
    }
}

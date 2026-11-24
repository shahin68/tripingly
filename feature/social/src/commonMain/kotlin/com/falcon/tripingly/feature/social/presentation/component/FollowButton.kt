package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Row
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.ToggleButton
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_follow
import com.falcon.tripingly.feature.social.generated.resources.social_following_button
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background

/**
 * Follow, or Following once I follow them: ONE [ToggleButton] that changes its look, as Route and Ask to join do
 * (Shahin, 2026-11-24): brand-filled Follow, neutral Following. A tap ([onToggle]) waits for the server: while
 * [isPending] the pill shrinks into a circle with a spinner, and once the server took it the parent's [isFollowing]
 * grows it into the new label while the colour glides; a refusal grows it back as it was. Its room is as wide as the
 * wider of the two in every language, so nothing beside it moves; the button sits at that room's end by default, so
 * Follow buttons line up down a list ([alignment]).
 */
@Composable
internal fun FollowButton(
    isFollowing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Medium,
    isPending: Boolean = false,
    alignment: Alignment = Alignment.CenterEnd,
) {
    val follow = stringResource(Res.string.social_follow)
    val following = stringResource(Res.string.social_following_button)
    Box(modifier, contentAlignment = alignment) {
        // Only their room: both labels laid out, never drawn, read out or tapped.
        Box(Modifier.alpha(0f).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            PrimaryButton(follow, {}, enabled = false, size = size)
            SecondaryButton(following, {}, enabled = false, size = size)
        }
        ToggleButton(
            text = if (isFollowing) following else follow,
            checked = isFollowing,
            onClick = onToggle,
            size = size,
            isLoading = isPending,
            quietWhenChecked = true,
        )
    }
}

@Preview
@Composable
private fun FollowButtonPreview() {
    TripinglyTheme {
        Row {
            FollowButton(isFollowing = false, onToggle = {})
            FollowButton(isFollowing = true, onToggle = {}, size = ButtonSize.Small)
            FollowButton(isFollowing = false, onToggle = {}, size = ButtonSize.Small, isPending = true)
        }
    }
}

@Preview
@Composable
private fun FollowButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
            FollowButton(isFollowing = true, onToggle = {})
            FollowButton(isFollowing = false, onToggle = {}, size = ButtonSize.Small)
        }
    }
}

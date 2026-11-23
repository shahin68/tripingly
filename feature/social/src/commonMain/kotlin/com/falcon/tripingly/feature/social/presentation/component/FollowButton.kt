package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.animation.Crossfade
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
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_follow
import com.falcon.tripingly.feature.social.generated.resources.social_following_button
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background

/**
 * Follow, or Following once I follow them; a tap switches at once ([onToggle]) with a soft crossfade. It's as wide as
 * the wider of the two in every language, so nothing beside it moves when it switches.
 */
@Composable
internal fun FollowButton(isFollowing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, size: ButtonSize = ButtonSize.Medium) {
    val haptics = rememberHaptics()
    val toggle = {
        haptics.toggle(!isFollowing)
        onToggle()
    }
    val follow = stringResource(Res.string.social_follow)
    val following = stringResource(Res.string.social_following_button)
    Box(modifier, contentAlignment = Alignment.Center) {
        // Only their room: both labels laid out, never drawn, read out or tapped.
        Box(Modifier.alpha(0f).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            PrimaryButton(follow, {}, enabled = false, size = size)
            SecondaryButton(following, {}, enabled = false, size = size)
        }
        Crossfade(isFollowing, label = "follow") { isOn ->
            Box(contentAlignment = Alignment.Center) {
                if (isOn) SecondaryButton(following, toggle, size = size) else PrimaryButton(follow, toggle, size = size)
            }
        }
    }
}

@Preview
@Composable
private fun FollowButtonPreview() {
    TripinglyTheme {
        Row {
            FollowButton(isFollowing = false, onToggle = {})
            FollowButton(isFollowing = true, onToggle = {}, size = ButtonSize.Small)
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

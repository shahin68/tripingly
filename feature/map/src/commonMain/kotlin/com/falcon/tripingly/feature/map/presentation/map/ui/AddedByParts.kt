package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_added_by_you
import com.falcon.tripingly.feature.map.generated.resources.map_place_added_by
import com.falcon.tripingly.feature.map.generated.resources.map_spot_by
import com.falcon.tripingly.feature.map.presentation.map.AddedBy
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.trip.UserSummary

/**
 * Who added a pin, inline after its type on the big card (Shahin's B, 2026-11-13): "· ", their face and [text], the same
 * words as in the details ("Added by @handle", "Added by you", "Spot by @handle"; Shahin, 2026-11-14). The text ends in
 * "…" when it doesn't fit; the face stays. A place's maker comes with its details, after the card opened: its room
 * glides open as it fades in, never a pop (Shahin, 2026-11-24).
 */
@Composable
internal fun AddedByInline(addedBy: AddedBy?, modifier: Modifier = Modifier, text: @Composable (AddedBy) -> String) {
    AnimatedContent(
        targetState = addedBy,
        contentKey = { it?.user?.id to it?.isMe },
        transitionSpec = { fadeIn(BylineFade) togetherWith fadeOut(BylineFade) using SizeTransform(clip = false) { _, _ -> BylineSize } },
        contentAlignment = Alignment.CenterStart,
        modifier = modifier,
        label = "added by",
    ) { shown ->
        if (shown != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
                Text("·", style = MaterialTheme.typography.bodySmall)
                shown.user?.let { AvatarCircle(it, size = AvatarSize.Tiny) }
                Text(
                    text(shown),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val BylineEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val BylineFade = tween<Float>(durationMillis = 300, easing = BylineEasing)
private val BylineSize = tween<IntSize>(durationMillis = 300, easing = BylineEasing)

/** "Added by @handle" or "Added by you": who put a stop on the trip, for the details. */
@Composable
internal fun AddedBy.text(): String =
    if (isMe) stringResource(Res.string.map_added_by_you) else stringResource(Res.string.map_place_added_by, user?.username.orEmpty())

/** "Spot by @handle": who made a custom spot, on the trip or off it (Shahin, 2026-11-13: "added by" is only for the trip). */
@Composable
internal fun AddedBy.spotText(): String = stringResource(Res.string.map_spot_by, user?.username.orEmpty())

/** A stop added by someone, one added by me, and a spot by someone, each after its type as on the big card. */
@Composable
private fun AddedByPreviewContent() {
    val anna = UserSummary(id = "u2", username = "anna", displayName = "Anna Kovács")
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Café", style = MaterialTheme.typography.bodySmall)
            AddedByInline(AddedBy(anna, isMe = false), Modifier.padding(start = MaterialTheme.spacing.extraSmall)) { it.text() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Museum", style = MaterialTheme.typography.bodySmall)
            AddedByInline(AddedBy(anna, isMe = true), Modifier.padding(start = MaterialTheme.spacing.extraSmall)) { it.text() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Viewpoint", style = MaterialTheme.typography.bodySmall)
            AddedByInline(AddedBy(anna, isMe = false), Modifier.padding(start = MaterialTheme.spacing.extraSmall)) { it.spotText() }
        }
    }
}

@Preview
@Composable
private fun AddedByPreview() {
    TripinglyTheme { AddedByPreviewContent() }
}

@Preview
@Composable
private fun AddedByDarkPreview() {
    TripinglyTheme(darkTheme = true) { AddedByPreviewContent() }
}

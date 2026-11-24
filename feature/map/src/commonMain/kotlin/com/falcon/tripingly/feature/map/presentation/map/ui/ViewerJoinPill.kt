package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.InviteButton
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_join_sent
import com.falcon.tripingly.feature.map.generated.resources.map_join_trip
import com.falcon.tripingly.feature.map.presentation.map.JoinButton
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * Someone else's trip: the trip's heart and Ask to join, joined like the details' buttons, under the map's header at
 * the end and as high as the header (Shahin, 2026-11-22). Tapped, it plays into "Request sent" (a spinner
 * on its icon until the server answers) and stays so until the owner decides (also next time the trip opens; ⋮ withdraws).
 */
@Composable
internal fun ViewerJoinPill(joinButton: JoinButton, isLikedByMe: Boolean, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    val isSent = joinButton == JoinButton.Sent
    InviteButton(
        text = stringResource(if (isSent) Res.string.map_join_sent else Res.string.map_join_trip),
        icon = if (isSent) Icons.Outlined.HowToReg else Icons.Outlined.PersonAdd,
        onClick = { onAction(MapAction.Header.RequestToJoin) }.takeIf { joinButton == JoinButton.Ask },
        isLoading = joinButton == JoinButton.Sending,
        liked = isLikedByMe,
        onToggleLike = { onAction(MapAction.Header.ToggleLike) },
        height = GlassButtonDefaults.HeaderSize,
        modifier = modifier,
    )
}

/** Ask to join not liked yet, sending while liked, and sent. */
@Composable
private fun ViewerJoinPillPreviewContent() {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.End,
    ) {
        ViewerJoinPill(JoinButton.Ask, isLikedByMe = false, onAction = {})
        ViewerJoinPill(JoinButton.Sending, isLikedByMe = true, onAction = {})
        ViewerJoinPill(JoinButton.Sent, isLikedByMe = true, onAction = {})
    }
}

@Preview
@Composable
private fun ViewerJoinPillPreview() {
    TripinglyTheme { ViewerJoinPillPreviewContent() }
}

@Preview
@Composable
private fun ViewerJoinPillDarkPreview() {
    TripinglyTheme(darkTheme = true) { ViewerJoinPillPreviewContent() }
}

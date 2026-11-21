package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.InviteButton
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_join_sent
import com.falcon.tripingly.feature.map.generated.resources.map_join_trip
import com.falcon.tripingly.feature.map.presentation.map.JoinButton
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import org.jetbrains.compose.resources.stringResource

/**
 * Someone else's trip: the trip's heart and Ask to join under the map's header at the start, where owners see who wants
 * to join ([JoinRequestsPill]), as high as that pill (Shahin, 2026-11-21). Tapped, it plays into "Request sent" (a spinner
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
        height = JoinPillHeight,
        modifier = modifier,
    )
}

/** The join requests pill's height: its 32 dp faces and 4 dp around them. */
private val JoinPillHeight = 40.dp

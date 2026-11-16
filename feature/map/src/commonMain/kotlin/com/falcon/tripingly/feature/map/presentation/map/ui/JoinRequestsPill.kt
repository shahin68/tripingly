package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarStack
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_join_request
import com.falcon.tripingly.feature.map.generated.resources.map_join_requests
import com.falcon.tripingly.feature.map.presentation.map.JoinRequestsState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "@anna and 2 others want to join" under the map's header at the start, in line with the back button, their faces
 * stacked; a tap opens the members sheet. Shown in full a while ([ShrinkDelayMillis]), it shrinks sideways to its faces
 * ([onSeen]), and for people seen before it is only that small circle (Shahin, 2026-11-16).
 */
@Composable
internal fun JoinRequestsPill(requests: JoinRequestsState, onClick: () -> Unit, onSeen: () -> Unit, modifier: Modifier = Modifier) {
    if (!requests.isCompact) {
        LaunchedEffect(requests.people) {
            delay(ShrinkDelayMillis)
            onSeen()
        }
    }
    GlassSurface(modifier = modifier, shape = CircleShape, onClick = onClick, blur = false) {
        Row(modifier = Modifier.padding(MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
            AvatarStack(requests.people, size = FacesSize)
            AnimatedVisibility(
                visible = !requests.isCompact,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                val first = requests.people.first().username
                Text(
                    text = if (requests.othersCount == 0) {
                        stringResource(Res.string.map_join_request, first)
                    } else {
                        pluralStringResource(Res.plurals.map_join_requests, requests.othersCount, first, requests.othersCount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = MaterialTheme.spacing.small, end = MaterialTheme.spacing.medium - MaterialTheme.spacing.extraSmall),
                )
            }
        }
    }
}

/** How long the pill shows its people in full before it shrinks to their faces. */
private const val ShrinkDelayMillis = 4_000L

private val FacesSize = 32.dp

@Preview
@Composable
private fun JoinRequestsPillPreview() {
    val people = persistentListOf(
        UserSummary(id = "u2", username = "anna", displayName = "Anna Kovács"),
        UserSummary(id = "u3", username = "jonas.k", displayName = "Jonas K"),
        UserSummary(id = "u4", username = "mia", displayName = "Mia"),
    )
    TripinglyTheme {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            JoinRequestsPill(JoinRequestsState(persistentListOf(people.first()), othersCount = 0), onClick = {}, onSeen = {})
            JoinRequestsPill(JoinRequestsState(people, othersCount = 4), onClick = {}, onSeen = {})
            JoinRequestsPill(JoinRequestsState(people, othersCount = 4, isCompact = true), onClick = {}, onSeen = {})
        }
    }
}

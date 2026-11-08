package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "@anna and 2 others want to join" under the map's header at the start, in line with the back button, their faces stacked; a tap opens the members sheet. */
@Composable
internal fun JoinRequestsPill(requests: JoinRequestsState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = CircleShape, onClick = onClick, blur = false) {
        Row(
            modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.extraSmall, bottom = MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarStack(requests.people, size = FacesSize)
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
            )
        }
    }
}

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
            JoinRequestsPill(JoinRequestsState(persistentListOf(people.first()), othersCount = 0), onClick = {})
            JoinRequestsPill(JoinRequestsState(people, othersCount = 4), onClick = {})
        }
    }
}

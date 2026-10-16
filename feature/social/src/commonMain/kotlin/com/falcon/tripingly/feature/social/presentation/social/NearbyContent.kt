package com.falcon.tripingly.feature.social.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceImageSource
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_place
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_allow
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_ask
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_blocked
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_distance_km
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_distance_m
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_empty
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_open_settings
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Action
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.NearbyStatus
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.State
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NearbyContent(state: State, onAction: (Action) -> Unit) {
    when (state.nearbyStatus) {
        NearbyStatus.Unknown, NearbyStatus.Locating -> Centered { CircularProgressIndicator() }
        NearbyStatus.NeedsPermission, NearbyStatus.Blocked, NearbyStatus.Unavailable -> LocationAsk(state.nearbyStatus, onAction)
        NearbyStatus.Ready -> {
            val listState = rememberLazyListState()
            LoadMoreAtEnd(listState) { onAction(Action.OnLoadMore) }
            PullToRefreshBox(
                isRefreshing = state.isLoadingNearby,
                onRefresh = { onAction(Action.OnRefresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (state.nearby.isEmpty()) {
                        item { EmptyText(stringResource(Res.string.social_nearby_empty)) }
                    }
                    items(state.nearby, key = { it.id }) { place ->
                        NearbyPlaceRow(
                            place = place,
                            onLike = { onAction(Action.OnLikePlace(place)) },
                            onAdd = { onAction(Action.OnAddPlace(place)) },
                        )
                    }
                    // Nearby lists OpenStreetMap places.
                    item { OsmAttribution(Modifier.padding(MaterialTheme.spacing.medium)) }
                }
            }
        }
    }
}

/** Why Nearby needs the location, and the way to allow it. */
@Composable
private fun LocationAsk(status: NearbyStatus, onAction: (Action) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(if (status == NearbyStatus.Blocked) Res.string.social_nearby_blocked else Res.string.social_nearby_ask),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium),
        )
        Button(onClick = { onAction(Action.OnAllowLocation) }) {
            Text(
                stringResource(if (status == NearbyStatus.Blocked) Res.string.social_nearby_open_settings else Res.string.social_nearby_allow),
            )
        }
    }
}

@Composable
private fun NearbyPlaceRow(place: NearbyPlace, onLike: () -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only a trip photo: a Commons cover needs its credit, which this list doesn't have.
        val tripCover = place.coverImageId?.takeIf { place.coverSource == PlaceImageSource.Trip }
        PhotoImage(
            photoId = tripCover ?: place.id,
            url = place.coverThumbUrl?.takeIf { tripCover != null },
            size = PhotoSize.Thumb,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = distanceLabel(place.distanceMeters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeButton(liked = place.likedByMe, count = place.likeCount, onToggle = onLike)
                TextButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.social_add_place), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** "350 m" up to a kilometre, then "2.4 km". */
@Composable
private fun distanceLabel(meters: Int): String =
    if (meters < 1000) {
        stringResource(Res.string.social_nearby_distance_m, meters)
    } else {
        val tenths = (meters / 100.0).roundToInt()
        stringResource(Res.string.social_nearby_distance_km, "${tenths / 10}.${tenths % 10}")
    }

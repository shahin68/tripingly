package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_to_my_trips
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_report
import com.falcon.tripingly.feature.social.generated.resources.social_trip_copy_count
import com.falcon.tripingly.feature.social.generated.resources.social_trip_stop_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Someone's public trip: who made it, its cover, likes and "Add to my trips". */
@Composable
internal fun PublicTripCard(
    trip: PublicTrip,
    isCopying: Boolean,
    onOpen: () -> Unit,
    onOpenOwner: () -> Unit,
    onLike: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
    showOwner: Boolean = true,
    /** My own trip, on my profile: nothing to add or report. */
    isMine: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showOwner) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenOwner)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarCircle(name = trip.trip.owner.displayName, key = trip.trip.owner.username, size = AvatarSize.Small)
                        Text(
                            text = "@${trip.trip.owner.username}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (!isMine) Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more))
                    }
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        actions = listOf(DropdownAction(stringResource(Res.string.social_report), isDestructive = true, onClick = onReport)),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                trip.trip.coverThumbUrl?.let { url ->
                    PhotoImage(
                        photoId = url,
                        url = url,
                        size = PhotoSize.Thumb,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(MaterialTheme.spacing.medium))
                }
                Column(Modifier.weight(1f)) {
                    Text(trip.trip.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    DateUtils.formatRange(trip.trip.startDate, trip.trip.endDate)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = buildList {
                            add(pluralStringResource(Res.plurals.social_trip_stop_count, trip.trip.markerCount, trip.trip.markerCount))
                            if (trip.copyCount > 0) {
                                add(pluralStringResource(Res.plurals.social_trip_copy_count, trip.copyCount, trip.copyCount))
                            }
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LikeButton(liked = trip.likedByMe, count = trip.likeCount, onToggle = onLike)
                if (!isMine) TextButton(onClick = onCopy, enabled = !isCopying) {
                    if (isCopying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(stringResource(Res.string.social_add_to_my_trips), modifier = Modifier.padding(start = MaterialTheme.spacing.small))
                }
            }
        }
    }
}

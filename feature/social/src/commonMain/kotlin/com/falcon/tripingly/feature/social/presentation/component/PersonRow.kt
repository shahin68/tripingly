package com.falcon.tripingly.feature.social.presentation.component

import com.falcon.tripingly.core.designsystem.component.skeletonText
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.CompactIconButton
import com.falcon.tripingly.core.designsystem.component.CompactIconInset
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_unfollow
import org.jetbrains.compose.resources.stringResource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary

/** A person in a list, on a card; tapping opens their profile, and ⋮ or a long press opens [actions] when there are any. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PersonRow(user: UserSummary, onClick: () -> Unit, modifier: Modifier = Modifier, actions: List<DropdownAction> = emptyList()) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = if (actions.isEmpty()) null else ({ menuOpen = true })),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // Symmetric, and as tall with ⋮ as without: the avatar sits the same distance from the top, bottom and start,
        // and the compact ⋮ is no taller than the avatar; its glyph lines up with the inset at the end.
        val inset = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall
        Row(
            modifier = Modifier.padding(start = inset, top = inset, bottom = inset, end = if (actions.isEmpty()) inset else inset - CompactIconInset),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(user, size = AvatarSize.Row)
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.skeletonText(),
                )
            }
            if (actions.isNotEmpty()) {
                Box {
                    CompactIconButton(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more), onClick = { menuOpen = true })
                    AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, actions = actions)
                }
            }
        }
    }
}

/** One of my people lists' ⋮: unfollow (friends and people I follow). */
@Composable
internal fun unfollowAction(onUnfollow: () -> Unit) =
    DropdownAction(stringResource(Res.string.social_unfollow), icon = Icons.Outlined.PersonRemove, onClick = onUnfollow)

@Composable
private fun PersonRowPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        previewPeople.forEach { user -> PersonRow(user = user, onClick = {}, actions = listOf(unfollowAction {})) }
    }
}

@Preview
@Composable
private fun PersonRowPreview() {
    TripinglyTheme { PersonRowPreviewContent() }
}

@Preview
@Composable
private fun PersonRowDarkPreview() {
    TripinglyTheme(darkTheme = true) { PersonRowPreviewContent() }
}

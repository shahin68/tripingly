package com.falcon.tripingly.feature.social.presentation.component

import com.falcon.tripingly.core.designsystem.component.skeletonText
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_block
import com.falcon.tripingly.feature.social.generated.resources.social_block_confirm
import com.falcon.tripingly.feature.social.generated.resources.social_block_message
import com.falcon.tripingly.feature.social.generated.resources.social_block_title
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_report_user
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
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(name = user.displayName, size = AvatarSize.Row)
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
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more), modifier = Modifier.skeleton(CircleShape))
                    }
                    AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, actions = actions)
                }
            }
        }
    }
}

/** One of my people lists' ⋮: unfollow (when I follow them), report, block. */
@Composable
internal fun personActions(onUnfollow: (() -> Unit)?, onReport: () -> Unit, onBlock: () -> Unit): List<DropdownAction> = listOfNotNull(
    onUnfollow?.let { DropdownAction(stringResource(Res.string.social_unfollow), icon = Icons.Outlined.PersonRemove, onClick = it) },
    DropdownAction(stringResource(Res.string.social_report_user), isDestructive = true, icon = Icons.Outlined.Flag, onClick = onReport),
    DropdownAction(stringResource(Res.string.social_block), isDestructive = true, icon = Icons.Outlined.Block, onClick = onBlock),
)

/** Asks before blocking @[username], saying what blocking does. */
@Composable
internal fun BlockDialog(username: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(Res.string.social_block_title, username),
        message = stringResource(Res.string.social_block_message),
        confirmLabel = stringResource(Res.string.social_block_confirm),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun PersonRowPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        previewPeople.forEach { user -> PersonRow(user = user, onClick = {}, actions = personActions(onUnfollow = {}, onReport = {}, onBlock = {})) }
    }
}

@Preview
@Composable
private fun BlockDialogPreview() {
    TripinglyTheme { BlockDialog("anna", onConfirm = {}, onDismiss = {}) }
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

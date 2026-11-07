package com.falcon.tripingly.feature.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.AvatarStack
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.BackAction
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.SwipeAction
import com.falcon.tripingly.core.designsystem.component.SwipeActions
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.timeAgo
import com.falcon.tripingly.feature.notifications.generated.resources.Res
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_block
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_block_message
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_block_title
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_clear_all
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_clear_confirm
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_clear_message
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_clear_title
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_delete
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_empty
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_remove_follower
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_remove_follower_confirm
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_remove_follower_message
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_remove_follower_title
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_retry
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_title
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_unread
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.Action
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.Confirm
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.State
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The notification center on its own screen, opened from the bell on my profile. A tap opens the entry's
 * [NotificationEntry.deepLink] through [onOpen] with its id, which marks it read. While it is on screen everything in it
 * counts as seen. Swiping an entry to the start shows Delete, and Block or Remove follower for one person.
 */
@Composable
fun NotificationCenterRoute(
    onBack: () -> Unit,
    onOpen: (deepLink: String, notificationId: String) -> Unit,
    viewModel: NotificationCenterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.onAction(Action.OnShown)
        onDispose { viewModel.onAction(Action.OnHidden) }
    }
    NotificationCenterScreen(
        state = state,
        onBack = onBack,
        onAction = { action ->
            if (action is Action.OnOpen) onOpen(action.entry.deepLink, action.entry.id)
            viewModel.onAction(action)
        },
    )
    state.confirming?.let { ConfirmDialog(it, viewModel::onAction) }
}

@Composable
internal fun NotificationCenterScreen(state: State, onBack: () -> Unit, onAction: (Action) -> Unit) {
    Scaffold(
        topBar = {
            FloatingTopBar(
                title = stringResource(Res.string.notifications_title),
                navigationIcon = { BackAction(onBack) },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = { onAction(Action.OnClearAll) }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(Res.string.notifications_clear_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.OnMessageShown) }, isError = state.message !is UiText.Note)
        NotificationList(state, onAction, screenContentPadding(padding))
    }
}

@Composable
private fun NotificationList(state: State, onAction: (Action) -> Unit, contentPadding: PaddingValues) {
    val listState = rememberLazyListState()
    // Older entries load as the end of the list comes into view.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().filter { it }.collect { onAction(Action.OnLoadMore) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().glassSource(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        when {
            state.entries.isEmpty() && state.loadError != null -> item(key = "notifications-error") {
                Column(Modifier.fillMaxWidth().animateItem(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QuietText(state.loadError.asString())
                    SecondaryButton(stringResource(Res.string.notifications_retry), { onAction(Action.OnRetry) }, size = ButtonSize.Medium)
                }
            }
            state.entries.isEmpty() && !state.hasLoaded -> items(PlaceholderEntries, key = { "skeleton-${it.id}" }) {
                Skeleton { NotificationRow(entry = it, onClick = {}) }
            }
            state.entries.isEmpty() -> item(key = "notifications-empty") {
                QuietText(stringResource(Res.string.notifications_empty), Modifier.animateItem())
            }
            else -> items(state.entries, key = { it.id }) { entry ->
                SwipeActions(actions = swipeActionsOf(entry, onAction), modifier = Modifier.animateItem()) {
                    NotificationRow(entry = entry, onClick = { onAction(Action.OnOpen(entry)) })
                }
            }
        }
    }
}

/** Delete always; Block, and Remove follower on a follow, only when one person is behind it. */
@Composable
private fun swipeActionsOf(entry: NotificationEntry, onAction: (Action) -> Unit): List<SwipeAction> {
    val person = entry.actor?.takeIf { entry.actors.size <= 1 }
    return listOfNotNull(
        person?.takeIf { entry.isFollow }?.let {
            SwipeAction(stringResource(Res.string.notifications_remove_follower), Icons.Outlined.PersonRemove) { onAction(Action.OnRemoveFollower(it)) }
        },
        person?.let { SwipeAction(stringResource(Res.string.notifications_block), Icons.Outlined.Block) { onAction(Action.OnBlock(it)) } },
        SwipeAction(stringResource(Res.string.notifications_delete), Icons.Outlined.Delete, isDestructive = true) { onAction(Action.OnRemove(entry)) },
    )
}

@Composable
private fun ConfirmDialog(confirming: Confirm, onAction: (Action) -> Unit) {
    val (title, message, confirm) = when (confirming) {
        Confirm.ClearAll -> Triple(
            stringResource(Res.string.notifications_clear_title),
            stringResource(Res.string.notifications_clear_message),
            stringResource(Res.string.notifications_clear_confirm),
        )
        is Confirm.Block -> Triple(
            stringResource(Res.string.notifications_block_title, confirming.user.username),
            stringResource(Res.string.notifications_block_message),
            stringResource(Res.string.notifications_block),
        )
        is Confirm.RemoveFollower -> Triple(
            stringResource(Res.string.notifications_remove_follower_title, confirming.user.username),
            stringResource(Res.string.notifications_remove_follower_message),
            stringResource(Res.string.notifications_remove_follower_confirm),
        )
    }
    AppDialog(
        title = title,
        message = message,
        confirmLabel = confirm,
        isDestructive = true,
        onConfirm = { onAction(Action.OnConfirm) },
        onDismiss = { onAction(Action.OnDismissConfirm) },
    )
}

/** One entry on a card: who (or a bell for the app itself), what, when; unread ones carry a dot. */
@Composable
private fun NotificationRow(entry: NotificationEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        val inset = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall
        Row(
            modifier = Modifier.padding(inset),
            horizontalArrangement = Arrangement.spacedBy(inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val people = entry.actors.ifEmpty { listOfNotNull(entry.actor) }
            if (people.isNotEmpty()) {
                AvatarStack(people)
            } else {
                Box(
                    modifier = Modifier.size(AvatarSize.Row).skeleton(CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
                if (entry.body.isNotBlank()) {
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.skeletonText(),
                    )
                }
                Text(
                    text = timeAgo(entry.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.skeletonText(),
                )
            }
            if (!entry.isRead) {
                val unread = stringResource(Res.string.notifications_unread)
                Box(
                    Modifier
                        .size(UnreadDotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .semantics { contentDescription = unread },
                )
            }
        }
    }
}

@Composable
private fun QuietText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge),
    )
}

private val UnreadDotSize = 8.dp

/** Entries from the end at which the next page loads. */
private const val LOAD_MORE_THRESHOLD = 3

@Composable
private fun NotificationCenterPreview(state: State, darkTheme: Boolean) {
    TripinglyTheme(darkTheme = darkTheme) { NotificationCenterScreen(state = state, onBack = {}, onAction = {}) }
}

@Preview
@Composable
private fun NotificationCenterContentPreview() = NotificationCenterPreview(previewCenter, darkTheme = false)

@Preview
@Composable
private fun NotificationCenterContentDarkPreview() = NotificationCenterPreview(previewCenter, darkTheme = true)

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun NotificationCenterLandscapePreview() = NotificationCenterPreview(previewCenter, darkTheme = false)

@Preview
@Composable
private fun NotificationCenterLoadingPreview() = NotificationCenterPreview(State(), darkTheme = false)

@Preview
@Composable
private fun NotificationCenterEmptyPreview() = NotificationCenterPreview(State(isLoading = false, hasLoaded = true), darkTheme = true)

@Preview
@Composable
private fun NotificationCenterErrorPreview() = NotificationCenterPreview(previewCenterError, darkTheme = false)

@Preview
@Composable
private fun ConfirmDialogPreview() = TripinglyTheme { ConfirmDialog(Confirm.ClearAll, onAction = {}) }

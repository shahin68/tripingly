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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.core.ui.timeAgo
import com.falcon.tripingly.feature.notifications.generated.resources.Res
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_empty
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_retry
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_unread
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.Action
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.State
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The notification center as a list under [header] (where it is hosted, e.g. the Profile tab's header and
 * segments), so the host decides where it lives. A tap opens the entry's [NotificationEntry.deepLink] through
 * [onOpen] with its id, which marks it read. While it is on screen everything in it counts as seen.
 */
@Composable
fun NotificationCenterRoute(
    header: LazyListScope.() -> Unit,
    onOpen: (deepLink: String, notificationId: String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: NotificationCenterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.onAction(Action.OnShown)
        onDispose { viewModel.onAction(Action.OnHidden) }
    }
    NotificationCenterScreen(
        state = state,
        header = header,
        onAction = { action ->
            if (action is Action.OnOpen) onOpen(action.entry.deepLink, action.entry.id)
            viewModel.onAction(action)
        },
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
internal fun NotificationCenterScreen(
    state: State,
    header: LazyListScope.() -> Unit,
    onAction: (Action) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        header()
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
                NotificationRow(entry = entry, onClick = { onAction(Action.OnOpen(entry)) }, modifier = Modifier.animateItem())
            }
        }
    }
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
            val actor = entry.actor
            if (actor != null) {
                AvatarCircle(actor, size = AvatarSize.Row)
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
    TripinglyTheme(darkTheme = darkTheme) {
        NotificationCenterScreen(
            state = state,
            header = {},
            onAction = {},
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview
@Composable
private fun NotificationCenterContentPreview() = NotificationCenterPreview(previewCenter, darkTheme = false)

@Preview
@Composable
private fun NotificationCenterContentDarkPreview() = NotificationCenterPreview(previewCenter, darkTheme = true)

@Preview
@Composable
private fun NotificationCenterLoadingPreview() = NotificationCenterPreview(State(), darkTheme = false)

@Preview
@Composable
private fun NotificationCenterEmptyPreview() = NotificationCenterPreview(State(isLoading = false, hasLoaded = true), darkTheme = true)

@Preview
@Composable
private fun NotificationCenterErrorPreview() = NotificationCenterPreview(previewCenterError, darkTheme = false)

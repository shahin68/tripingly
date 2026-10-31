package com.falcon.tripingly.feature.map.presentation.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_comment_delete
import com.falcon.tripingly.feature.map.generated.resources.map_comment_delete_message
import com.falcon.tripingly.feature.map.generated.resources.map_comment_delete_title
import com.falcon.tripingly.feature.map.generated.resources.map_comment_menu
import com.falcon.tripingly.feature.map.generated.resources.map_comment_placeholder
import com.falcon.tripingly.feature.map.generated.resources.map_comment_report
import com.falcon.tripingly.feature.map.generated.resources.map_comment_send
import com.falcon.tripingly.feature.map.generated.resources.map_comments
import com.falcon.tripingly.feature.map.generated.resources.map_comments_empty
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_delete
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel.Action
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel.State
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.preview.previewStop
import kotlin.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The comments on [marker] in a sheet over the map; tapping an author opens their profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopCommentsRoute(
    marker: MapMarker,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StopCommentsViewModel = koinViewModel(key = "comments:${marker.id}") { parametersOf(marker.id) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box {
        StopCommentsScreen(
            state = state,
            draft = viewModel.draft,
            marker = marker,
            onOpenProfile = onOpenProfile,
            onAction = viewModel::onAction,
            onClose = onDismiss,
        )
        MessageBannerHost(Modifier.align(Alignment.TopCenter))
    }

    if (state.reporting != null) {
        ReportDialog(
            details = viewModel.reportDetails,
            onReport = { reason -> viewModel.onAction(Action.OnSendReport(reason)) },
            onDismiss = { viewModel.onAction(Action.OnDismissReport) },
            isSending = state.isReporting,
        )
    }

    if (state.commentToDelete != null) {
        AppDialog(
            title = stringResource(Res.string.map_comment_delete_title),
            message = stringResource(Res.string.map_comment_delete_message),
            confirmLabel = stringResource(Res.string.map_confirm_delete),
            isDestructive = true,
            onConfirm = { viewModel.onAction(Action.OnConfirmDelete) },
            onDismiss = { viewModel.onAction(Action.OnDismissDelete) },
        )
    }
}

@Composable
fun StopCommentsScreen(
    state: State,
    /** The view model's comment field. */
    draft: TextFieldState,
    marker: MapMarker,
    onOpenProfile: (String) -> Unit,
    onAction: (Action) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // More comments load as the end of the list comes into view.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().filter { it }.collect { onAction(Action.OnLoadMore) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(SHEET_HEIGHT_FRACTION)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SheetHeader(title = stringResource(Res.string.map_comments), subtitle = marker.title.asString(), onClose = onClose)
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.OnDismissMessage) },
            isError = state.message !is UiText.Note,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.comments.isEmpty() && state.isLoading -> Skeleton {
                    Column { PlaceholderComments.forEach { CommentRow(comment = it, onOpenProfile = {}, onAction = {}) } }
                }
                state.comments.isEmpty() -> Text(
                    text = stringResource(Res.string.map_comments_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(MaterialTheme.spacing.large),
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(comment = comment, onOpenProfile = onOpenProfile, onAction = onAction, modifier = Modifier.animateItem())
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                state = draft,
                placeholder = stringResource(Res.string.map_comment_placeholder),
                onImeAction = { onAction(Action.OnSend) },
                enabled = !state.isSending,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_DRAFT_LINES),
                inputTransformation = InputTransformation.maxLength(SocialRepository.MAX_COMMENT_LENGTH),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = { onAction(Action.OnSend) }, enabled = draft.text.isNotBlank() && !state.isSending) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.map_comment_send))
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    onOpenProfile: (String) -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        AvatarCircle(
            user = comment.author,
            size = AvatarSize.Small,
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(CircleShape)
                .clickable { onOpenProfile(comment.author.username) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .skeletonText()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenProfile(comment.author.username) },
                )
                Text(
                    text = DateUtils.formatAbbreviated(comment.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.skeletonText(),
                )
            }
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.skeletonText())
        }
        // The heart and ⋮ together, centred on each other by the row.
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
            LikeButton(
                liked = comment.likedByMe,
                count = comment.likeCount,
                onToggle = { onAction(Action.OnToggleLike(comment)) },
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.map_comment_menu), modifier = Modifier.skeleton(CircleShape))
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    // Mine, or on my trip: deleted rather than reported.
                    actions = listOf(
                        if (comment.canDelete) {
                            DropdownAction(stringResource(Res.string.map_comment_delete), isDestructive = true) { onAction(Action.OnDelete(comment)) }
                        } else {
                            DropdownAction(stringResource(Res.string.map_comment_report)) { onAction(Action.OnReport(comment)) }
                        },
                    ),
                )
            }
        }
    }
}

/** Placeholder comments of typical lengths: the rows' own skeleton while the first page loads. */
private val PlaceholderComments = listOf("Lovely place, we spent the whole afternoon here", "Go early", "The view from the top is worth it").mapIndexed { index, body ->
    Comment(
        id = "placeholder$index",
        markerId = "",
        body = body,
        author = UserSummary("placeholder", "traveller", "Traveller"),
        likeCount = 0,
        likedByMe = false,
        canDelete = false,
        createdAt = Instant.fromEpochSeconds(0),
    )
}

/** The sheet opens tall enough to read a conversation with the keyboard up. */
private const val SHEET_HEIGHT_FRACTION = 0.85f

private const val MAX_DRAFT_LINES = 4

/** Comments from the end at which the next page loads. */
private const val LOAD_MORE_THRESHOLD = 5

@Preview
@Composable
private fun StopCommentsScreenPreview(@PreviewParameter(StopCommentsPreviewStates::class) state: State) {
    TripinglyTheme {
        StopCommentsScreen(
            state = state,
            draft = rememberTextFieldState(),
            marker = previewStop,
            onOpenProfile = {},
            onAction = {},
            onClose = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun StopCommentsScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        StopCommentsScreen(
            state = StopCommentsPreviewStates().values.first(),
            draft = rememberTextFieldState("Great view from the top!"),
            marker = previewStop,
            onOpenProfile = {},
            onAction = {},
            onClose = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

/** Someone else's comment I liked, and my own, which I can delete. */
@Preview
@Composable
private fun CommentRowPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            StopCommentsPreviewStates().values.first().comments.forEach { comment ->
                CommentRow(comment = comment, onOpenProfile = {}, onAction = {})
            }
        }
    }
}

/** An icon button's touch area, the ⋮ beside a comment's like. */

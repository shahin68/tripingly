package com.falcon.tripingly.feature.map.presentation.comments

import kotlinx.collections.immutable.ImmutableList
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.falcon.tripingly.core.ui.shortTimeAgo
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_comment_cancel_reply
import com.falcon.tripingly.feature.map.generated.resources.map_comment_delete
import com.falcon.tripingly.feature.map.generated.resources.map_comment_delete_message
import com.falcon.tripingly.feature.map.generated.resources.map_comment_delete_title
import com.falcon.tripingly.feature.map.generated.resources.map_comment_hide_replies
import com.falcon.tripingly.feature.map.generated.resources.map_comment_menu
import com.falcon.tripingly.feature.map.generated.resources.map_comment_more_replies
import com.falcon.tripingly.feature.map.generated.resources.map_comment_placeholder
import com.falcon.tripingly.feature.map.generated.resources.map_comment_reply
import com.falcon.tripingly.feature.map.generated.resources.map_comment_reply_placeholder
import com.falcon.tripingly.feature.map.generated.resources.map_comment_replying_to
import com.falcon.tripingly.feature.map.generated.resources.map_comment_report
import com.falcon.tripingly.feature.map.generated.resources.map_comment_send
import com.falcon.tripingly.feature.map.generated.resources.map_comment_view_replies
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The comments on [marker] in a sheet over the map; tapping an author or a mention opens their profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopCommentsRoute(
    marker: MapMarker,
    /** The trip's people but me, who can be @mentioned. */
    people: ImmutableList<UserSummary>,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StopCommentsViewModel = koinViewModel(key = "comments:${marker.id}") { parametersOf(marker.id, people) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.onAction(StopCommentsViewModel.Action.OnShown)
        onDispose { viewModel.onAction(StopCommentsViewModel.Action.OnHidden) }
    }

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
    val rows = remember(state.comments, state.threads) { commentRows(state) }
    // The list glides to my new comment: among the newest, or in its thread once that has loaded.
    val sentIndex = rows.indexOfFirst { it is CommentListRow.Item && it.comment.id == state.sentCommentId }
    LaunchedEffect(state.sentCommentId, sentIndex >= 0) {
        if (sentIndex >= 0) listState.animateScrollToItem(sentIndex)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.replyingTo) { if (state.replyingTo != null) focusRequester.requestFocus() }

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
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is CommentListRow.Item -> CommentRow(
                                comment = row.comment,
                                onOpenProfile = onOpenProfile,
                                onAction = onAction,
                                modifier = Modifier.animateItem(),
                            )
                            is CommentListRow.Toggle -> RepliesButton(
                                text = if (row.isOpen) {
                                    stringResource(Res.string.map_comment_hide_replies)
                                } else {
                                    pluralStringResource(Res.plurals.map_comment_view_replies, row.main.replyCount, row.main.replyCount)
                                },
                                isLoading = row.isLoading,
                                onClick = { onAction(Action.OnToggleReplies(row.main)) },
                                modifier = Modifier.animateItem(),
                            )
                            is CommentListRow.MoreReplies -> RepliesButton(
                                text = stringResource(Res.string.map_comment_more_replies),
                                isLoading = row.isLoading,
                                onClick = { onAction(Action.OnLoadMoreReplies(row.main)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
        state.suggestions.forEach { person ->
            MentionSuggestion(person = person, onClick = { onAction(Action.OnMention(person)) })
        }
        state.replyingTo?.let { comment ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.map_comment_replying_to, "@${comment.author.username}"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(Action.OnCancelReply) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_comment_cancel_reply))
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
                placeholder = stringResource(if (state.replyingTo != null) Res.string.map_comment_reply_placeholder else Res.string.map_comment_placeholder),
                onImeAction = { onAction(Action.OnSend) },
                enabled = !state.isSending,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_DRAFT_LINES),
                inputTransformation = InputTransformation.maxLength(SocialRepository.MAX_COMMENT_LENGTH),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
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
            // Replies sit under their main comment's text.
            .padding(start = MaterialTheme.spacing.medium + if (comment.parentId != null) ReplyIndent else 0.dp)
            .padding(top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small),
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
                    text = shortTimeAgo(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.skeletonText(),
                )
            }
            Text(text = withMentions(comment, onOpenProfile), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.skeletonText())
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Under the text, with no ripple: just the heart reacts.
                LikeButton(
                    liked = comment.likedByMe,
                    count = comment.likeCount,
                    onToggle = { onAction(Action.OnToggleLike(comment)) },
                    indication = null,
                )
                TextButton(onClick = { onAction(Action.OnReply(comment)) }) {
                    Text(stringResource(Res.string.map_comment_reply), style = MaterialTheme.typography.labelMedium, modifier = Modifier.skeletonText())
                }
            }
        }
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

/** What the list shows, in order: main comments, each followed by its replies button and its open replies. */
private sealed interface CommentListRow {
    val key: String

    data class Item(val comment: Comment) : CommentListRow {
        override val key get() = comment.id
    }

    data class Toggle(val main: Comment, val isOpen: Boolean, val isLoading: Boolean) : CommentListRow {
        override val key get() = "replies:${main.id}"
    }

    data class MoreReplies(val main: Comment, val isLoading: Boolean) : CommentListRow {
        override val key get() = "more:${main.id}"
    }
}

private fun commentRows(state: State): List<CommentListRow> = state.comments.flatMap { main ->
    val thread = state.threads[main.id] ?: StopCommentsViewModel.Thread()
    buildList {
        add(CommentListRow.Item(main))
        if (main.replyCount > 0 || thread.replies.isNotEmpty()) {
            add(CommentListRow.Toggle(main, thread.isOpen, isLoading = thread.isLoading && thread.replies.isEmpty()))
        }
        if (thread.isOpen) {
            thread.replies.forEach { add(CommentListRow.Item(it)) }
            if (thread.nextCursor != null) add(CommentListRow.MoreReplies(main, thread.isLoading))
        }
    }
}

/** The body with the trip people it mentions in bold; tapping one opens their profile. */
@Composable
private fun withMentions(comment: Comment, onOpenProfile: (String) -> Unit): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(comment.body, comment.mentions, color) {
        buildAnnotatedString {
            var last = 0
            MentionPattern.findAll(comment.body).forEach { match ->
                val username = match.groupValues[1].lowercase().trimEnd('.')
                if (comment.mentions.none { it.equals(username, ignoreCase = true) }) return@forEach
                val end = match.range.first + 1 + username.length
                append(comment.body.substring(last, match.range.first))
                withLink(LinkAnnotation.Clickable(username, TextLinkStyles(SpanStyle(color = color, fontWeight = FontWeight.SemiBold))) { onOpenProfile(username) }) {
                    append(comment.body.substring(match.range.first, end))
                }
                last = end
            }
            append(comment.body.substring(last))
        }
    }
}

/** An `@handle` as the server reads it (backend `mentions.ts`). */
private val MentionPattern = Regex("(?<![\\w.@])@([a-z0-9_.]{3,30})", RegexOption.IGNORE_CASE)

@Composable
private fun RepliesButton(text: String, isLoading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = MaterialTheme.spacing.medium + ReplyIndent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, enabled = !isLoading) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

/** One of the trip's people matching the `@` being typed. */
@Composable
private fun MentionSuggestion(person: UserSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(user = person, size = AvatarSize.Small)
        Text(person.displayName, style = MaterialTheme.typography.labelLarge)
        Text("@${person.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How far a reply sits in: past its main comment's avatar. */
private val ReplyIndent = AvatarSize.Small + 8.dp

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


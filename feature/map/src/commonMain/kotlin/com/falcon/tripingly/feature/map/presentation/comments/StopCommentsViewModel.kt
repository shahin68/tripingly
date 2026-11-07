package com.falcon.tripingly.feature.map.presentation.comments

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_delete_comment
import com.falcon.tripingly.feature.map.generated.resources.map_error_like
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_comments
import com.falcon.tripingly.feature.map.generated.resources.map_error_report
import com.falcon.tripingly.feature.map.generated.resources.map_error_send_comment
import com.falcon.tripingly.feature.map.generated.resources.map_reported
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One stop's comments in the sheet: main comments, most liked first, then newest first, each with its replies
 * folded under it (oldest first). Everyone who sees the trip reads, writes, replies and likes; the author and
 * the trip's owner delete; others report. Typing `@` suggests the trip's [people] to mention. Comments added or
 * deleted by others, and new like counts, show up live while the sheet is open, and the server sends no pushes
 * about this stop's comments meanwhile.
 */
class StopCommentsViewModel(
    private val markerId: String,
    /** The trip's owner and members but me: who can be @mentioned. */
    private val people: List<UserSummary>,
    private val socialRepository: SocialRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val myUserId = (sessionRepository.session.value as? SessionState.SignedIn)?.account?.id

    /** The comment being written; sending clears it. */
    val draft = TextFieldState()

    /** The report dialog's details, cleared each time it opens. */
    val reportDetails = TextFieldState()

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    init {
        load(cursor = null)
        watchLiveChanges()
        suggestMentions()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnSend -> send()
            is Action.OnLoadMore -> _uiState.value.nextCursor?.takeUnless { _uiState.value.isLoading }?.let { load(it) }
            is Action.OnToggleReplies -> toggleReplies(action.main)
            is Action.OnLoadMoreReplies -> _uiState.value.threads[action.main.id]?.nextCursor?.let { loadReplies(action.main.id, it) }
            is Action.OnReply -> reply(action.comment)
            is Action.OnCancelReply -> _uiState.update { it.copy(replyingTo = null) }
            is Action.OnMention -> mention(action.user)
            is Action.OnToggleLike -> toggleLike(action.comment)
            is Action.OnDelete -> _uiState.update { it.copy(commentToDelete = action.comment) }
            is Action.OnConfirmDelete -> delete()
            is Action.OnDismissDelete -> _uiState.update { it.copy(commentToDelete = null) }
            is Action.OnReport -> {
                reportDetails.clearText()
                _uiState.update { it.copy(reporting = action.comment) }
            }
            is Action.OnSendReport -> sendReport(action.reason, reportDetails.text.toString().trim())
            is Action.OnDismissReport -> _uiState.update { it.copy(reporting = null) }
            is Action.OnDismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun load(cursor: String?) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = socialRepository.comments(markerId, cursor)) {
                is AppResult.Success -> _uiState.update { state ->
                    // A live comment may already be in the list.
                    val loaded = result.data.items.filter { new -> state.comments.none { it.id == new.id } }
                    state.copy(
                        comments = (if (cursor == null) loaded + state.comments else state.comments + loaded).toImmutableList(),
                        nextCursor = result.data.nextCursor,
                        isLoading = false,
                    )
                }
                is AppResult.Error -> _uiState.update { it.copy(isLoading = false, message = result.error.toUiText(Res.string.map_error_load_comments)) }
            }
        }
    }

    /** Opens a main comment's replies (loading them the first time), or folds them away. */
    private fun toggleReplies(main: Comment) {
        if (_uiState.value.threads[main.id]?.isOpen == true) updateThread(main.id) { it.copy(isOpen = false) } else openThread(main.id)
    }

    private fun openThread(mainId: String) {
        if (mainId in _uiState.value.threads) updateThread(mainId) { it.copy(isOpen = true) } else {
            updateThread(mainId) { Thread(isOpen = true) }
            loadReplies(mainId, cursor = null)
        }
    }

    private fun loadReplies(mainId: String, cursor: String?) {
        updateThread(mainId) { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = socialRepository.replies(mainId, cursor)) {
                is AppResult.Success -> updateThread(mainId) { thread ->
                    val loaded = result.data.items.filter { new -> thread.replies.none { it.id == new.id } }
                    thread.copy(replies = (thread.replies + loaded).toImmutableList(), nextCursor = result.data.nextCursor, isLoading = false)
                }
                is AppResult.Error -> {
                    updateThread(mainId) { it.copy(isLoading = false) }
                    _uiState.update { it.copy(message = result.error.toUiText(Res.string.map_error_load_comments)) }
                }
            }
        }
    }

    /** Replies go under the main comment; replying to someone else starts with their handle. */
    private fun reply(comment: Comment) {
        _uiState.update { it.copy(replyingTo = comment) }
        val handle = "@${comment.author.username} "
        if (comment.author.id != myUserId && !draft.text.startsWith(handle)) draft.setTextAndPlaceCursorAtEnd(handle + draft.text)
    }

    /** The `@` being typed right before the cursor suggests the trip's people whose name starts like it. */
    private fun suggestMentions() {
        viewModelScope.launch {
            snapshotFlow { mentionQuery(draft.text, draft.selection.start) }.collect { query ->
                val suggestions = query?.let { typed ->
                    people.filter { person ->
                        person.username.startsWith(typed.text, ignoreCase = true) ||
                            person.displayName.split(' ').any { it.startsWith(typed.text, ignoreCase = true) }
                    }.take(MAX_SUGGESTIONS)
                }.orEmpty()
                _uiState.update { it.copy(suggestions = suggestions.toImmutableList()) }
            }
        }
    }

    /** Puts the picked person's handle where the `@` is being typed. */
    private fun mention(user: UserSummary) {
        val query = mentionQuery(draft.text, draft.selection.start) ?: return
        draft.edit {
            replace(query.start, query.end, "@${user.username} ")
            placeCursorBeforeCharAt(query.start + user.username.length + 2)
        }
    }

    private fun watchLiveChanges() {
        viewModelScope.launch {
            socialRepository.commentChanges(markerId).collect { change ->
                when (change) {
                    is CommentChange.Added -> add(change.comment)
                    is CommentChange.Deleted -> remove(change.commentId)
                }
            }
        }
        viewModelScope.launch {
            socialRepository.likeCounts.collect { count ->
                if (count.target != LikeTarget.Comment) return@collect
                updateComment(count.targetId) { it.copy(likeCount = count.count) }
            }
        }
    }

    private fun send() {
        val body = draft.text.toString().trim()
        if (body.isEmpty() || _uiState.value.isSending) return
        val replyingTo = _uiState.value.replyingTo
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            when (val result = socialRepository.addComment(markerId, body, replyingTo?.let { it.parentId ?: it.id })) {
                is AppResult.Success -> {
                    draft.clearText()
                    add(result.data)
                    // A reply shows its whole thread.
                    result.data.parentId?.let(::openThread)
                    _uiState.update { it.copy(isSending = false, replyingTo = null, sentCommentId = result.data.id) }
                }
                is AppResult.Error -> _uiState.update { it.copy(isSending = false, message = result.error.toUiText(Res.string.map_error_send_comment)) }
            }
        }
    }

    /**
     * A new main comment goes where the server would list it: after the liked ones, as the newest. A reply goes
     * at the end of its thread when that is loaded, and counts on its main comment.
     */
    private fun add(comment: Comment) {
        val mainId = comment.parentId
        if (mainId == null) {
            updateComments { comments ->
                if (comments.any { it.id == comment.id }) return@updateComments comments
                val at = comments.indexOfFirst { it.likeCount <= comment.likeCount }.takeIf { it >= 0 } ?: comments.size
                comments.take(at) + comment + comments.drop(at)
            }
            return
        }
        val thread = _uiState.value.threads[mainId]
        if (thread?.replies?.any { it.id == comment.id } == true) return
        updateComment(mainId) { it.copy(replyCount = it.replyCount + 1) }
        // Unloaded replies come with the rest when the thread opens.
        if (thread != null && thread.nextCursor == null) updateThread(mainId) { it.copy(replies = (it.replies + comment).toImmutableList()) }
    }

    /** A main comment goes with its replies. */
    private fun remove(commentId: String) {
        val state = _uiState.value
        val mainId = state.threads.entries.firstOrNull { (_, thread) -> thread.replies.any { it.id == commentId } }?.key
        _uiState.update { it.copy(threads = (it.threads - commentId).toImmutableMap()) }
        updateComments { comments -> comments.filterNot { it.id == commentId } }
        if (mainId != null) {
            updateThread(mainId) { thread -> thread.copy(replies = thread.replies.filterNot { it.id == commentId }.toImmutableList()) }
            updateComment(mainId) { it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) }
        }
    }

    /** Shows at once and goes back if the server refuses. */
    private fun toggleLike(comment: Comment) {
        val liked = !comment.likedByMe
        updateComment(comment.id) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            when (val result = socialRepository.setLiked(LikeTarget.Comment, comment.id, liked)) {
                is AppResult.Success -> updateComment(comment.id) {
                    it.copy(likedByMe = result.data.liked, likeCount = result.data.likeCount)
                }
                is AppResult.Error -> {
                    updateComment(comment.id) { it.copy(likedByMe = comment.likedByMe, likeCount = comment.likeCount) }
                    _uiState.update { it.copy(message = result.error.toUiText(Res.string.map_error_like)) }
                }
            }
        }
    }

    private fun delete() {
        val comment = _uiState.value.commentToDelete ?: return
        _uiState.update { it.copy(commentToDelete = null) }
        viewModelScope.launch {
            when (val result = socialRepository.deleteComment(comment)) {
                is AppResult.Success -> remove(comment.id)
                is AppResult.Error -> _uiState.update { it.copy(message = result.error.toUiText(Res.string.map_error_delete_comment)) }
            }
        }
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val comment = _uiState.value.reporting ?: return
        if (_uiState.value.isReporting) return
        _uiState.update { it.copy(isReporting = true) }
        viewModelScope.launch {
            val result = socialRepository.report(ReportTarget.Comment, comment.id, reason, details)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isReporting = false, reporting = null, message = UiText.Note(UiText.Resource(Res.string.map_reported)))
                    is AppResult.Error -> it.copy(isReporting = false, message = result.error.toUiText(Res.string.map_error_report))
                }
            }
        }
    }

    private fun updateComments(change: (List<Comment>) -> List<Comment>) {
        _uiState.update { it.copy(comments = change(it.comments).toImmutableList()) }
    }

    private fun updateThread(mainId: String, change: (Thread) -> Thread) {
        _uiState.update { it.copy(threads = (it.threads + (mainId to change(it.threads[mainId] ?: Thread()))).toImmutableMap()) }
    }

    /** A main comment or a reply. */
    private fun updateComment(commentId: String, change: (Comment) -> Comment) {
        _uiState.update { state ->
            state.copy(
                comments = state.comments.map { if (it.id == commentId) change(it) else it }.toImmutableList(),
                threads = state.threads.mapValues { (_, thread) ->
                    thread.copy(replies = thread.replies.map { if (it.id == commentId) change(it) else it }.toImmutableList())
                }.toImmutableMap(),
            )
        }
    }

    /** A main comment's replies as far as they're loaded. */
    @Immutable
    data class Thread(
        val replies: ImmutableList<Comment> = persistentListOf(),
        /** More (newer) replies wait on the server. */
        val nextCursor: String? = null,
        val isOpen: Boolean = false,
        val isLoading: Boolean = false,
    )

    @Immutable
    data class State(
        /** Main comments, most liked first, then newest first. */
        val comments: ImmutableList<Comment> = persistentListOf(),
        /** Replies by main comment, once opened. */
        val threads: ImmutableMap<String, Thread> = persistentMapOf(),
        /** More main comments wait on the server. */
        val nextCursor: String? = null,
        val isLoading: Boolean = true,
        /** The draft is waiting for the server. */
        val isSending: Boolean = false,
        /** The comment I sent last: the list glides to it. */
        val sentCommentId: String? = null,
        /** The draft answers this comment: it goes under its main comment. */
        val replyingTo: Comment? = null,
        /** The trip's people matching the `@` being typed. */
        val suggestions: ImmutableList<UserSummary> = persistentListOf(),
        /** Waiting for the user to confirm. */
        val commentToDelete: Comment? = null,
        /** The comment the report dialog is about. */
        val reporting: Comment? = null,
        val isReporting: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        data object OnSend : Action
        data object OnLoadMore : Action
        /** Opens or folds a main comment's replies. */
        data class OnToggleReplies(val main: Comment) : Action
        data class OnLoadMoreReplies(val main: Comment) : Action
        /** Answers a main comment or a reply; either way it goes under the main comment. */
        data class OnReply(val comment: Comment) : Action
        data object OnCancelReply : Action
        /** A suggested person to mention was picked. */
        data class OnMention(val user: UserSummary) : Action
        data class OnToggleLike(val comment: Comment) : Action
        /** Asks first; see [OnConfirmDelete]. */
        data class OnDelete(val comment: Comment) : Action
        data object OnConfirmDelete : Action
        data object OnDismissDelete : Action
        data class OnReport(val comment: Comment) : Action
        data class OnSendReport(val reason: ReportReason) : Action
        data object OnDismissReport : Action
        data object OnDismissMessage : Action
    }

    private companion object {
        const val MAX_SUGGESTIONS = 5
    }
}

/** The `@handle` being typed at [cursor] in [text]: where it starts (the `@`), ends, and what follows the `@`. */
internal data class MentionQuery(val start: Int, val end: Int, val text: String)

internal fun mentionQuery(text: CharSequence, cursor: Int): MentionQuery? {
    var at = cursor
    while (at > 0 && text[at - 1].isHandleChar()) at--
    if (at == 0 || text[at - 1] != '@') return null
    val start = at - 1
    // Not inside a word or an email (anna@…), and not a second @.
    if (start > 0 && (text[start - 1].isHandleChar() || text[start - 1] == '@')) return null
    return MentionQuery(start, cursor, text.substring(at, cursor))
}

private fun Char.isHandleChar() = isLetterOrDigit() || this == '_' || this == '.'

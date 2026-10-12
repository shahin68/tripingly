package com.falcon.tripingly.feature.map.presentation.comments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_reported
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One stop's comments in the sheet, oldest first: everyone who sees the trip reads, writes and likes them;
 * the author and the trip's owner delete them; others report them. Comments added or deleted by others, and
 * new like counts, show up live while the sheet is open.
 */
class StopCommentsViewModel(
    private val markerId: String,
    private val socialRepository: SocialRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    init {
        load(cursor = null)
        watchLiveChanges()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnDraftChanged -> _uiState.update { it.copy(draft = action.text.take(SocialRepository.MAX_COMMENT_LENGTH)) }
            is Action.OnSend -> send()
            is Action.OnLoadMore -> _uiState.value.nextCursor?.takeUnless { _uiState.value.isLoading }?.let { load(it) }
            is Action.OnToggleLike -> toggleLike(action.comment)
            is Action.OnDelete -> _uiState.update { it.copy(commentToDelete = action.comment) }
            is Action.OnConfirmDelete -> delete()
            is Action.OnDismissDelete -> _uiState.update { it.copy(commentToDelete = null) }
            is Action.OnReport -> _uiState.update { it.copy(reporting = action.comment) }
            is Action.OnSendReport -> sendReport(action.reason, action.details)
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
                is AppResult.Error -> _uiState.update { it.copy(isLoading = false, message = result.error.toUiText()) }
            }
        }
    }

    private fun watchLiveChanges() {
        viewModelScope.launch {
            socialRepository.commentChanges.collect { change ->
                when (change) {
                    is CommentChange.Added -> if (change.comment.markerId == markerId) {
                        updateComments { comments -> if (comments.any { it.id == change.comment.id }) comments else comments + change.comment }
                    }
                    is CommentChange.Deleted -> updateComments { comments -> comments.filterNot { it.id == change.commentId } }
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
        val body = _uiState.value.draft.trim()
        if (body.isEmpty() || _uiState.value.isSending) return
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            when (val result = socialRepository.addComment(markerId, body)) {
                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        draft = "",
                        isSending = false,
                        comments = (state.comments.filterNot { it.id == result.data.id } + result.data).toImmutableList(),
                    )
                }
                is AppResult.Error -> _uiState.update { it.copy(isSending = false, message = result.error.toUiText()) }
            }
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
                    _uiState.update { it.copy(message = result.error.toUiText()) }
                }
            }
        }
    }

    private fun delete() {
        val comment = _uiState.value.commentToDelete ?: return
        _uiState.update { it.copy(commentToDelete = null) }
        viewModelScope.launch {
            when (val result = socialRepository.deleteComment(comment)) {
                is AppResult.Success -> updateComments { comments -> comments.filterNot { it.id == comment.id } }
                is AppResult.Error -> _uiState.update { it.copy(message = result.error.toUiText()) }
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
                    is AppResult.Success -> it.copy(isReporting = false, reporting = null, message = UiText.Resource(Res.string.map_reported))
                    is AppResult.Error -> it.copy(isReporting = false, message = result.error.toUiText())
                }
            }
        }
    }

    private fun updateComments(change: (List<Comment>) -> List<Comment>) {
        _uiState.update { it.copy(comments = change(it.comments).toImmutableList()) }
    }

    private fun updateComment(commentId: String, change: (Comment) -> Comment) {
        updateComments { comments -> comments.map { if (it.id == commentId) change(it) else it } }
    }

    @Immutable
    data class State(
        /** Oldest first. */
        val comments: ImmutableList<Comment> = persistentListOf(),
        /** More (newer) comments wait on the server. */
        val nextCursor: String? = null,
        val isLoading: Boolean = true,
        val draft: String = "",
        /** The draft is waiting for the server. */
        val isSending: Boolean = false,
        /** Waiting for the user to confirm. */
        val commentToDelete: Comment? = null,
        /** The comment the report dialog is about. */
        val reporting: Comment? = null,
        val isReporting: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        data class OnDraftChanged(val text: String) : Action
        data object OnSend : Action
        data object OnLoadMore : Action
        data class OnToggleLike(val comment: Comment) : Action
        /** Asks first; see [OnConfirmDelete]. */
        data class OnDelete(val comment: Comment) : Action
        data object OnConfirmDelete : Action
        data object OnDismissDelete : Action
        data class OnReport(val comment: Comment) : Action
        data class OnSendReport(val reason: ReportReason, val details: String) : Action
        data object OnDismissReport : Action
        data object OnDismissMessage : Action
    }
}

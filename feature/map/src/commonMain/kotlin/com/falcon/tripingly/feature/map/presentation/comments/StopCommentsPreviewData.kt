package com.falcon.tripingly.feature.map.presentation.comments

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_send_comment
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel.State
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel.Thread
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

private val anna = UserSummary("u1", "anna", "Anna")
private val jonas = UserSummary("u2", "jonas.k", "Jonas Krüger")

private val previewComments = persistentListOf(
    Comment(
        id = "c1",
        markerId = "s1",
        body = "Go early in the morning, the queue is much shorter.",
        author = anna,
        likeCount = 4,
        likedByMe = true,
        canDelete = false,
        createdAt = Instant.parse("2026-10-20T08:00:00Z"),
        replyCount = 2,
    ),
    Comment(
        id = "c2",
        markerId = "s1",
        body = "The view from the second floor is better than from the top. Book the tickets online a few days before, they sell out in summer.",
        author = jonas,
        likeCount = 0,
        likedByMe = false,
        canDelete = true,
        createdAt = Instant.parse("2026-10-22T18:30:00Z"),
    ),
)

private val previewReplies = persistentListOf(
    Comment(
        id = "r1",
        markerId = "s1",
        body = "@anna how early? We land at nine.",
        author = jonas,
        likeCount = 0,
        likedByMe = false,
        canDelete = true,
        createdAt = Instant.parse("2026-10-20T09:15:00Z"),
        parentId = "c1",
        mentions = listOf("anna"),
    ),
    Comment(
        id = "r2",
        markerId = "s1",
        body = "@jonas.k before ten is fine.",
        author = anna,
        likeCount = 1,
        likedByMe = false,
        canDelete = false,
        createdAt = Instant.parse("2026-10-20T10:02:00Z"),
        parentId = "c1",
        mentions = listOf("jonas.k"),
    ),
)

/** Comments with an open thread, loading, no comments yet, an error while sending, and replying with suggestions. */
internal class StopCommentsPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(comments = previewComments, threads = persistentMapOf("c1" to Thread(replies = previewReplies, isOpen = true)), isLoading = false),
        State(),
        State(isLoading = false),
        State(comments = previewComments, isLoading = false, isSending = true, message = UiText.Resource(Res.string.map_error_send_comment)),
        State(
            comments = previewComments,
            isLoading = false,
            replyingTo = previewComments.first(),
            suggestions = persistentListOf(anna, UserSummary("u3", "anja", "Anja Weiss")),
        ),
    )
}

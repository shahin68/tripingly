package com.falcon.tripingly.feature.map.presentation.comments

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_send_comment
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel.State
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

private val previewComments = persistentListOf(
    Comment(
        id = "c1",
        markerId = "s1",
        body = "Go early in the morning, the queue is much shorter.",
        author = UserSummary("u1", "anna", "Anna"),
        likeCount = 4,
        likedByMe = true,
        canDelete = false,
        createdAt = Instant.parse("2026-10-20T08:00:00Z"),
    ),
    Comment(
        id = "c2",
        markerId = "s1",
        body = "The view from the second floor is better than from the top. Book the tickets online a few days before, they sell out in summer.",
        author = UserSummary("u2", "jonas.k", "Jonas Krüger"),
        likeCount = 0,
        likedByMe = false,
        canDelete = true,
        createdAt = Instant.parse("2026-10-22T18:30:00Z"),
    ),
)

/** Comments, loading, no comments yet, and an error while sending. */
internal class StopCommentsPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(comments = previewComments, isLoading = false),
        State(),
        State(isLoading = false),
        State(comments = previewComments, isLoading = false, isSending = true, message = UiText.Resource(Res.string.map_error_send_comment)),
    )
}

package com.falcon.tripingly.core.data.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.result.onError
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.network.model.CreateCommentDto
import com.falcon.tripingly.core.network.model.CreateReportDto
import com.falcon.tripingly.core.network.newIdempotencyKey
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import com.falcon.tripingly.core.network.social.SocialApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class DefaultSocialRepository(
    private val api: SocialApi,
    private val local: TripLocalDataSource,
    realtime: RealtimeClient,
    /** The signed-in user's id. */
    private val currentUserId: () -> String?,
) : SocialRepository {

    override suspend fun explore(filter: ExploreFilter, cursor: String?): AppResult<Page<PublicTrip>, DataError.Network> =
        api.explore(cursor, EXPLORE_PAGE_SIZE, filter.period.name.lowercase(), filter.near?.lat, filter.near?.lng)
            .map { page -> Page(page.items.map { it.toPublicTrip() }, page.nextCursor) }

    override suspend fun comments(markerId: String, cursor: String?): AppResult<Page<Comment>, DataError.Network> =
        api.comments(markerId, cursor, COMMENTS_PAGE_SIZE).map { page -> Page(page.items.map { it.toComment() }, page.nextCursor) }

    override suspend fun addComment(markerId: String, body: String): AppResult<Comment, DataError.Network> =
        api.addComment(markerId, CreateCommentDto(body.trim()), newIdempotencyKey())
            .map { it.toComment() }
            .onSuccess { local.addToCommentCount(markerId, 1) }

    override suspend fun deleteComment(comment: Comment): AppResult<Unit, DataError.Network> =
        api.deleteComment(comment.id).onSuccess { local.addToCommentCount(comment.markerId, -1) }

    override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> {
        // A stop's heart changes at once in the cached trip; a refusal puts it back.
        val before = if (target == LikeTarget.Marker) local.marker(targetId) else null
        if (before != null && before.likedByMe != liked) {
            local.saveMarkerLike(targetId, (before.likeCount + if (liked) 1 else -1).coerceAtLeast(0), liked)
        }
        val result = if (liked) api.like(target.wireName, targetId) else api.unlike(target.wireName, targetId)
        return result.map { it.toLikeState() }
            .onSuccess { if (target == LikeTarget.Marker) local.saveMarkerLike(targetId, it.likeCount, it.liked) }
            .onError { if (before != null) local.saveMarkerLike(targetId, before.likeCount, before.likedByMe) }
    }

    override suspend fun report(
        target: ReportTarget,
        targetId: String,
        reason: ReportReason,
        details: String?,
    ): AppResult<Unit, DataError.Network> =
        api.report(
            CreateReportDto(
                targetType = target.toDto(),
                targetId = targetId,
                reason = reason.toDto(),
                details = details?.trim()?.takeIf { it.isNotEmpty() },
            ),
        ).map { }

    // My own comments are already shown from the server's answer.
    override val commentChanges: Flow<CommentChange> = realtime.events.mapNotNull { event ->
        when (event) {
            is RealtimeEvent.CommentAdded -> if (event.byMe()) null else CommentChange.Added(event.comment.toComment())
            is RealtimeEvent.CommentDeleted -> if (event.byMe()) null else CommentChange.Deleted(event.markerId, event.commentId)
            else -> null
        }
    }

    override val likeCounts: Flow<LikeCount> = realtime.events.mapNotNull { event ->
        if (event !is RealtimeEvent.LikeCountChanged) return@mapNotNull null
        val target = LikeTarget.entries.firstOrNull { it.wireName == event.targetType } ?: return@mapNotNull null
        LikeCount(target, event.targetId, event.count)
    }

    private fun RealtimeEvent.InTrip.byMe() = actorId != null && actorId == currentUserId()

    private companion object {
        const val EXPLORE_PAGE_SIZE = 20
        // The API's largest page.
        const val COMMENTS_PAGE_SIZE = 50
    }
}

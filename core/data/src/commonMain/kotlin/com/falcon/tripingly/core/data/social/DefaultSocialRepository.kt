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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Per item, while taps on its heart are being sent: see [sendLatestLike]. */
    private class LikeSync(var wanted: Boolean) {
        val lock = Mutex()
        var callers = 0
        var confirmed: LikeState? = null
    }

    private val likeSyncs = mutableMapOf<String, LikeSync>()
    private val likeSyncsLock = Mutex()

    override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> {
        // A place's heart changes at once on its stops in the cached trips; a refusal puts it back.
        val before = if (target == LikeTarget.Place) local.markerAtPlace(targetId) else null
        if (before != null && before.likedByMe != liked) {
            local.savePlaceLike(targetId, (before.likeCount + if (liked) 1 else -1).coerceAtLeast(0), liked)
        }
        val key = "${target.wireName}:$targetId"
        val sync = likeSyncsLock.withLock {
            likeSyncs.getOrPut(key) { LikeSync(liked) }.also { it.wanted = liked; it.callers++ }
        }
        val result = try {
            sync.lock.withLock { sendLatestLike(target, targetId, sync) }
        } finally {
            // The burst of taps is over: the next tap asks the server again (another device may have changed it).
            likeSyncsLock.withLock { if (--sync.callers == 0) likeSyncs.remove(key) }
        }
        return result
            .onSuccess { if (target == LikeTarget.Place) local.savePlaceLike(targetId, it.likeCount, it.liked) }
            .onError { if (before != null) local.savePlaceLike(targetId, before.likeCount, before.likedByMe) }
    }

    /**
     * The last tap wins: one request at a time per item. Taps meanwhile only change what's wanted; when an
     * answer no longer matches it, one more request follows. Every tap of the burst gets the final state, so
     * an older answer never puts an old heart back.
     */
    private suspend fun sendLatestLike(target: LikeTarget, targetId: String, sync: LikeSync): AppResult<LikeState, DataError.Network> {
        while (true) {
            val confirmed = sync.confirmed
            if (confirmed != null && confirmed.liked == sync.wanted) return AppResult.Success(confirmed)
            val wanted = sync.wanted
            when (val result = (if (wanted) api.like(target.wireName, targetId) else api.unlike(target.wireName, targetId)).map { it.toLikeState() }) {
                is AppResult.Success -> sync.confirmed = result.data
                is AppResult.Error -> {
                    sync.confirmed = null
                    return result
                }
            }
        }
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

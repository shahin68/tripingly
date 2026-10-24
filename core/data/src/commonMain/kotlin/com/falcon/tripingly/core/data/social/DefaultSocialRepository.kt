package com.falcon.tripingly.core.data.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.HotState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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

    /** Per item, while taps on its heart or flame are being sent: see [sendLatest]. */
    private class Sync(var wanted: Boolean) {
        val lock = Mutex()
        val callers = MutableStateFlow(0)
        var confirmed: Toggle? = null
        var failure: AppResult.Error<DataError.Network>? = null
    }

    /** A like or a hot mark as the server counted it. */
    private data class Toggle(val on: Boolean, val count: Int)

    private val syncs = mutableMapOf<String, Sync>()
    private val syncsLock = Mutex()

    override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> =
        setLatest("${target.wireName}:$targetId", liked) { on ->
            (if (on) api.like(target.wireName, targetId) else api.unlike(target.wireName, targetId)).map { Toggle(it.liked, it.likeCount.toInt()) }
        }.map { LikeState(it.on, it.count) }

    /** Per place, while taps on its flame are on their way: its stops' flame and count before the first tap. */
    private val hotBeforeTaps = mutableMapOf<String, HotState>()
    private val hotLock = Mutex()

    override suspend fun setHot(placeId: String, hot: Boolean): AppResult<HotState, DataError.Network> {
        // A place's flame changes at once on its stops in the cached trips and stays; the server's answer doesn't
        // move it, and a refusal puts back what it was before the taps.
        hotLock.withLock {
            val shown = local.markerAtPlace(placeId) ?: return@withLock
            hotBeforeTaps.getOrPut(placeId) { HotState(shown.hotByMe, shown.hotCount) }
            if (shown.hotByMe != hot) local.savePlaceHot(placeId, (shown.hotCount + if (hot) 1 else -1).coerceAtLeast(0), hot)
        }
        val result = setLatest("place:$placeId", hot) { on ->
            (if (on) api.markHot(placeId) else api.unmarkHot(placeId)).map { Toggle(it.hot, it.hotCount.toInt()) }
        }
        hotLock.withLock {
            // Every tap of a burst gets the same answer; the first one back settles it.
            val before = hotBeforeTaps.remove(placeId)
            if (before != null && result is AppResult.Error) local.savePlaceHot(placeId, before.hotCount, before.hot)
        }
        return result.map { HotState(it.on, it.count) }
    }

    private suspend fun setLatest(
        key: String,
        on: Boolean,
        send: suspend (on: Boolean) -> AppResult<Toggle, DataError.Network>,
    ): AppResult<Toggle, DataError.Network> {
        val sync = syncsLock.withLock {
            syncs.getOrPut(key) { Sync(on) }.also { it.wanted = on; it.callers.update { n -> n + 1 } }
        }
        return try {
            var isSender = false
            val result = sync.lock.withLock {
                sync.failure ?: sendLatest(sync, send).also { isSender = true }
            }
            // A failed burst: the tap that sent it answers last, so its "before" (the state before the burst) is
            // what callers put back, not the state of some tap in between.
            if (isSender && result is AppResult.Error) sync.callers.first { it == 1 }
            result
        } finally {
            // The burst of taps is over: the next tap asks the server again (another device may have changed it).
            syncsLock.withLock { if (sync.callers.updateAndGet { it - 1 } == 0) syncs.remove(key) }
        }
    }

    /**
     * The last tap wins: one request at a time per item, all sent by the burst's first tap. Taps meanwhile
     * only change what's wanted; when an answer no longer matches it, one more request follows. Every tap of
     * the burst gets the final state, so an older answer never puts an old state back. A failure ends the
     * burst: every tap of it gets that failure.
     */
    private suspend fun sendLatest(
        sync: Sync,
        send: suspend (on: Boolean) -> AppResult<Toggle, DataError.Network>,
    ): AppResult<Toggle, DataError.Network> {
        while (true) {
            val confirmed = sync.confirmed
            if (confirmed != null && confirmed.on == sync.wanted) return AppResult.Success(confirmed)
            when (val result = send(sync.wanted)) {
                is AppResult.Success -> sync.confirmed = result.data
                is AppResult.Error -> {
                    sync.failure = result
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

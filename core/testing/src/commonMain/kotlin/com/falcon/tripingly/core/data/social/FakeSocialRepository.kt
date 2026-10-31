package com.falcon.tripingly.core.data.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.HotState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Clock

/** For tests: comments and likes in memory, nothing to explore. */
class FakeSocialRepository(
    private val me: UserSummary = UserSummary("00000000-0000-4000-8000-000000000001", "me", "Me"),
) : SocialRepository {

    private val comments = mutableListOf<Comment>()
    private val likes = mutableMapOf<Pair<LikeTarget, String>, Int>()
    private val liked = mutableSetOf<Pair<LikeTarget, String>>()
    private val hotCounts = mutableMapOf<String, Int>()
    private val hotPlaces = mutableSetOf<String>()
    private var nextId = 0

    override suspend fun explore(filter: ExploreFilter, cursor: String?): AppResult<Page<PublicTrip>, DataError.Network> =
        AppResult.Success(Page(emptyList(), nextCursor = null))

    override suspend fun comments(markerId: String, cursor: String?): AppResult<Page<Comment>, DataError.Network> =
        AppResult.Success(Page(comments.filter { it.markerId == markerId }, nextCursor = null))

    override suspend fun addComment(markerId: String, body: String): AppResult<Comment, DataError.Network> {
        val comment = Comment(
            id = "fake-comment-${nextId++}",
            markerId = markerId,
            body = body.trim(),
            author = me,
            likeCount = 0,
            likedByMe = false,
            canDelete = true,
            createdAt = Clock.System.now(),
        )
        comments += comment
        return AppResult.Success(comment)
    }

    override suspend fun deleteComment(comment: Comment): AppResult<Unit, DataError.Network> {
        comments.removeAll { it.id == comment.id }
        return AppResult.Success(Unit)
    }

    override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> {
        val key = target to targetId
        val changed = if (liked) this.liked.add(key) else this.liked.remove(key)
        if (changed) likes[key] = (likes[key] ?: 0) + if (liked) 1 else -1
        return AppResult.Success(LikeState(liked, likes[key] ?: 0)).also { _myLikes.emit(LikeChange(target, targetId, it.data)) }
    }

    override suspend fun setHot(placeId: String, hot: Boolean): AppResult<HotState, DataError.Network> {
        val changed = if (hot) hotPlaces.add(placeId) else hotPlaces.remove(placeId)
        if (changed) hotCounts[placeId] = (hotCounts[placeId] ?: 0) + if (hot) 1 else -1
        return AppResult.Success(HotState(hot, hotCounts[placeId] ?: 0)).also { _hotChanges.emit(HotChange(placeId, hot, it.data.hotCount)) }
    }

    override suspend fun report(
        target: ReportTarget,
        targetId: String,
        reason: ReportReason,
        details: String?,
    ): AppResult<Unit, DataError.Network> = AppResult.Success(Unit)

    override val commentChanges: Flow<CommentChange> = emptyFlow()

    override val likeCounts: Flow<LikeCount> = emptyFlow()

    private val _myLikes = MutableSharedFlow<LikeChange>(extraBufferCapacity = 16)
    override val myLikes: Flow<LikeChange> = _myLikes.asSharedFlow()

    private val _hotChanges = MutableSharedFlow<HotChange>(extraBufferCapacity = 16)
    override val hotChanges: Flow<HotChange> = _hotChanges.asSharedFlow()
}

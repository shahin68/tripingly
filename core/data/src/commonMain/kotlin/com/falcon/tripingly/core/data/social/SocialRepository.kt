package com.falcon.tripingly.core.data.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import kotlinx.coroutines.flow.Flow

/** Explore, comments, likes and reports. Everything waits for the server; nothing here is cached. */
interface SocialRepository {
    /** Public trips from others, most popular first; pass the last page's cursor for more. */
    suspend fun explore(cursor: String?): AppResult<Page<PublicTrip>, DataError.Network>

    /** A stop's comments, oldest first. */
    suspend fun comments(markerId: String, cursor: String?): AppResult<Page<Comment>, DataError.Network>

    /** Counts the comment in the cached stop too. */
    suspend fun addComment(markerId: String, body: String): AppResult<Comment, DataError.Network>

    suspend fun deleteComment(comment: Comment): AppResult<Unit, DataError.Network>

    /** Likes or unlikes; a stop's heart and count change at once in the cached trip, and go back on a refusal. */
    suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network>

    suspend fun report(
        target: ReportTarget,
        targetId: String,
        reason: ReportReason,
        details: String?,
    ): AppResult<Unit, DataError.Network>

    /** Comments others add or delete in the trips being watched live (the open trip map). */
    val commentChanges: Flow<CommentChange>

    /** New like counts in the trips being watched live; whether I like something stays as it was. */
    val likeCounts: Flow<LikeCount>

    companion object {
        /** The server's longest comment. */
        const val MAX_COMMENT_LENGTH = 1000

        /** The server's longest report details. */
        const val MAX_REPORT_DETAILS_LENGTH = 1000
    }
}

data class LikeCount(val target: LikeTarget, val targetId: String, val count: Int)

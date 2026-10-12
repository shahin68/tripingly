package com.falcon.tripingly.feature.map.presentation.comments

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.LikeCount
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StopCommentsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val live = MutableSharedFlow<CommentChange>()
    private val liveLikes = MutableSharedFlow<LikeCount>()
    private var likeRefused = false

    private val social = object : SocialRepository by FakeSocialRepository() {
        override val commentChanges: Flow<CommentChange> = live
        override val likeCounts: Flow<LikeCount> = liveLikes
        override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> =
            if (likeRefused) AppResult.Error(DataError.Network.NoInternet) else AppResult.Success(LikeState(liked, if (liked) 1 else 0))
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun comment(id: String) = Comment(
        id = id,
        markerId = MARKER_ID,
        body = "Lovely",
        author = UserSummary("u2", "anna", "Anna"),
        likeCount = 0,
        likedByMe = false,
        canDelete = false,
        createdAt = Instant.parse("2026-10-11T10:00:00Z"),
    )

    @Test
    fun `a sent comment shows at the end and clears the field`() = runTest(testDispatcher) {
        val viewModel = StopCommentsViewModel(MARKER_ID, social)
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnDraftChanged("  Great view  "))
        viewModel.onAction(Action.OnSend)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Great view", state.comments.single().body)
        assertEquals("", state.draft)
    }

    @Test
    fun `comments from others come and go live`() = runTest(testDispatcher) {
        val viewModel = StopCommentsViewModel(MARKER_ID, social)
        testScheduler.advanceUntilIdle()

        live.emit(CommentChange.Added(comment("c1")))
        live.emit(CommentChange.Added(comment("c2").copy(markerId = "other-stop")))
        liveLikes.emit(LikeCount(LikeTarget.Comment, "c1", 3))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("c1"), viewModel.uiState.value.comments.map { it.id })
        assertEquals(3, viewModel.uiState.value.comments.single().likeCount)

        live.emit(CommentChange.Deleted(MARKER_ID, "c1"))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.comments.isEmpty())
    }

    @Test
    fun `a refused like goes back`() = runTest(testDispatcher) {
        likeRefused = true
        val viewModel = StopCommentsViewModel(MARKER_ID, social)
        testScheduler.advanceUntilIdle()
        live.emit(CommentChange.Added(comment("c1")))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnToggleLike(viewModel.uiState.value.comments.single()))
        assertTrue(viewModel.uiState.value.comments.single().likedByMe)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.comments.single().likedByMe)
        assertTrue(viewModel.uiState.value.message != null)
    }

    private companion object {
        const val MARKER_ID = "m1"
    }
}

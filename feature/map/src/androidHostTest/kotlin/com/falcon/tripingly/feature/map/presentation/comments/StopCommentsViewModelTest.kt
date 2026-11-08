package com.falcon.tripingly.feature.map.presentation.comments

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.LikeCount
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.model.account.ProfileUpdate
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
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StopCommentsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val live = MutableSharedFlow<CommentChange>()
    private val liveLikes = MutableSharedFlow<LikeCount>()
    private var likeRefused = false

    private val social = object : SocialRepository by FakeSocialRepository() {
        override fun commentChanges(markerId: String): Flow<CommentChange> = live
        override val likeCounts: Flow<LikeCount> = liveLikes
        override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> =
            if (likeRefused) AppResult.Error(DataError.Network.NoInternet) else AppResult.Success(LikeState(liked, if (liked) 1 else 0))
    }

    private val people = listOf(UserSummary("u2", "anna", "Anna Berg"), UserSummary("u3", "jonas.k", "Jonas Krüger"))

    private val account = FakeAccountBackend(today = { LocalDate(2026, 11, 7) })

    /** A view model whose sheet is on screen, as the sheet starts it. */
    private fun viewModel() = StopCommentsViewModel(MARKER_ID, people, social, account).also { it.onAction(Action.OnShown) }

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
    fun `a sent comment shows and clears the field`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.draft.setTextAndPlaceCursorAtEnd("  Great view  ")
        viewModel.onAction(Action.OnSend)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Great view", state.comments.single().body)
        assertEquals("", viewModel.draft.text.toString())
        // The list scrolls down to it.
        assertEquals(state.comments.single().id, state.sentCommentId)
    }

    @Test
    fun `comments from others come and go live`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        live.emit(CommentChange.Added(comment("c1")))
        liveLikes.emit(LikeCount(LikeTarget.Comment, "c1", 3))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("c1"), viewModel.uiState.value.comments.map { it.id })
        assertEquals(3, viewModel.uiState.value.comments.single().likeCount)

        live.emit(CommentChange.Deleted(MARKER_ID, "c1"))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.comments.isEmpty())
    }

    @Test
    fun `the stop is watched only while the sheet is on screen`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(1, live.subscriptionCount.value)

        viewModel.onAction(Action.OnHidden)
        testScheduler.advanceUntilIdle()
        // Collecting is what sends `comments.watch`: stopping it unwatches the stop, so its pushes come again.
        assertEquals(0, live.subscriptionCount.value)

        viewModel.onAction(Action.OnShown)
        testScheduler.advanceUntilIdle()
        assertEquals(1, live.subscriptionCount.value)
    }

    @Test
    fun `a refused like goes back`() = runTest(testDispatcher) {
        likeRefused = true
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        live.emit(CommentChange.Added(comment("c1")))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnToggleLike(viewModel.uiState.value.comments.single()))
        assertTrue(viewModel.uiState.value.comments.single().likedByMe)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.comments.single().likedByMe)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun `a new main comment goes after the liked ones`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        live.emit(CommentChange.Added(comment("liked").copy(likeCount = 2)))
        live.emit(CommentChange.Added(comment("old")))
        live.emit(CommentChange.Added(comment("new")))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("liked", "new", "old"), viewModel.uiState.value.comments.map { it.id })
    }

    @Test
    fun `a reply goes under its main comment and opens the thread`() = runTest(testDispatcher) {
        // The first fake account is the fake social repository's "me".
        account.signInForDevelopment("me", "Me")
        account.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
        account.accept((account.legalDocuments("en") as AppResult.Success).data)
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.draft.setTextAndPlaceCursorAtEnd("First")
        viewModel.onAction(Action.OnSend)
        testScheduler.advanceUntilIdle()
        val main = viewModel.uiState.value.comments.single()

        viewModel.onAction(Action.OnReply(main))
        // My own comment: no handle to start with.
        assertEquals("", viewModel.draft.text.toString())
        viewModel.draft.setTextAndPlaceCursorAtEnd("Second")
        viewModel.onAction(Action.OnSend)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(main.id), state.comments.map { it.id })
        assertEquals(1, state.comments.single().replyCount)
        val thread = state.threads.getValue(main.id)
        assertTrue(thread.isOpen)
        assertEquals(listOf("Second"), thread.replies.map { it.body })
        assertNull(state.replyingTo)
        assertEquals(thread.replies.single().id, state.sentCommentId)
    }

    @Test
    fun `replying to someone starts with their handle`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        live.emit(CommentChange.Added(comment("c1")))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnReply(viewModel.uiState.value.comments.single()))

        assertEquals("@anna ", viewModel.draft.text.toString())
        assertEquals("c1", viewModel.uiState.value.replyingTo?.id)
        viewModel.onAction(Action.OnCancelReply)
        assertNull(viewModel.uiState.value.replyingTo)
    }

    @Test
    fun `deleting a main comment takes its replies`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        live.emit(CommentChange.Added(comment("c1")))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnToggleReplies(viewModel.uiState.value.comments.single()))
        testScheduler.advanceUntilIdle()
        live.emit(CommentChange.Added(comment("r1").copy(parentId = "c1")))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("r1"), viewModel.uiState.value.threads.getValue("c1").replies.map { it.id })

        live.emit(CommentChange.Deleted(MARKER_ID, "c1"))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.comments.isEmpty())
        assertTrue(viewModel.uiState.value.threads.isEmpty())
    }

    @Test
    fun `typing an at sign suggests the trip's people and picking one fills the handle`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.draft.setTextAndPlaceCursorAtEnd("Ask @jo")
        Snapshot.sendApplyNotifications()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("jonas.k"), viewModel.uiState.value.suggestions.map { it.username })

        viewModel.onAction(Action.OnMention(people[1]))
        Snapshot.sendApplyNotifications()
        testScheduler.advanceUntilIdle()
        assertEquals("Ask @jonas.k ", viewModel.draft.text.toString())
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `a mention is only typed after a space or at the start`() {
        assertEquals(MentionQuery(0, 3, "an"), mentionQuery("@an", 3))
        assertEquals(MentionQuery(4, 6, "a"), mentionQuery("Hi, @a", 6))
        assertEquals(MentionQuery(3, 4, ""), mentionQuery("Hi @", 4))
        assertNull(mentionQuery("mail anna@web", 13))
        assertNull(mentionQuery("@anna is here", 13))
        assertNull(mentionQuery("@@a", 3))
    }

    private companion object {
        const val MARKER_ID = "m1"
    }
}

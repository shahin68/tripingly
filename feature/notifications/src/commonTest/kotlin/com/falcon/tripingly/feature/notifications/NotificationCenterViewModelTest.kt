package com.falcon.tripingly.feature.notifications

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.push.CenterChange
import com.falcon.tripingly.core.data.push.FakeNotificationCenterRepository
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCenterViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(id: String, minute: Int = 0, actor: UserSummary? = null) = NotificationEntry(
        id = id,
        actor = actor,
        title = "Entry $id",
        body = "",
        deepLink = "tripinly://notifications",
        isRead = false,
        at = Instant.parse("2026-11-07T10:00:00Z") + minute.minutes,
    )

    @Test
    fun `pages load newest first without repeats`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(
            mapOf(
                null to Page(listOf(entry("n3"), entry("n2")), nextCursor = "c1"),
                "c1" to Page(listOf(entry("n2"), entry("n1")), nextCursor = null),
            ),
        )
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()
        viewModel.onAction(Action.OnLoadMore)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("n3", "n2", "n1"), state.entries.map { it.id })
        assertTrue(state.hasLoaded)
        // Nothing more to load: another call asks for nothing.
        viewModel.onAction(Action.OnLoadMore)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `being shown marks it seen and arrivals come in on top`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n2"), entry("n1")), nextCursor = null)))
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()

        viewModel.onAction(Action.OnShown)
        advanceUntilIdle()
        assertEquals(1, center.seenCount)

        // A like on the same item again: its entry moves up, and being shown, it counts as seen at once.
        center.arrive(entry("n1", minute = 5).copy(title = "Anna and 1 other liked your photo"))
        advanceUntilIdle()
        assertEquals(listOf("n1", "n2"), viewModel.uiState.value.entries.map { it.id })
        assertEquals("Anna and 1 other liked your photo", viewModel.uiState.value.entries.first().title)
        assertFalse(center.hasUnseen.value)

        // Hidden, an arrival waits as unseen.
        viewModel.onAction(Action.OnHidden)
        center.arrive(entry("n4"))
        advanceUntilIdle()
        assertTrue(center.hasUnseen.value)
        assertEquals("n4", viewModel.uiState.value.entries.first().id)
    }

    @Test
    fun `opening an entry marks it read`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n1")), nextCursor = null)))
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()

        viewModel.onAction(Action.OnOpen(viewModel.uiState.value.entries.single()))

        assertTrue(viewModel.uiState.value.entries.single().isRead)
    }

    @Test
    fun `a failed load says why and retries`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n1")), nextCursor = null)))
        center.failure = DataError.Network.NoInternet
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.loadError)
        assertTrue(viewModel.uiState.value.entries.isEmpty())

        center.failure = null
        viewModel.onAction(Action.OnRetry)
        advanceUntilIdle()
        assertEquals(listOf("n1"), viewModel.uiState.value.entries.map { it.id })
        assertEquals(null, viewModel.uiState.value.loadError)
    }

    @Test
    fun `entries read or removed elsewhere change here too`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n3"), entry("n2"), entry("n1")), nextCursor = null)))
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()

        center.change(CenterChange.Read(setOf("n2")))
        center.change(CenterChange.Removed(setOf("n3")))
        advanceUntilIdle()
        assertEquals(listOf("n2" to true, "n1" to false), viewModel.uiState.value.entries.map { it.id to it.isRead })

        center.change(CenterChange.Cleared)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun `a removed entry goes at once and comes back in its place when the server refuses`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n3"), entry("n2"), entry("n1")), nextCursor = null)))
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()

        viewModel.onAction(Action.OnRemove(viewModel.uiState.value.entries[0]))
        assertEquals(listOf("n2", "n1"), viewModel.uiState.value.entries.map { it.id })
        advanceUntilIdle()
        assertEquals(listOf("n3"), center.removed)

        center.failure = DataError.Network.NoInternet
        viewModel.onAction(Action.OnRemove(viewModel.uiState.value.entries[1]))
        assertEquals(listOf("n2"), viewModel.uiState.value.entries.map { it.id })
        advanceUntilIdle()
        assertEquals(listOf("n2", "n1"), viewModel.uiState.value.entries.map { it.id })
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun `clearing all asks first and empties the center`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n2"), entry("n1")), nextCursor = null)))
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()

        viewModel.onAction(Action.OnClearAll)
        assertTrue(viewModel.uiState.value.isConfirmingClear)
        viewModel.onAction(Action.OnDismissConfirm)
        assertFalse(viewModel.uiState.value.isConfirmingClear)
        assertEquals(2, viewModel.uiState.value.entries.size)

        viewModel.onAction(Action.OnClearAll)
        viewModel.onAction(Action.OnConfirm)
        advanceUntilIdle()
        assertTrue(center.cleared)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun `a failed clear brings the entries back`() = runTest(dispatcher) {
        val center = FakeNotificationCenterRepository(mapOf(null to Page(listOf(entry("n2"), entry("n1")), nextCursor = null)))
        val viewModel = NotificationCenterViewModel(center)
        advanceUntilIdle()
        center.failure = DataError.Network.NoInternet

        viewModel.onAction(Action.OnClearAll)
        viewModel.onAction(Action.OnConfirm)
        advanceUntilIdle()
        assertEquals(listOf("n2", "n1"), viewModel.uiState.value.entries.map { it.id })
        assertNotNull(viewModel.uiState.value.message)
    }
}

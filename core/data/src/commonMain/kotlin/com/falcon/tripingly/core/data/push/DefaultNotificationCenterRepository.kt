package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.data.trips.toInstant
import com.falcon.tripingly.core.data.trips.toUser
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.core.network.push.NotificationEntryDto
import com.falcon.tripingly.core.network.push.PushApi
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Asks the server whether something unseen waits at start and after every reconnect; each arrival lights it up, and
 * `notifications.changed` (read, removed or seen anywhere) sets it to what the server counts.
 */
internal class DefaultNotificationCenterRepository(
    private val api: PushApi,
    private val realtime: RealtimeClient,
    private val scope: CoroutineScope,
) : NotificationCenterRepository {

    private val unseen = MutableStateFlow(false)
    override val hasUnseen: StateFlow<Boolean> = unseen.asStateFlow()

    private val created = realtime.events.filterIsInstance<RealtimeEvent.NotificationCreated>()
    override val arrivals: Flow<NotificationEntry> = created.mapNotNull { it.entry?.toEntry() }

    private val changed = realtime.events.filterIsInstance<RealtimeEvent.NotificationsChanged>()
    override val changes: Flow<CenterChange> = changed.mapNotNull {
        when (it.change) {
            RealtimeEvent.NotificationsChanged.Change.Read -> CenterChange.Read(it.ids.toSet())
            RealtimeEvent.NotificationsChanged.Change.Removed -> CenterChange.Removed(it.ids.toSet())
            RealtimeEvent.NotificationsChanged.Change.Cleared -> CenterChange.Cleared
            RealtimeEvent.NotificationsChanged.Change.Seen -> null
        }
    }

    private var listening: Job? = null

    override fun start() {
        if (listening != null) {
            scope.launch { refresh() }
            return
        }
        listening = scope.launch {
            launch { created.collect { unseen.value = true } }
            launch { changed.collect { unseen.value = it.unseenCount > 0 } }
            launch { refresh() }
            realtime.connections.collect { refresh() }
        }
    }

    override suspend fun entries(cursor: String?): AppResult<Page<NotificationEntry>, DataError.Network> =
        api.notifications(cursor, PAGE_SIZE).map { page -> Page(page.items.map { it.toEntry() }, page.nextCursor) }

    // Best effort: offline, the indicator comes back the next time the app asks.
    override suspend fun markSeen() {
        unseen.value = false
        api.markCenterSeen()
    }

    override suspend fun remove(id: String): AppResult<Unit, DataError.Network> = api.removeNotification(id)

    override suspend fun clear(): AppResult<Unit, DataError.Network> = api.clearNotifications()

    /** Whoever signs in next starts from what the server says. */
    fun forget() {
        unseen.value = false
    }

    private suspend fun refresh() {
        (api.notifications(cursor = null, limit = 1) as? AppResult.Success)?.let { unseen.value = it.data.unseenCount > 0 }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

private fun NotificationEntryDto.toEntry() = NotificationEntry(
    id = id,
    actor = actor?.toUser(),
    actors = actors.map { it.toUser() },
    title = title,
    body = body,
    deepLink = deepLink,
    isRead = read,
    at = createdAt.toInstant(),
)

package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.notification.NotificationEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [pages] by cursor (null: the first); [arrive] and [change] send what the server would; [seenCount] counts the
 * center's opens. [failure] fails loads, removals and clearing.
 */
class FakeNotificationCenterRepository(
    var pages: Map<String?, Page<NotificationEntry>> = emptyMap(),
) : NotificationCenterRepository {
    private val unseen = MutableStateFlow(false)
    override val hasUnseen: StateFlow<Boolean> = unseen
    private val live = MutableSharedFlow<NotificationEntry>(extraBufferCapacity = 16)
    override val arrivals: Flow<NotificationEntry> = live
    private val changed = MutableSharedFlow<CenterChange>(extraBufferCapacity = 16)
    override val changes: Flow<CenterChange> = changed
    /** Entries removed through the center, in order; [cleared] once it was emptied. */
    val removed = mutableListOf<String>()
    var cleared = false
        private set
    var seenCount = 0
        private set

    /** Set to make [entries] fail. */
    var failure: DataError.Network? = null

    override fun start() = Unit

    override suspend fun entries(cursor: String?): AppResult<Page<NotificationEntry>, DataError.Network> =
        failure?.let { AppResult.Error(it) } ?: AppResult.Success(pages[cursor] ?: Page(emptyList(), nextCursor = null))

    override suspend fun markSeen() {
        seenCount++
        unseen.value = false
    }

    override suspend fun remove(id: String): AppResult<Unit, DataError.Network> =
        failure?.let { AppResult.Error(it) } ?: AppResult.Success(Unit).also { removed += id }

    override suspend fun clear(): AppResult<Unit, DataError.Network> =
        failure?.let { AppResult.Error(it) } ?: AppResult.Success(Unit).also { cleared = true }

    /** Something read or removed elsewhere, as the server tells it; [unseenLeft]: whether anything still waits. */
    fun change(change: CenterChange, unseenLeft: Boolean = false) {
        unseen.value = unseenLeft
        changed.tryEmit(change)
    }

    fun arrive(entry: NotificationEntry) {
        unseen.value = true
        live.tryEmit(entry)
    }
}

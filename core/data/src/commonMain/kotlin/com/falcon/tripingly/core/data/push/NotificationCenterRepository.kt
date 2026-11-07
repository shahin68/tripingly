package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.notification.NotificationEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The notification center: everything the server told me, newest first, and whether something new waits there. */
interface NotificationCenterRepository {
    /**
     * Entries unread and new or back on top since the center was last opened: the red indicator on Profile and the
     * bell. Reading them anywhere (a stop, its comments, another device) turns it off.
     */
    val hasUnseen: StateFlow<Boolean>

    /** Entries arriving, or coming back on top, while the app runs. */
    val arrivals: Flow<NotificationEntry>

    /** Entries read or removed anywhere: on another screen, by a stop's comments, on another device. */
    val changes: Flow<CenterChange>

    /** Once signed in: asks the server now, after every reconnect, and lights up with each arrival. */
    fun start()

    suspend fun entries(cursor: String?): AppResult<Page<NotificationEntry>, DataError.Network>

    /** The center was opened, or showed an arrival: what's in it counts as seen. */
    suspend fun markSeen()

    /** Takes one entry out of the center; what it is about (a comment, a like) stays. */
    suspend fun remove(id: String): AppResult<Unit, DataError.Network>

    /** Empties the center. */
    suspend fun clear(): AppResult<Unit, DataError.Network>
}

/** What happened to entries somewhere else in the app or on another device. */
sealed interface CenterChange {
    data class Read(val ids: Set<String>) : CenterChange

    data class Removed(val ids: Set<String>) : CenterChange

    data object Cleared : CenterChange
}

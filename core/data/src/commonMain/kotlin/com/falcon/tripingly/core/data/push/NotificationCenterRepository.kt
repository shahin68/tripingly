package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.notification.NotificationEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The notification center: everything the server told me, newest first, and whether something new waits there. */
interface NotificationCenterRepository {
    /** Entries new or back on top since the center was last opened: the red indicator on Profile and the center's tab. */
    val hasUnseen: StateFlow<Boolean>

    /** Entries arriving, or coming back on top, while the app runs. */
    val arrivals: Flow<NotificationEntry>

    /** Once signed in: asks the server now, after every reconnect, and lights up with each arrival. */
    fun start()

    suspend fun entries(cursor: String?): AppResult<Page<NotificationEntry>, DataError.Network>

    /** The center was opened, or showed an arrival: what's in it counts as seen. */
    suspend fun markSeen()
}

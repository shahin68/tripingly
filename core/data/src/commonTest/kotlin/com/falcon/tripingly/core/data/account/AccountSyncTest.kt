package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSyncTest {

    private val backend = FakeAccountBackend(today = { LocalDate(2026, 11, 22) })
    private var refreshes = 0
    private val session = object : SessionRepository by backend {
        override suspend fun refreshAccount() = backend.refreshAccount().also { refreshes++ }
    }
    private val realtime = FakeRealtimeClient()

    @Test
    fun theAccountIsAskedForOnEachConnection_andWhenItChangedElsewhere() = runTest {
        val sync = AccountSync(session, realtime, backgroundScope)
        sync.start()
        sync.start()
        runCurrent()

        realtime.connect()
        runCurrent()
        assertEquals(1, refreshes)

        realtime.send(RealtimeEvent.MeChanged)
        runCurrent()
        assertEquals(2, refreshes)

        realtime.send(RealtimeEvent.MarkerDeleted("t1", "u2", "m1"))
        runCurrent()
        assertEquals(2, refreshes)
    }
}

package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorPaginatorTest {

    private val pages = mapOf(
        null to CursorPage(listOf(1, 2), "c2"),
        "c2" to CursorPage(listOf(3), null),
    )

    @Test
    fun loadNext_walksPagesUntilTheEnd() = runTest {
        val cursors = mutableListOf<String?>()
        val paginator = CursorPaginator { cursor ->
            cursors += cursor
            AppResult.Success(pages.getValue(cursor))
        }

        assertEquals(listOf(1, 2), (paginator.loadNext() as AppResult.Success).data.items)
        assertFalse(paginator.endReached)
        assertEquals(listOf(3), (paginator.loadNext() as AppResult.Success).data.items)
        assertTrue(paginator.endReached)
        assertEquals(emptyList(), (paginator.loadNext() as AppResult.Success).data.items)
        assertEquals(listOf(null, "c2"), cursors)
    }

    @Test
    fun failedPage_isRetriedWithTheSameCursor() = runTest {
        val cursors = mutableListOf<String?>()
        var fail = true
        val paginator = CursorPaginator { cursor ->
            cursors += cursor
            if (cursor == "c2" && fail) {
                fail = false
                AppResult.Error(DataError.Network.NoInternet)
            } else {
                AppResult.Success(pages.getValue(cursor))
            }
        }

        paginator.loadNext()
        assertEquals(AppResult.Error(DataError.Network.NoInternet), paginator.loadNext())
        assertEquals(listOf(3), (paginator.loadNext() as AppResult.Success).data.items)
        assertEquals(listOf(null, "c2", "c2"), cursors)
    }

    @Test
    fun reset_startsFromTheFirstPage() = runTest {
        val paginator = CursorPaginator<Int> { cursor -> AppResult.Success(pages.getValue(cursor)) }
        paginator.loadNext()
        paginator.loadNext()

        paginator.reset()

        assertFalse(paginator.endReached)
        assertEquals(listOf(1, 2), (paginator.loadNext() as AppResult.Success).data.items)
    }
}

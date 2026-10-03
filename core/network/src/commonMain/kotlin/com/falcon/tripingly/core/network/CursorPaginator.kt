package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One page of a cursor-paginated list (`{ items, nextCursor }`), already mapped. */
data class CursorPage<T>(val items: List<T>, val nextCursor: String?)

/**
 * Walks a cursor-paginated endpoint page by page. [loadNext] is a no-op once the
 * end is reached, and concurrent calls never load the same page twice.
 */
class CursorPaginator<T>(
    private val load: suspend (cursor: String?) -> AppResult<CursorPage<T>, DataError.Network>,
) {
    private val mutex = Mutex()
    private var cursor: String? = null
    private var started = false

    var endReached: Boolean = false
        private set

    /** The next page, or an empty success when there are no more pages. */
    suspend fun loadNext(): AppResult<CursorPage<T>, DataError.Network> = mutex.withLock {
        if (endReached) return@withLock AppResult.Success(CursorPage(emptyList(), null))
        val result = load(if (started) cursor else null)
        if (result is AppResult.Success) {
            started = true
            cursor = result.data.nextCursor
            endReached = cursor == null
        }
        result
    }

    suspend fun reset() = mutex.withLock {
        cursor = null
        started = false
        endReached = false
    }
}

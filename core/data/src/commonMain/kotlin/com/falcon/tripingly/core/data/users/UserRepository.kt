package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.trip.UserSummary

/** Other people: find them by name, and the signed-in user's friends. */
interface UserRepository {
    /**
     * Up to 20 people whose username or display name starts with [query] (a leading `@` is ignored).
     * The server needs two characters; a shorter query returns nothing without asking it.
     */
    suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network>

    /** The user's friends (`GET /me/friends`), the first [FRIENDS_PAGE_SIZE]. */
    suspend fun friends(): AppResult<List<UserSummary>, DataError.Network>

    companion object {
        /** The API's largest page. */
        const val FRIENDS_PAGE_SIZE = 50

        /** The API's shortest search query. */
        const val MIN_SEARCH_LENGTH = 2
    }
}

/** The query as the server expects it: trimmed, without a leading `@`. */
internal fun String.toUserQuery(): String = trim().removePrefix("@")

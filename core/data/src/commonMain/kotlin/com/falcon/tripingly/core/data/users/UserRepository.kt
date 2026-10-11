package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.trip.UserSummary

/** Other people: find them by name, their profiles, follows and blocks. */
interface UserRepository {
    /**
     * Up to 20 people whose username or display name starts with [query] (a leading `@` is ignored).
     * The server needs two characters; a shorter query returns nothing without asking it.
     */
    suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network>

    /** The user's friends (`GET /me/friends`), the first [FRIENDS_PAGE_SIZE]. */
    suspend fun friends(): AppResult<List<UserSummary>, DataError.Network>

    /** One of the signed-in user's people lists, a page at a time. */
    suspend fun people(list: PeopleList, cursor: String?): AppResult<Page<UserSummary>, DataError.Network>

    /**
     * A profile, with the first page of public trips; with a [cursor], the profile again with the next page.
     * Someone unknown, or with a block either way, is `NOT_FOUND`.
     */
    suspend fun profile(username: String, cursor: String? = null): AppResult<Profile, DataError.Network>

    suspend fun setFollowing(userId: String, following: Boolean): AppResult<Unit, DataError.Network>

    /** Blocking also ends follows both ways and takes each of us out of the other's trips. */
    suspend fun setBlocked(userId: String, blocked: Boolean): AppResult<Unit, DataError.Network>

    companion object {
        /** The API's largest page. */
        const val FRIENDS_PAGE_SIZE = 50

        /** The API's shortest search query. */
        const val MIN_SEARCH_LENGTH = 2
    }
}

/** The query as the server expects it: trimmed, without a leading `@`. */
internal fun String.toUserQuery(): String = trim().removePrefix("@")

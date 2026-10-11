package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.data.trips.toUser
import com.falcon.tripingly.core.data.users.UserRepository.Companion.FRIENDS_PAGE_SIZE
import com.falcon.tripingly.core.data.users.UserRepository.Companion.MIN_SEARCH_LENGTH
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.network.users.UsersApi

class DefaultUserRepository(private val api: UsersApi) : UserRepository {

    override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
        val trimmed = query.toUserQuery()
        if (trimmed.length < MIN_SEARCH_LENGTH) return AppResult.Success(emptyList())
        return api.search(trimmed).map { page -> page.items.map { it.toUser() } }
    }

    override suspend fun friends(): AppResult<List<UserSummary>, DataError.Network> =
        api.friends(cursor = null, limit = FRIENDS_PAGE_SIZE).map { page -> page.items.map { it.toUser() } }
}

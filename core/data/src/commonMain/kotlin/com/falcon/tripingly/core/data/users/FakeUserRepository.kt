package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository.Companion.MIN_SEARCH_LENGTH
import com.falcon.tripingly.core.model.trip.UserSummary

/** A few people for `-Ptripinly.useFakeApi=true` builds and tests; the first three are friends. */
class FakeUserRepository : UserRepository {

    private val people = listOf(
        UserSummary("00000000-0000-4000-8000-000000000101", "anna", "Anna"),
        UserSummary("00000000-0000-4000-8000-000000000102", "jonas.k", "Jonas"),
        UserSummary("00000000-0000-4000-8000-000000000103", "lena", "Lena"),
        UserSummary("00000000-0000-4000-8000-000000000104", "marton", "Márton"),
        UserSummary("00000000-0000-4000-8000-000000000105", "julia_t", "Júlia"),
    )

    override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
        val trimmed = query.toUserQuery()
        if (trimmed.length < MIN_SEARCH_LENGTH) return AppResult.Success(emptyList())
        return AppResult.Success(
            people.filter {
                it.username.startsWith(trimmed, ignoreCase = true) || it.displayName.startsWith(trimmed, ignoreCase = true)
            },
        )
    }

    override suspend fun friends(): AppResult<List<UserSummary>, DataError.Network> = AppResult.Success(people.take(3))
}

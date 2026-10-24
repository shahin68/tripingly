package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository.Companion.MIN_SEARCH_LENGTH
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow

/** A few people for tests; the first three are friends. */
class FakeUserRepository : UserRepository {

    private val people = listOf(
        UserSummary("00000000-0000-4000-8000-000000000101", "anna", "Anna"),
        UserSummary("00000000-0000-4000-8000-000000000102", "jonas.k", "Jonas"),
        UserSummary("00000000-0000-4000-8000-000000000103", "lena", "Lena"),
        UserSummary("00000000-0000-4000-8000-000000000104", "marton", "Márton"),
        UserSummary("00000000-0000-4000-8000-000000000105", "julia_t", "Júlia"),
    )

    /** I follow the first four; the first three and the last follow me. */
    private val following = people.take(4).map { it.id }.toMutableSet()
    private val followers = (people.take(3) + people.last()).map { it.id }.toMutableSet()
    private val blocked = mutableSetOf<String>()

    override val followChanges: Flow<FollowChange> = emptyFlow()

    private val _blocks = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val blocks: Flow<String> = _blocks.asSharedFlow()

    private fun visible() = people.filterNot { it.id in blocked }

    override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
        val trimmed = query.trim().removePrefix("@")
        if (trimmed.length < MIN_SEARCH_LENGTH) return AppResult.Success(emptyList())
        return AppResult.Success(
            visible().filter {
                it.username.startsWith(trimmed, ignoreCase = true) || it.displayName.startsWith(trimmed, ignoreCase = true)
            },
        )
    }

    override suspend fun friends(): AppResult<List<UserSummary>, DataError.Network> =
        AppResult.Success(visible().filter { it.id in following && it.id in followers })

    override suspend fun people(list: PeopleList, cursor: String?): AppResult<Page<UserSummary>, DataError.Network> {
        val items = when (list) {
            PeopleList.Friends -> visible().filter { it.id in following && it.id in followers }
            PeopleList.Following -> visible().filter { it.id in following }
            PeopleList.Followers -> visible().filter { it.id in followers }
        }
        return AppResult.Success(Page(items, nextCursor = null))
    }

    override suspend fun profile(username: String, cursor: String?): AppResult<Profile, DataError.Network> {
        val user = visible().firstOrNull { it.username == username.trim().removePrefix("@") }
            ?: return AppResult.Error(DataError.Network.Api(404, "NOT_FOUND", "Not found"))
        return AppResult.Success(
            Profile(
                user = user,
                followerCount = if (user.id in following) 1 else 0,
                followingCount = if (user.id in followers) 1 else 0,
                isFollowing = user.id in following,
                followsMe = user.id in followers,
                isFriend = user.id in following && user.id in followers,
                publicTripCount = 0,
                trips = emptyList(),
                nextCursor = null,
            ),
        )
    }

    override suspend fun setFollowing(userId: String, following: Boolean): AppResult<Unit, DataError.Network> {
        if (following) this.following += userId else this.following -= userId
        return AppResult.Success(Unit)
    }

    override suspend fun setBlocked(userId: String, blocked: Boolean): AppResult<Unit, DataError.Network> {
        if (blocked) {
            _blocks.emit(userId)
            this.blocked += userId
            following -= userId
            followers -= userId
        } else {
            this.blocked -= userId
        }
        return AppResult.Success(Unit)
    }
}

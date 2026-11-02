package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.social.toProfile
import com.falcon.tripingly.core.data.trips.toUser
import com.falcon.tripingly.core.data.users.UserRepository.Companion.FRIENDS_PAGE_SIZE
import com.falcon.tripingly.core.data.users.UserRepository.Companion.MIN_SEARCH_LENGTH
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import com.falcon.tripingly.core.network.users.UsersApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull

class DefaultUserRepository(private val api: UsersApi, realtime: RealtimeClient) : UserRepository {

    override val followChanges: Flow<FollowChange> = realtime.events.mapNotNull { event ->
        (event as? RealtimeEvent.FollowsChanged)?.let { FollowChange(it.followerId, it.followeeId, it.following) }
    }

    override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
        val trimmed = query.toUserQuery()
        if (trimmed.length < MIN_SEARCH_LENGTH) return AppResult.Success(emptyList())
        return api.search(trimmed).map { page -> page.items.map { it.toUser() } }
    }

    override suspend fun friends(): AppResult<List<UserSummary>, DataError.Network> =
        api.friends(cursor = null, limit = FRIENDS_PAGE_SIZE).map { page -> page.items.map { it.toUser() } }

    override suspend fun people(list: PeopleList, cursor: String?): AppResult<Page<UserSummary>, DataError.Network> {
        val page = when (list) {
            PeopleList.Friends -> api.friends(cursor, PEOPLE_PAGE_SIZE)
            PeopleList.Following -> api.following(cursor, PEOPLE_PAGE_SIZE)
            PeopleList.Followers -> api.followers(cursor, PEOPLE_PAGE_SIZE)
        }
        return page.map { Page(it.items.map { user -> user.toUser() }, it.nextCursor) }
    }

    override suspend fun blocked(cursor: String?): AppResult<Page<UserSummary>, DataError.Network> =
        api.blocked(cursor, PEOPLE_PAGE_SIZE).map { Page(it.items.map { user -> user.toUser() }, it.nextCursor) }

    override suspend fun profile(username: String, cursor: String?): AppResult<Profile, DataError.Network> =
        api.profile(username.toUserQuery(), cursor, PROFILE_TRIPS_PAGE_SIZE).map { it.toProfile() }

    override suspend fun setFollowing(userId: String, following: Boolean): AppResult<Unit, DataError.Network> =
        if (following) api.follow(userId) else api.unfollow(userId)

    private val _blocks = MutableSharedFlow<String>(extraBufferCapacity = BLOCKS_BUFFER)
    override val blocks: Flow<String> = _blocks.asSharedFlow()

    override suspend fun setBlocked(userId: String, blocked: Boolean): AppResult<Unit, DataError.Network> =
        (if (blocked) api.block(userId) else api.unblock(userId)).onSuccess { if (blocked) _blocks.emit(userId) }

    private companion object {
        const val PEOPLE_PAGE_SIZE = 50
        const val BLOCKS_BUFFER = 8
        const val PROFILE_TRIPS_PAGE_SIZE = 20
    }
}

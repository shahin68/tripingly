package com.falcon.tripingly.feature.social.presentation.nearby

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.social.Person
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_follow
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.PeopleState
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Nearby's people: friends, then follows, then people with trips around me, then the most followed (the server's
 * order), as faces in a row and all of them in a sheet, a page at a time. Follow and Following switch at once and
 * stay; only a refusal puts them back. The same goes for the people a search finds ([State.search]).
 */
internal class NearbyPeople(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<State>,
    private val userRepository: UserRepository,
    private val showMessage: (UiText) -> Unit,
) {
    /** The next page of the people shown in the sheet: around me, or found by the search. */
    private var cursor: String? = null
    private var searchCursor: String? = null
    private var point: GeoPoint? = null
    private var loadingMore: Job? = null
    private val followJobs = mutableMapOf<String, Job>()

    /** As the server last confirmed it: the button before the first tap of a burst. */
    private val confirmedFollowing = mutableMapOf<String, Boolean>()

    init {
        scope.launch {
            // Someone blocked here leaves the row, the sheet and the search at once.
            userRepository.blocks.collect { userId -> updatePeople { list -> list.filterNot { it.user.id == userId } } }
        }
        scope.launch {
            // Followed or unfollowed on their profile (or another device): the button shows it here too. A follow I
            // make has them as the one followed; a tap still on its way here wins.
            userRepository.followChanges.collect { change ->
                if (followJobs[change.followeeId]?.isActive != true) {
                    updatePerson(change.followeeId) { it.copy(isFollowing = change.following) }
                }
            }
        }
    }

    /** The people around [point]; kept in memory only, for the next pages. */
    fun load(point: GeoPoint) {
        this.point = point
        loadingMore?.cancel()
        scope.launch {
            val result = userRepository.findPeople(query = null, near = point)
            if (result is AppResult.Success) {
                cursor = result.data.nextCursor
                state.update { it.copy(people = PeopleState(result.data.people.toImmutableList(), result.data.total, hasLoaded = true)) }
            } else {
                // The row stops waiting: the people shown before stay, and with none it goes away.
                state.update { it.copy(people = it.people.copy(hasLoaded = true)) }
            }
        }
    }

    /** A search's people, its first page (from [NearbySearch]). */
    fun showFound(people: PeopleState, nextCursor: String?) {
        searchCursor = nextCursor
        state.update { it.copy(search = it.search.copy(people = people)) }
    }

    /** The sheet's next page, of the people it shows. */
    fun loadMore() {
        if (loadingMore?.isActive == true) return
        val search = state.value.search
        val isSearch = search.isSearching
        val next = (if (isSearch) searchCursor else cursor) ?: return
        loadingMore = scope.launch {
            val result = userRepository.findPeople(if (isSearch) search.query else null, point, next)
            if (result !is AppResult.Success) return@launch
            // A new query meanwhile has its own first page.
            if (isSearch && state.value.search.query != search.query) return@launch
            if (isSearch) searchCursor = result.data.nextCursor else cursor = result.data.nextCursor
            state.update { nearby ->
                val add: (PeopleState) -> PeopleState = { people ->
                    val known = people.people.map { it.user.id }.toSet()
                    people.copy(people = (people.people + result.data.people.filterNot { it.user.id in known }).toImmutableList())
                }
                if (isSearch) nearby.copy(search = nearby.search.copy(people = add(nearby.search.people))) else nearby.copy(people = add(nearby.people))
            }
        }
    }

    /** Follow or Following: shown at once and kept; a newer tap wins, and only a refusal puts it back. */
    fun toggleFollow(person: Person) {
        val userId = person.user.id
        if (followJobs[userId]?.isActive != true) confirmedFollowing[userId] = person.isFollowing
        followJobs[userId]?.cancel()
        val following = !person.isFollowing
        updatePerson(userId) { it.copy(isFollowing = following) }
        followJobs[userId] = scope.launch {
            val result = userRepository.setFollowing(userId, following)
            if (result is AppResult.Error) {
                val before = confirmedFollowing.getValue(userId)
                updatePerson(userId) { it.copy(isFollowing = before) }
                showMessage(result.error.toUiText(Res.string.social_error_follow))
            }
        }
    }

    private fun updatePerson(userId: String, change: (Person) -> Person) =
        updatePeople { list -> list.map { if (it.user.id == userId) change(it) else it } }

    private fun updatePeople(change: (List<Person>) -> List<Person>) = state.update { nearby ->
        fun PeopleState.changed(): PeopleState {
            val people = change(people)
            // A blocked person also leaves the count beside the faces.
            return copy(people = people.toImmutableList(), total = (total - (this.people.size - people.size)).coerceAtLeast(people.size))
        }
        nearby.copy(people = nearby.people.changed(), search = nearby.search.copy(people = nearby.search.people.changed()))
    }
}

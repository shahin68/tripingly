package com.falcon.tripingly.feature.social.presentation.search

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.social.Person
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_follow
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.PeopleState
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Search tab's people, only those a search finds (Shahin, 2026-11-24: no people around by default): friends, then follows,
 * then the rest (the server's order), as their top three rows above the spots and all of them in a sheet, a page at a time. Follow and
 * Following switch at once and stay; only a refusal puts them back.
 */
internal class FoundPeople(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<State>,
    private val userRepository: UserRepository,
    /** Where the Search tab looked, for the next pages; in memory only. */
    private val point: () -> GeoPoint?,
    private val showMessage: (UiText) -> Unit,
) {
    /** The next page of the people the search found. */
    private var cursor: String? = null
    private var loadingMore: Job? = null
    private val followJobs = mutableMapOf<String, Job>()

    /** As the server last confirmed it: the button before the first tap of a burst. */
    private val confirmedFollowing = mutableMapOf<String, Boolean>()

    init {
        scope.launch {
            // Someone blocked here leaves the row and the sheet at once.
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

    /** A search's people, its first page (from [TypedSearch]). */
    fun showFound(people: PeopleState, nextCursor: String?) {
        loadingMore?.cancel()
        cursor = nextCursor
        state.update { it.copy(search = it.search.copy(people = people)) }
    }

    /** The sheet's next page. */
    fun loadMore() {
        if (loadingMore?.isActive == true) return
        val search = state.value.search
        val next = cursor?.takeIf { search.isSearching } ?: return
        loadingMore = scope.launch {
            val result = userRepository.findPeople(search.query, point(), next)
            // A new query meanwhile has its own first page.
            if (result !is AppResult.Success || state.value.search.query != search.query) return@launch
            cursor = result.data.nextCursor
            state.update { nearby ->
                val people = nearby.search.people
                val known = people.people.map { it.user.id }.toSet()
                val more = people.copy(people = (people.people + result.data.people.filterNot { it.user.id in known }).toImmutableList())
                nearby.copy(search = nearby.search.copy(people = more))
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
        val found = nearby.search.people
        val people = change(found.people)
        // A blocked person also leaves the count in See all.
        val changed = found.copy(people = people.toImmutableList(), total = (found.total - (found.people.size - people.size)).coerceAtLeast(people.size))
        nearby.copy(search = nearby.search.copy(people = changed))
    }
}

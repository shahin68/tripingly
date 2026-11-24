package com.falcon.tripingly.feature.social.presentation.search

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_search
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.PeopleState
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.SearchState
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.State
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Search tab's search, from the shell's top bar: people and our spots named like the query, on one screen (Shahin,
 * 2026-11-23). It asks once typing pauses, from two characters, the latest query winning; near [point] when the Search tab
 * knows where I am (nearer first, with distances), else from anywhere (spots with their city). The point is used for
 * the request only.
 */
internal class TypedSearch(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<State>,
    private val userRepository: UserRepository,
    private val placeRepository: PlaceRepository,
    private val point: () -> GeoPoint?,
    private val people: FoundPeople,
    private val showMessage: (UiText) -> Unit,
) {
    private var search: Job? = null
    private var loadingMore: Job? = null
    private var spotsCursor: String? = null

    /** A short or cleared query clears the results at once. */
    fun onQuery(query: String) {
        if (query == state.value.search.query) return
        search?.cancel()
        loadingMore?.cancel()
        state.update { it.copy(search = it.search.copy(query = query)) }
        if (!state.value.search.isSearching) {
            state.update { it.copy(search = SearchState(query = query)) }
            return
        }
        search = scope.launch {
            delay(SEARCH_DEBOUNCE)
            find(query)
        }
    }

    /** A pull to refresh while searching: the same query again, at once. */
    fun refresh() {
        val query = state.value.search.query
        if (!state.value.search.isSearching) return
        search?.cancel()
        loadingMore?.cancel()
        search = scope.launch { find(query) }
    }

    private suspend fun find(query: String) = coroutineScope {
        state.update { it.copy(search = it.search.copy(isLoading = true)) }
        val near = point()
        val found = async { userRepository.findPeople(query, near) }
        val spots = placeRepository.spots(query, near)
        val peopleResult = found.await()
        // A failure clears the last query's people too, so they never show under this one.
        if (peopleResult is AppResult.Success) {
            people.showFound(PeopleState(peopleResult.data.people.toImmutableList(), peopleResult.data.total, hasLoaded = true), peopleResult.data.nextCursor)
        } else {
            people.showFound(PeopleState(hasLoaded = true), nextCursor = null)
        }
        spotsCursor = (spots as? AppResult.Success)?.data?.nextCursor
        state.update {
            it.copy(
                search = it.search.copy(
                    spots = (spots as? AppResult.Success)?.data?.items?.toImmutableList() ?: persistentListOf(),
                    isLoading = false,
                    hasLoaded = true,
                ),
            )
        }
        val error = (spots as? AppResult.Error)?.error ?: (peopleResult as? AppResult.Error)?.error
        if (error != null) showMessage(error.toUiText(Res.string.social_error_search))
    }

    /** The next page of spots; one request at a time. */
    fun loadMore() {
        if (loadingMore?.isActive == true || search?.isActive == true) return
        val cursor = spotsCursor ?: return
        val query = state.value.search.query
        loadingMore = scope.launch {
            val result = placeRepository.spots(query, point(), cursor)
            if (result !is AppResult.Success) return@launch
            spotsCursor = result.data.nextCursor
            state.update { nearby ->
                val known = nearby.search.spots.map { it.id }.toSet()
                nearby.copy(search = nearby.search.copy(spots = (nearby.search.spots + result.data.items.filterNot { it.id in known }).toImmutableList()))
            }
        }
    }

    private companion object {
        val SEARCH_DEBOUNCE = 300.milliseconds
    }
}

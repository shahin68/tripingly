package com.falcon.tripingly.feature.social.presentation.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import com.falcon.tripingly.core.designsystem.component.AppPullToRefresh
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.EmptyState
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.Illustration
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.rememberBloomState
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.component.verticalFadingEdge
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_allow
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_ask
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_blocked
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_empty
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_empty_hint
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_open_settings
import com.falcon.tripingly.feature.social.generated.resources.social_people
import com.falcon.tripingly.feature.social.generated.resources.social_search_hint
import com.falcon.tripingly.feature.social.generated.resources.social_search_none
import com.falcon.tripingly.feature.social.presentation.component.LoadMoreAtEnd
import com.falcon.tripingly.feature.social.presentation.component.SearchPreviewStates
import com.falcon.tripingly.feature.social.presentation.component.placeholderNearbyPlaces
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.Action
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.Event
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.PeopleState
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.SearchState
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.State
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.Status
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Search tab. A spot opens its own map ([onOpenSpot]), a person their profile ([onOpenProfile]); the people tip's
 * Search people opens the tab's search field in the shell's top bar ([onOpenSearch]). [barsPadding] is the tab shell's
 * floating top and bottom bars, which the grid scrolls behind.
 */
@Composable
fun SearchRoute(
    onOpenSpot: (placeId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onOpenSearch: () -> Unit,
    barsPadding: PaddingValues = PaddingValues(),
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPermission = rememberLocationPermissionController(onResult = { viewModel.onAction(Action.PermissionResult(it)) })
    // When the Search tab opens and on every return to the app, e.g. from Settings.
    LifecycleResumeEffect(locationPermission) {
        viewModel.onAction(Action.PermissionChecked(locationPermission.status()))
        onPauseOrDispose {}
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            Event.RequestPermission -> locationPermission.request()
            Event.OpenAppSettings -> locationPermission.openSettings()
            Event.OpenSearch -> onOpenSearch()
        }
    }

    SearchScreen(state = state, onAction = viewModel::onAction, onOpenSpot = onOpenSpot, onOpenProfile = onOpenProfile, barsPadding = barsPadding)

    // The people sheet keeps its scroll here: a profile opened from it covers the Search tab, the sheet slides away, and on the
    // way back it comes up again where it was (AppBottomSheet hides while its screen isn't on top).
    val sheetList = rememberLazyListState()
    AppBottomSheet(value = state.search.people.takeIf { state.isPeopleSheetOpen }, onDismiss = { viewModel.onAction(Action.ClosePeople) }) { people, close ->
        PeopleSheet(people, sheetList, onAction = viewModel::onAction, onOpenProfile = onOpenProfile, onClose = close)
    }
}

/** The Search tab's part of the shell's top bar: people and spots search, on the tab's own [SearchViewModel]. */
@Composable
fun SearchTabField(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: SearchViewModel = koinViewModel()) {
    AppTextField(
        state = viewModel.queryField,
        placeholder = stringResource(Res.string.social_search_hint),
        onTextChange = { viewModel.onAction(Action.QueryChanged(it)) },
        onClose = onClose,
        modifier = modifier.fillMaxWidth(),
        style = AppTextFieldStyle.Search,
    )
}

@Composable
internal fun SearchScreen(
    state: State,
    onAction: (Action) -> Unit,
    onOpenSpot: (placeId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    modifier: Modifier = Modifier,
    barsPadding: PaddingValues = PaddingValues(),
) {
    val contentPadding = screenContentPadding(barsPadding)
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
    // One grid for the spots around me and for what a search found, so typing never swaps screens: its keyed items glide
    // into their new places. The last results stay while the next query loads, and until a search's first results the spots
    // around me stay (Shahin, 2026-11-24: nothing jumps; a skeleton only while there is nothing to show yet).
    val results = state.search.takeIf { it.isSearching && it.hasLoaded }
    Box(modifier.fillMaxSize()) {
        when {
            results == null && state.search.isSearching && state.places.isEmpty() -> SearchSkeleton(contentPadding)
            results == null && (state.status == Status.NeedsPermission || state.status == Status.Blocked || state.status == Status.Unavailable) ->
                LocationAsk(state.status, onAction, contentPadding)
            // Around everything, so the error and empty screens pull too, like Try again (Shahin, 2026-11-23). Not the
            // location asks: their button asks for the location rather than reloading. While searching, a pull asks again.
            else -> AppPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(Action.Refresh) },
                contentPadding = contentPadding,
            ) {
                LoadStateContent(
                    isLoading = results == null && (state.status == Status.Unknown || state.status == Status.Locating),
                    errorMessage = state.loadError?.takeIf { results == null }?.asString(),
                    onRetry = { onAction(Action.Refresh) },
                    contentPadding = contentPadding,
                    loading = { SearchSkeleton(contentPadding) },
                ) {
                    SearchGrid(state, results, onAction, onOpenSpot, onOpenProfile, contentPadding)
                }
            }
        }
    }
}

/**
 * The spots around me with the people tip on top until it is dismissed, or what a search found ([results]): its top
 * people, then its spots, with or without the location (spots then show their city). Then the OpenStreetMap credit.
 * People show only while searching (Shahin, 2026-11-24). Every item animates in, out and to its new place.
 */
@Composable
private fun SearchGrid(
    state: State,
    results: SearchState?,
    onAction: (Action) -> Unit,
    onOpenSpot: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val gridState = rememberLazyGridState()
    LoadMoreAtEnd(gridState) { onAction(Action.LoadMore) }
    val spots = results?.spots ?: state.places
    // The people box and the tip bloom in and out (Shahin, 2026-11-24); the box keeps the last people found while it goes.
    val found = results?.people?.takeIf { it.people.isNotEmpty() }
    var lastFound by remember { mutableStateOf(found ?: PeopleState()) }
    if (found != null) lastFound = found
    val peopleBloom = rememberBloomState(found != null)
    val tipBloom = rememberBloomState(results == null && state.isPeopleTipShown)
    SpotGrid(
        places = spots,
        gridState = gridState,
        contentPadding = contentPadding,
        onOpen = { onOpenSpot(it.id) },
        header = {
            topPeople(peopleBloom, lastFound, onAction, onOpenProfile)
            peopleTip(tipBloom, onAction)
            if (results == null) {
                if (state.places.isEmpty()) {
                    fullWidthItem("empty") {
                        EmptyState(
                            illustration = Illustration.NearbyEmpty,
                            title = stringResource(Res.string.social_nearby_empty),
                            message = stringResource(Res.string.social_nearby_empty_hint),
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        )
                    }
                }
            } else {
                if (!results.isLoading && results.people.people.isEmpty() && results.spots.isEmpty()) {
                    fullWidthItem("none") {
                        EmptyState(
                            illustration = Illustration.NearbyEmpty,
                            title = stringResource(Res.string.social_search_none),
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        )
                    }
                }
            }
        },
        footer = { osmCredit(state.isOsmCreditShown && spots.isNotEmpty()) },
    )
}

/**
 * Everyone the search found, a page at a time, in the same rows as the top people. A tap opens their profile; the sheet
 * slides away under it and comes back on the way back ([listState] keeps where it was).
 */
@Composable
private fun PeopleSheet(people: PeopleState, listState: LazyListState, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(title = stringResource(Res.string.social_people), onClose = onClose)
        LoadMoreAtEnd(listState) { onAction(Action.LoadMorePeople) }
        LazyColumn(state = listState, modifier = Modifier.verticalFadingEdge(listState), contentPadding = PaddingValues(bottom = MaterialTheme.spacing.large)) {
            items(people.people, key = { it.user.id }) { person ->
                FoundPersonRow(
                    person,
                    isFollowPending = person.user.id in people.pendingFollows,
                    onAction = onAction,
                    onOpenProfile = onOpenProfile,
                    inset = MaterialTheme.spacing.large,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * The grid's own tiles as a [Skeleton], laid out in its real rows (2+1, 1.5+1.5, 1+2, [skeletonTiles]), while there is
 * nothing to show yet: the location and the first spots load, or a search's first results without spots around me.
 */
@Composable
private fun SearchSkeleton(contentPadding: PaddingValues) {
    Skeleton(Modifier.fillMaxSize()) {
        SpotGrid(
            places = placeholderNearbyPlaces,
            gridState = rememberLazyGridState(),
            contentPadding = contentPadding,
            onOpen = {},
            arrange = ::skeletonTiles,
        )
    }
}

/** Why the Search tab needs the location and the way to allow it: the full-screen state with a location picture. */
@Composable
private fun LocationAsk(status: Status, onAction: (Action) -> Unit, contentPadding: PaddingValues) {
    val blocked = status == Status.Blocked
    ErrorScreen(
        onRetry = { onAction(Action.AllowLocation) },
        title = stringResource(Res.string.social_nearby),
        message = stringResource(if (blocked) Res.string.social_nearby_blocked else Res.string.social_nearby_ask),
        retryLabel = stringResource(if (blocked) Res.string.social_nearby_open_settings else Res.string.social_nearby_allow),
        contentPadding = contentPadding,
        illustration = Illustration.LocationAsk,
    )
}

@Preview
@Composable
private fun SearchScreenPreview(@PreviewParameter(SearchPreviewStates::class) state: State) {
    TripinglyTheme { SearchScreen(state = state, onAction = {}, onOpenSpot = {}, onOpenProfile = {}) }
}

@Preview
@Composable
private fun SearchScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { SearchScreen(state = SearchPreviewStates().values.elementAt(3), onAction = {}, onOpenSpot = {}, onOpenProfile = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SearchScreenLandscapePreview() {
    TripinglyTheme { SearchScreen(state = SearchPreviewStates().values.elementAt(3), onAction = {}, onOpenSpot = {}, onOpenProfile = {}) }
}

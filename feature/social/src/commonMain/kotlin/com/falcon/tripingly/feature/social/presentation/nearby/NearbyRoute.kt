package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import com.falcon.tripingly.core.designsystem.component.AppPullToRefresh
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.EmptyState
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.Illustration
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.PersonRow
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.Tag
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_friend_tag
import com.falcon.tripingly.feature.social.generated.resources.social_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_allow
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_ask
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_blocked
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_empty
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_empty_hint
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_open_settings
import com.falcon.tripingly.feature.social.generated.resources.social_people
import com.falcon.tripingly.feature.social.generated.resources.social_search_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_search_none
import com.falcon.tripingly.feature.social.presentation.component.FollowButton
import com.falcon.tripingly.feature.social.presentation.component.LoadMoreAtEnd
import com.falcon.tripingly.feature.social.presentation.component.NearbyPreviewStates
import com.falcon.tripingly.feature.social.presentation.component.placeholderNearbyPlaces
import com.falcon.tripingly.feature.social.presentation.component.placeholderPeople
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Action
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Event
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.PeopleState
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.State
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Status
import com.falcon.tripingly.core.model.social.Person
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.clickable

/**
 * The Nearby tab. A spot opens its own map ([onOpenSpot]), a person their profile ([onOpenProfile]). [barsPadding] is
 * the tab shell's floating top and bottom bars, which the grid scrolls behind.
 */
@Composable
fun NearbyRoute(
    onOpenSpot: (placeId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    barsPadding: PaddingValues = PaddingValues(),
    viewModel: NearbyViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPermission = rememberLocationPermissionController(onResult = { viewModel.onAction(Action.PermissionResult(it)) })
    // When Nearby opens and on every return to the app, e.g. from Settings.
    LifecycleResumeEffect(locationPermission) {
        viewModel.onAction(Action.PermissionChecked(locationPermission.status()))
        onPauseOrDispose {}
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            Event.RequestPermission -> locationPermission.request()
            Event.OpenAppSettings -> locationPermission.openSettings()
        }
    }

    NearbyScreen(state = state, onAction = viewModel::onAction, onOpenSpot = onOpenSpot, onOpenProfile = onOpenProfile, barsPadding = barsPadding)

    // The people sheet keeps its scroll here: a profile opened from it covers Nearby, the sheet slides away, and on the
    // way back it comes up again where it was (AppBottomSheet hides while its screen isn't on top).
    val sheetList = rememberLazyListState()
    AppBottomSheet(value = state.shownPeople.takeIf { state.isPeopleSheetOpen }, onDismiss = { viewModel.onAction(Action.ClosePeople) }) { people, close ->
        PeopleSheet(people, sheetList, onAction = viewModel::onAction, onOpenProfile = onOpenProfile, onClose = close)
    }
}

/** The Nearby tab's part of the shell's top bar: people and spots search, on the tab's own [NearbyViewModel]. */
@Composable
fun NearbySearchField(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: NearbyViewModel = koinViewModel()) {
    AppTextField(
        state = viewModel.queryField,
        placeholder = stringResource(Res.string.social_search_nearby),
        onTextChange = { viewModel.onAction(Action.QueryChanged(it)) },
        onClose = onClose,
        modifier = modifier.fillMaxWidth(),
        style = AppTextFieldStyle.Search,
    )
}

@Composable
internal fun NearbyScreen(
    state: State,
    onAction: (Action) -> Unit,
    onOpenSpot: (placeId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    modifier: Modifier = Modifier,
    barsPadding: PaddingValues = PaddingValues(),
) {
    val contentPadding = screenContentPadding(barsPadding)
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
    Box(modifier.fillMaxSize()) {
        when {
            // Typed: the people and spots found, with or without the location (spots then show their city).
            state.search.isSearching && !state.search.hasLoaded -> NearbySkeleton(contentPadding)
            state.search.isSearching -> SearchResults(state, onAction, onOpenSpot, onOpenProfile, contentPadding)
            state.status == Status.NeedsPermission || state.status == Status.Blocked || state.status == Status.Unavailable ->
                LocationAsk(state.status, onAction, contentPadding)
            // Around everything, so the error and empty screens pull too, like Try again (Shahin, 2026-11-23). Not the
            // location asks: their button asks for the location rather than reloading.
            else -> AppPullToRefresh(isRefreshing = state.isLoading && state.places.isNotEmpty(), onRefresh = { onAction(Action.Refresh) }, contentPadding = contentPadding) {
                LoadStateContent(
                    isLoading = state.status == Status.Unknown || state.status == Status.Locating,
                    errorMessage = state.loadError?.asString(),
                    onRetry = { onAction(Action.Refresh) },
                    contentPadding = contentPadding,
                    loading = { NearbySkeleton(contentPadding) },
                ) {
                    NearbyGrid(state, onAction, onOpenSpot, onOpenProfile, contentPadding)
                }
            }
        }
    }
}

/** The faces, then the spots around me, then the OpenStreetMap credit. */
@Composable
private fun NearbyGrid(state: State, onAction: (Action) -> Unit, onOpenSpot: (String) -> Unit, onOpenProfile: (String) -> Unit, contentPadding: PaddingValues) {
    val gridState = rememberLazyGridState()
    LoadMoreAtEnd(gridState) { onAction(Action.LoadMore) }
    SpotGrid(
        places = state.places,
        gridState = gridState,
        contentPadding = contentPadding,
        onOpen = { onOpenSpot(it.id) },
        onMarkHot = { onAction(Action.MarkHot(it)) },
        header = {
            peopleRow(state.people, onAction, onOpenProfile)
            if (state.places.isEmpty()) {
                fullWidthItem("empty") {
                    EmptyState(
                        illustration = Illustration.NearbyEmpty,
                        title = stringResource(Res.string.social_nearby_empty),
                        message = stringResource(Res.string.social_nearby_empty_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        footer = { osmCredit(state.isOsmCreditShown && state.places.isNotEmpty()) },
    )
}

/** The people and spots a search found, in the same grid; nothing found says so. */
@Composable
private fun SearchResults(state: State, onAction: (Action) -> Unit, onOpenSpot: (String) -> Unit, onOpenProfile: (String) -> Unit, contentPadding: PaddingValues) {
    val search = state.search
    val gridState = rememberLazyGridState()
    LoadMoreAtEnd(gridState) { onAction(Action.LoadMore) }
    SpotGrid(
        places = search.spots,
        gridState = gridState,
        contentPadding = contentPadding,
        onOpen = { onOpenSpot(it.id) },
        onMarkHot = { onAction(Action.MarkHot(it)) },
        header = {
            if (search.people.people.isNotEmpty()) peopleRow(search.people, onAction, onOpenProfile)
            if (search.hasLoaded && !search.isLoading && search.people.people.isEmpty() && search.spots.isEmpty()) {
                fullWidthItem("none") {
                    EmptyState(illustration = Illustration.NearbyEmpty, title = stringResource(Res.string.social_search_none), modifier = Modifier.fillMaxWidth())
                }
            }
        },
    )
}

/**
 * The faces row on top of the grid. Its place is kept while the people load (their placeholder faces), so the
 * tiles don't move down when they come; nobody at all takes it away.
 */
private fun LazyGridScope.peopleRow(people: PeopleState, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit) {
    if (people.hasLoaded && people.people.isEmpty()) return
    fullWidthItem("people") {
        if (people.hasLoaded) {
            PeopleFaces(people, onOpenProfile = { onOpenProfile(it.user.username) }, onOpenSheet = { onAction(Action.OpenPeople) })
        } else {
            Skeleton { PeopleFaces(PlaceholderPeople, onOpenProfile = {}, onOpenSheet = {}) }
        }
    }
}

/**
 * Everyone the row stands for, a page at a time: name, @handle, a Friend tag and Follow / Following. A tap opens
 * their profile; the sheet slides away under it and comes back on the way back ([listState] keeps where it was).
 */
@Composable
private fun PeopleSheet(people: PeopleState, listState: LazyListState, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(title = stringResource(Res.string.social_people), onClose = onClose)
        LoadMoreAtEnd(listState) { onAction(Action.LoadMorePeople) }
        val friend = stringResource(Res.string.social_friend_tag)
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = MaterialTheme.spacing.large)) {
            items(people.people, key = { it.user.id }) { person ->
                PersonRow(
                    user = person.user,
                    detail = "@${person.user.username}",
                    modifier = Modifier
                        .animateItem()
                        .clickable { onOpenProfile(person.user.username) }
                        .padding(horizontal = MaterialTheme.spacing.large - MaterialTheme.spacing.extraSmall),
                ) {
                    if (person.isFriend) Tag(friend)
                    FollowButton(person.isFollowing, onToggle = { onAction(Action.ToggleFollow(person)) }, size = ButtonSize.Small)
                }
            }
        }
    }
}

/** The grid's own faces and tiles, drawn from placeholders as a [Skeleton], while the location and the spots load. */
@Composable
private fun NearbySkeleton(contentPadding: PaddingValues) {
    Skeleton(Modifier.fillMaxSize()) {
        SpotGrid(
            places = placeholderNearbyPlaces,
            gridState = rememberLazyGridState(),
            contentPadding = contentPadding,
            onOpen = {},
            onMarkHot = {},
            header = { fullWidthItem("people") { PeopleFaces(PlaceholderPeople, onOpenProfile = {}, onOpenSheet = {}) } },
        )
    }
}

/** Enough placeholder faces for a full row on any phone. */
private val PlaceholderPeople = PeopleState(
    List(PLACEHOLDER_FACES) { index -> placeholderPeople[index % placeholderPeople.size].let { Person(it.copy(id = "${it.id}-$index"), isFriend = false, isFollowing = false) } }.toImmutableList(),
    total = 0,
    hasLoaded = true,
)

private const val PLACEHOLDER_FACES = 8

/** Why Nearby needs the location and the way to allow it: the full-screen state with a location picture. */
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
private fun NearbyScreenPreview(@PreviewParameter(NearbyPreviewStates::class) state: State) {
    TripinglyTheme { NearbyScreen(state = state, onAction = {}, onOpenSpot = {}, onOpenProfile = {}) }
}

@Preview
@Composable
private fun NearbyScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { NearbyScreen(state = NearbyPreviewStates().values.elementAt(3), onAction = {}, onOpenSpot = {}, onOpenProfile = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun NearbyScreenLandscapePreview() {
    TripinglyTheme { NearbyScreen(state = NearbyPreviewStates().values.elementAt(3), onAction = {}, onOpenSpot = {}, onOpenProfile = {}) }
}

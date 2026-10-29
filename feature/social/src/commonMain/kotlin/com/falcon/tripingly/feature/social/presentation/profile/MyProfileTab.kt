package com.falcon.tripingly.feature.social.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_followers
import com.falcon.tripingly.feature.social.generated.resources.social_followers_empty
import com.falcon.tripingly.feature.social.generated.resources.social_following
import com.falcon.tripingly.feature.social.generated.resources.social_following_empty
import com.falcon.tripingly.feature.social.generated.resources.social_friends
import com.falcon.tripingly.feature.social.generated.resources.social_friends_empty
import com.falcon.tripingly.feature.social.generated.resources.social_people_none
import com.falcon.tripingly.feature.social.generated.resources.social_profile_no_trips
import com.falcon.tripingly.feature.social.generated.resources.social_retry
import com.falcon.tripingly.feature.social.generated.resources.social_search_people
import com.falcon.tripingly.feature.social.generated.resources.social_trips
import com.falcon.tripingly.feature.social.presentation.component.EmptyText
import com.falcon.tripingly.feature.social.presentation.component.LoadMoreAtEnd
import com.falcon.tripingly.feature.social.presentation.component.PersonRow
import com.falcon.tripingly.feature.social.presentation.component.PublicTripCard
import com.falcon.tripingly.feature.social.presentation.component.placeholderPeople
import com.falcon.tripingly.feature.social.presentation.component.placeholderProfile
import com.falcon.tripingly.feature.social.presentation.component.placeholderTrips
import com.falcon.tripingly.feature.social.presentation.component.previewPeople
import com.falcon.tripingly.feature.social.presentation.component.previewTrip
import com.falcon.tripingly.feature.social.presentation.people.PeopleSearchViewModel
import com.falcon.tripingly.feature.social.presentation.people.PeopleViewModel
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Action
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the Profile tab lists under my header: my public trips, or one of my people lists. */
internal enum class ProfileSegment(val label: StringResource, val list: PeopleList?) {
    Trips(Res.string.social_trips, null),
    Friends(Res.string.social_friends, PeopleList.Friends),
    Following(Res.string.social_following, PeopleList.Following),
    Followers(Res.string.social_followers, PeopleList.Followers),
}

/**
 * The Profile tab: my header and counts, then my public trips or my friends, the people I follow or my followers.
 * While a name is typed in the shell's top bar ([PeopleSearchField]), the people found replace it all.
 */
@Composable
internal fun MyProfileTab(
    state: State,
    onAction: (Action) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    barsPadding: PaddingValues,
    searchViewModel: PeopleSearchViewModel = koinViewModel(),
) {
    val search by searchViewModel.uiState.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableStateOf(ProfileSegment.Trips) }
    // One list per segment, each loaded the first time its segment is picked and kept while the tab lives.
    val people = segment.list?.let { list ->
        key(list) {
            val viewModel = koinViewModel<PeopleViewModel>(key = "people:$list") { parametersOf(list) }
            viewModel.uiState.collectAsStateWithLifecycle().value to viewModel::onAction
        }
    }
    MyProfileScreen(
        state = state,
        search = search,
        segment = segment,
        people = people?.first,
        onAction = onAction,
        onDismissSearchMessage = { searchViewModel.onAction(PeopleSearchViewModel.Action.DismissMessage) },
        onSegment = { segment = it },
        onPeopleAction = { action -> people?.second?.invoke(action) },
        onOpenProfile = onOpenProfile,
        barsPadding = barsPadding,
    )
}

@Composable
internal fun MyProfileScreen(
    state: State,
    search: PeopleSearchViewModel.State,
    segment: ProfileSegment,
    people: PeopleViewModel.State?,
    onAction: (Action) -> Unit,
    onDismissSearchMessage: () -> Unit,
    onSegment: (ProfileSegment) -> Unit,
    onPeopleAction: (PeopleViewModel.Action) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    barsPadding: PaddingValues = PaddingValues(),
) {
    val contentPadding = screenContentPadding(barsPadding)
    val message = state.message ?: search.message ?: people?.message
    ShowMessage(
        message = message?.asString(),
        onShown = {
            when (message) {
                state.message -> onAction(Action.DismissMessage)
                search.message -> onDismissSearchMessage()
                else -> onPeopleAction(PeopleViewModel.Action.OnDismissMessage)
            }
        },
        isError = message !is UiText.Note,
    )
    LoadStateContent(
        isLoading = state.profile == null && state.isLoading,
        errorMessage = state.loadError?.asString(),
        onRetry = { onAction(Action.Retry) },
        contentPadding = contentPadding,
        loading = { MyProfileSkeleton(segment, contentPadding) },
    ) {
        val listState = rememberLazyListState()
        LoadMoreAtEnd(listState) { if (segment.list == null) onAction(Action.Trips.LoadMore) else onPeopleAction(PeopleViewModel.Action.OnLoadMore) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            if (search.isSearching) {
                peopleItems(search.results, isLoading = search.isLoading, loadError = null, empty = Res.string.social_people_none, onRetry = {}, onOpenProfile)
                return@LazyColumn
            }
            state.profile?.let { item(key = "header") { MyProfileHeader(it, Modifier.animateItem()) } }
            item(key = "segments") { Segments(segment, onSegment, Modifier.animateItem()) }
            when (val list = segment.list) {
                null -> tripItems(state, onAction)
                else -> peopleItems(
                    people = people?.people.orEmpty(),
                    isLoading = people?.hasLoaded != true,
                    loadError = people?.loadError,
                    empty = list.empty,
                    onRetry = { onPeopleAction(PeopleViewModel.Action.OnRetry) },
                    onOpenProfile = onOpenProfile,
                )
            }
        }
    }
}

/** The Profile tab's part of the shell's top bar: people search, on the tab's own [PeopleSearchViewModel]. */
@Composable
fun PeopleSearchField(viewModel: PeopleSearchViewModel = koinViewModel()) {
    AppTextField(
        state = viewModel.queryField,
        placeholder = stringResource(Res.string.social_search_people),
        onTextChange = { viewModel.onAction(PeopleSearchViewModel.Action.QueryChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        style = AppTextFieldStyle.Search,
    )
}

@Composable
private fun Segments(segment: ProfileSegment, onSegment: (ProfileSegment) -> Unit, modifier: Modifier = Modifier) {
    SegmentedToggle(modifier.fillMaxWidth()) {
        ProfileSegment.entries.forEach { entry ->
            SegmentedToggleOption(label = stringResource(entry.label), selected = entry == segment, onClick = { onSegment(entry) })
        }
    }
}

/** The tab while my profile loads: my header, the segments and my trips, drawn from placeholders as a [Skeleton]. */
@Composable
private fun MyProfileSkeleton(segment: ProfileSegment, contentPadding: PaddingValues) {
    Skeleton(Modifier.fillMaxSize()) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            MyProfileHeader(placeholderProfile)
            Segments(segment, onSegment = {})
            if (segment.list == null) {
                placeholderTrips.forEach { PublicTripCard(trip = it, onOpen = {}, onLike = {}, onToggleSave = {}, onReport = {}) }
            } else {
                placeholderPeople.forEach { PersonRow(user = it, onClick = {}) }
            }
        }
    }
}

private fun LazyListScope.tripItems(state: State, onAction: (Action) -> Unit) {
    if (state.trips.isEmpty()) {
        item(key = "trips-empty") { EmptyText(stringResource(Res.string.social_profile_no_trips), Modifier.animateItem()) }
    }
    items(state.trips, key = { it.trip.id }) { trip ->
        PublicTripCard(
            trip = trip,
            onOpen = { onAction(Action.Trips.Click(trip)) },
            onLike = { onAction(Action.Trips.Like(trip)) },
            onToggleSave = { onAction(Action.Trips.ToggleSave(trip)) },
            onReport = { onAction(Action.Trips.Report(trip)) },
            modifier = Modifier.animateItem(),
        )
    }
}

/** People as cards; their skeleton while the first page loads, why it failed with a retry, and [empty] when there's nobody. */
private fun LazyListScope.peopleItems(
    people: List<UserSummary>,
    isLoading: Boolean,
    loadError: UiText?,
    empty: StringResource,
    onRetry: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    when {
        people.isEmpty() && loadError != null -> item(key = "people-error") {
            Column(Modifier.fillMaxWidth().animateItem(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyText(loadError.asString())
                SecondaryButton(stringResource(Res.string.social_retry), onRetry, size = ButtonSize.Medium)
            }
        }
        people.isEmpty() && isLoading -> items(placeholderPeople, key = { "skeleton-${it.id}" }) { Skeleton { PersonRow(user = it, onClick = {}) } }
        people.isEmpty() -> item(key = "people-empty") { EmptyText(stringResource(empty), Modifier.animateItem()) }
        else -> items(people, key = { it.id }) { user ->
            PersonRow(user = user, onClick = { onOpenProfile(user.username) }, modifier = Modifier.animateItem())
        }
    }
}

private val PeopleList.empty: StringResource
    get() = when (this) {
        PeopleList.Friends -> Res.string.social_friends_empty
        PeopleList.Following -> Res.string.social_following_empty
        PeopleList.Followers -> Res.string.social_followers_empty
    }

@Composable
private fun MyProfilePreview(segment: ProfileSegment, darkTheme: Boolean = false, query: String = "") {
    TripinglyTheme(darkTheme = darkTheme) {
        MyProfileScreen(
            state = State(isMe = true, profile = previewProfile, trips = persistentListOf(previewTrip), isLoading = false),
            search = PeopleSearchViewModel.State(query = query, results = previewPeople.toImmutableList()),
            segment = segment,
            people = PeopleViewModel.State(people = previewPeople.toImmutableList(), hasLoaded = true),
            onAction = {}, onDismissSearchMessage = {}, onSegment = {}, onPeopleAction = {}, onOpenProfile = {},
        )
    }
}

@Preview
@Composable
private fun MyProfileTripsPreview() = MyProfilePreview(ProfileSegment.Trips)

@Preview
@Composable
private fun MyProfileFriendsDarkPreview() = MyProfilePreview(ProfileSegment.Friends, darkTheme = true)

@Preview
@Composable
private fun MyProfileSearchPreview() = MyProfilePreview(ProfileSegment.Trips, query = "ann")

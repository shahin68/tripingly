package com.falcon.tripingly.feature.social.presentation.social

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_explore
import com.falcon.tripingly.feature.social.presentation.component.previewPeople
import com.falcon.tripingly.feature.social.presentation.component.previewTrip
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.AddPlaceState
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.ExploreState
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.NearbyState
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.NearbyStatus
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.PeopleSearchState
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Section
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate

private val previewExplore = ExploreState(
    trips = persistentListOf(
        previewTrip,
        previewTrip.copy(trip = previewTrip.trip.copy(id = "t2", name = "Lake Balaton by bike"), likeCount = 3, likedByMe = false),
    ),
    hasLoaded = true,
)

internal val previewNearbyPlaces = persistentListOf(
    NearbyPlace(
        id = "n1",
        name = "Café Central",
        category = PlaceCategory.Cafe,
        location = GeoPoint(48.2104, 16.3655),
        isTripinly = true,
        likeCount = 12,
        likedByMe = true,
        coverImageId = null,
        coverSource = null,
        coverThumbUrl = null,
        distanceMeters = 350,
    ),
    NearbyPlace(
        id = "n2",
        name = "Kunsthistorisches Museum Wien, the museum of fine arts",
        category = PlaceCategory.Museum,
        location = GeoPoint(48.2038, 16.3616),
        isTripinly = false,
        likeCount = 0,
        likedByMe = false,
        coverImageId = null,
        coverSource = null,
        coverThumbUrl = null,
        distanceMeters = 2_400,
    ),
)

/** Explore with trips, Explore loading, Explore filtered to nothing with an error, Nearby with places, and people found by name. */
internal class SocialScreenPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(explore = previewExplore),
        State(explore = ExploreState(isLoading = true)),
        State(explore = ExploreState(period = ExplorePeriod.Week, isNearbyFilter = true, hasLoaded = true), message = UiText.Resource(Res.string.social_error_load_explore)),
        State(section = Section.Nearby, nearby = NearbyState(status = NearbyStatus.Ready, places = previewNearbyPlaces)),
        State(people = PeopleSearchState(query = "an", results = previewPeople.toPersistentList())),
    )
}

/** Nearby while locating, asking for the location, after it was blocked, with places, and with none around. */
internal class NearbyPreviewStates : PreviewParameterProvider<NearbyState> {
    override val values = sequenceOf(
        NearbyState(status = NearbyStatus.Locating),
        NearbyState(status = NearbyStatus.NeedsPermission),
        NearbyState(status = NearbyStatus.Blocked),
        NearbyState(status = NearbyStatus.Ready, places = previewNearbyPlaces),
        NearbyState(status = NearbyStatus.Ready),
    )
}

/** The default month, Nearby on with a week, the season, and all time. */
internal class ExploreFiltersPreviewStates : PreviewParameterProvider<ExploreState> {
    override val values = sequenceOf(
        ExploreState(),
        ExploreState(period = ExplorePeriod.Week, isNearbyFilter = true),
        ExploreState(period = ExplorePeriod.Season),
        ExploreState(period = ExplorePeriod.All),
    )
}

/** Picking a trip, the trip's days loading, and picking a day. */
internal class AddPlacePreviewStates : PreviewParameterProvider<AddPlaceState> {
    private val trips = persistentListOf(previewTrip.trip, previewTrip.trip.copy(id = "t2", name = "Lake Balaton", startDate = null, endDate = null))
    override val values = sequenceOf(
        AddPlaceState(previewNearbyPlaces[0], trips),
        AddPlaceState(previewNearbyPlaces[0], trips, trip = trips[0], isLoadingDays = true),
        AddPlaceState(
            place = previewNearbyPlaces[0],
            trips = trips,
            trip = trips[0],
            days = persistentListOf(
                TripDay("d1", 0, LocalDate(2026, 6, 1), emptyList()),
                TripDay("d2", 1, LocalDate(2026, 6, 2), emptyList()),
            ),
        ),
    )
}

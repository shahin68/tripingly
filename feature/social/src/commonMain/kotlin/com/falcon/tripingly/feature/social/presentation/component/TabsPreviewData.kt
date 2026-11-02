package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_explore
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.AddPlaceState
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Status
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

internal val previewNearbyPlaces = persistentListOf(
    NearbyPlace(
        id = "n1",
        name = "Café Central",
        category = PlaceCategory.Cafe,
        location = GeoPoint(48.2104, 16.3655),
        isTripinly = true,
        hotCount = 12,
        hotByMe = true,
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
        hotCount = 0,
        hotByMe = false,
        coverImageId = null,
        coverSource = null,
        coverThumbUrl = null,
        distanceMeters = 2_400,
    ),
)

/** Explore with trips, the first load, filtered to nothing, and a first load that failed. */
internal class ExplorePreviewStates : PreviewParameterProvider<ExploreViewModel.State> {
    override val values = sequenceOf(
        ExploreViewModel.State(
            trips = persistentListOf(
                previewTrip,
                previewTrip.copy(trip = previewTrip.trip.copy(id = "t2", name = "Lake Balaton by bike"), likeCount = 1_204, likedByMe = false),
                previewTrip.copy(trip = previewTrip.trip.copy(id = "t3", name = "Budapest nights"), likeCount = 0, likedByMe = false),
            ),
            hasLoaded = true,
        ),
        ExploreViewModel.State(isLoading = true),
        ExploreViewModel.State(period = ExplorePeriod.Week, isNearbyFilter = true, hasLoaded = true),
        ExploreViewModel.State(hasLoaded = true, loadError = UiText.Resource(Res.string.social_error_load_explore)),
    )
}

/** Nearby while locating, asking for the location, after it was blocked, with places, with none around, and failed. */
internal class NearbyPreviewStates : PreviewParameterProvider<NearbyViewModel.State> {
    override val values = sequenceOf(
        NearbyViewModel.State(status = Status.Locating),
        NearbyViewModel.State(status = Status.NeedsPermission),
        NearbyViewModel.State(status = Status.Blocked),
        NearbyViewModel.State(status = Status.Ready, places = previewNearbyPlaces),
        NearbyViewModel.State(status = Status.Ready),
        NearbyViewModel.State(status = Status.Ready, loadError = UiText.Resource(Res.string.social_error_load_explore)),
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

package com.falcon.tripingly.feature.social.presentation.component

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/*
 * Placeholder content for the loading skeletons, which show the real composables with it: typical lengths, so
 * the grey bars look like the content that's coming. Never shown as text.
 */

internal val placeholderPeople = listOf(
    UserSummary("placeholder1", "traveller", "Alex Traveller"),
    UserSummary("placeholder2", "wanderer", "Sam Wanderer"),
    UserSummary("placeholder3", "explorer", "Kim Explorer"),
)

internal val placeholderTrips = listOf("A week by the sea", "Old towns and castles", "Weekend in the city").mapIndexed { index, name ->
    PublicTrip(
        trip = Trip(
            id = "placeholder$index",
            name = name,
            startDate = LocalDate(2026, 6, 1),
            endDate = LocalDate(2026, 6, 7),
            visibility = TripVisibility.PUBLIC,
            role = TripRole.VIEWER,
            owner = placeholderPeople[index],
            dayCount = 7,
            markerCount = 12,
            createdAt = Instant.fromEpochSeconds(0),
            updatedAt = Instant.fromEpochSeconds(0),
        ),
        likeCount = 24,
        likedByMe = false,
        saveCount = 0,
    )
}

internal val placeholderProfile = Profile(
    user = placeholderPeople.first(),
    followerCount = 12,
    followingCount = 12,
    isFollowing = false,
    followsMe = false,
    isFriend = false,
    publicTripCount = 3,
    trips = emptyList(),
    nextCursor = null,
)

internal val placeholderNearbyPlaces = listOf("Café on the corner", "City museum of art", "Old town square", "Park by the river").mapIndexed { index, name ->
    NearbyPlace(
        id = "placeholder$index",
        name = name,
        category = PlaceCategory.Museum,
        location = GeoPoint(0.0, 0.0),
        isTripinly = false,
        hotCount = 12,
        hotByMe = false,
        coverImageId = null,
        coverSource = null,
        coverThumbUrl = null,
        distanceMeters = 350,
    )
}

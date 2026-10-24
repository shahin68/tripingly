package com.falcon.tripingly.feature.trips

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.Delete
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.Leave
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.MakePrivate
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.MakePublic
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.Members
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.RemoveSaved
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.Rename
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.Reschedule
import com.falcon.tripingly.feature.trips.presentation.screen.TripMenuItem.Share
import com.falcon.tripingly.feature.trips.presentation.screen.menu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TripMenuTest {

    private fun trip(role: TripRole, visibility: TripVisibility = TripVisibility.PUBLIC) = Trip(
        id = "t1",
        name = "Vienna",
        startDate = null,
        endDate = null,
        visibility = visibility,
        role = role,
        owner = UserSummary("u1", "anna", "Anna"),
        dayCount = 1,
        markerCount = 0,
        createdAt = Instant.parse("2026-10-16T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-16T10:00:00Z"),
    )

    @Test
    fun theOwner_managesTheTrip() {
        assertEquals(listOf(Rename, Reschedule, MakePrivate, Members, Share, Delete), trip(TripRole.OWNER).menu())
        assertEquals(MakePublic, trip(TripRole.OWNER, TripVisibility.PRIVATE).menu()[2])
    }

    @Test
    fun anEditor_seesMembersSharesOrLeaves() {
        assertEquals(listOf(Members, Share, Leave), trip(TripRole.EDITOR).menu())
    }

    @Test
    fun aSavedTrip_isSharedOrRemoved() {
        assertEquals(listOf(Share, RemoveSaved), trip(TripRole.VIEWER).menu())
    }
}

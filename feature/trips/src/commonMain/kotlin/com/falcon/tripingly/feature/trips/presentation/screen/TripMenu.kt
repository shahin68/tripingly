package com.falcon.tripingly.feature.trips.presentation.screen

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole

/** One entry of a trip's long-press menu in My Trips. */
internal enum class TripMenuItem { Rename, Reschedule, MakePrivate, MakePublic, Members, Share, RemoveSaved, Delete, Leave }

/** The owner manages the trip, an editor can share it or leave, a saved trip can be shared or removed. */
internal fun Trip.menu(): List<TripMenuItem> = buildList {
    if (role == TripRole.VIEWER) return listOf(TripMenuItem.Share, TripMenuItem.RemoveSaved)
    if (role.canManage) {
        add(TripMenuItem.Rename)
        add(TripMenuItem.Reschedule)
        add(if (visibility == TripVisibility.PUBLIC) TripMenuItem.MakePrivate else TripMenuItem.MakePublic)
    }
    add(TripMenuItem.Members)
    add(TripMenuItem.Share)
    if (role.canManage) add(TripMenuItem.Delete) else if (role.canEdit) add(TripMenuItem.Leave)
}

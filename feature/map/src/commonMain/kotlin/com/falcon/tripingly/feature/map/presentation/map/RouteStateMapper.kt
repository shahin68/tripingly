package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.RouteLeg
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_transit_ride_detail
import com.falcon.tripingly.feature.map.generated.resources.map_transit_ride_times
import com.falcon.tripingly.feature.map.generated.resources.map_transit_stops
import com.falcon.tripingly.feature.map.generated.resources.map_transit_to_stop
import com.falcon.tripingly.feature.map.generated.resources.map_transit_towards
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk_detail
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk_to
import com.falcon.tripingly.feature.map.presentation.map.ui.distanceText
import com.falcon.tripingly.feature.map.presentation.map.ui.durationText
import com.falcon.tripingly.core.model.route.TransitVehicle
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.map.state.MY_LOCATION_ID
import com.falcon.tripingly.feature.map.presentation.map.state.RouteModel
import com.falcon.tripingly.feature.map.presentation.map.state.RouteSide
import com.falcon.tripingly.feature.map.presentation.map.state.isGoingTo
import com.falcon.tripingly.feature.map.presentation.map.state.isRouteEnd
import com.falcon.tripingly.feature.map.presentation.map.state.isRouteTo
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The route card's summary of [plan] (a real one, or null while there is none); [isTransitCovered]: public transport goes
 * between the route's ends.
 */
internal fun RouteModel.summary(plan: RoutePlan?, alongTheWay: List<AlongTheWayPlace>, selectedPlaceId: String?, addToDayNumber: Int?, isTransitCovered: Boolean) = RouteSummaryState(
    mode = travelMode,
    durationSeconds = plan?.route?.durationSeconds,
    distanceMeters = plan?.route?.distanceMeters,
    isEstimated = isUnavailable || this.plan?.isEstimated == true,
    isLoading = isLoading,
    alongTheWay = alongTheWay.toImmutableList(),
    selectedPlaceId = selectedPlaceId,
    addToDayNumber = addToDayNumber,
    isTransitCovered = isTransitCovered,
    leaveAt = leaveAt(),
    transitNote = transitNote,
    transitLegs = plan?.takeIf { planMode == TravelMode.Transit }?.route?.legs.orEmpty().flatMap { it.steps }.mapNotNull { step ->
        when {
            step.isRide -> step.line?.let { TransitChip.Ride(it, step.vehicle ?: TransitVehicle.Other) }
            // A step across the street between two rides isn't worth a chip.
            step.durationSeconds >= MIN_WALK_CHIP_SECONDS -> TransitChip.Walk(step.durationSeconds)
            else -> null
        }
    }.toImmutableList(),
)

/**
 * "Leave at" by public transport, once a time is known: the one picked, or the one the route was asked for (now, or now's
 * time on the open day); never "Leave now" (Shahin, 2026-11-15). Not where public transport doesn't go.
 */
private fun RouteModel.leaveAt(): LeaveAtState? {
    if (travelMode != TravelMode.Transit || transitNote == TransitNote.NotHere) return null
    val time = transitPick?.time ?: transitAt?.at?.toLocalDateTime(TimeZone.currentSystemDefault())?.time ?: return null
    return LeaveAtState(clockTime(time), isArriveBy = transitPick?.isArriveBy == true && between != null)
}

/** A public transport route's legs as the steps sheet's rows; a day's leg starts with the stop it goes to ([stopTitle]). */
internal fun transitStepRows(legs: List<RouteLeg>, stopTitle: (String) -> UiText?): List<TransitStepRow> = legs.flatMapIndexed { legIndex, leg ->
    val header = leg.toMarkerId?.let(stopTitle)?.let { TransitStepRow.Leg("leg-$legIndex", UiText.Resource(Res.string.map_transit_to_stop, it)) }
    listOfNotNull(header) + leg.steps.mapIndexed { index, step ->
        val key = "step-$legIndex-$index"
        val duration = durationText(step.durationSeconds)
        if (step.isRide) {
            TransitStepRow.Ride(
                key = key,
                line = step.line,
                vehicle = step.vehicle ?: TransitVehicle.Other,
                towards = step.headsign?.let { UiText.Resource(Res.string.map_transit_towards, it) },
                times = UiText.Resource(Res.string.map_transit_ride_times, step.from.name.orEmpty(), clockTime(step.from.time), step.to.name.orEmpty(), clockTime(step.to.time)),
                detail = UiText.Resource(Res.string.map_transit_ride_detail, UiText.Plural(Res.plurals.map_transit_stops, step.stops, step.stops), duration),
            )
        } else {
            TransitStepRow.Walk(
                key = key,
                title = step.to.name?.let { UiText.Resource(Res.string.map_transit_walk_to, duration, it) } ?: UiText.Resource(Res.string.map_transit_walk, duration),
                detail = UiText.Resource(Res.string.map_transit_walk_detail, distanceText(step.distanceMeters), clockTime(step.from.time), clockTime(step.to.time)),
            )
        }
    }
}

/** The time "Leave at" opens on: the one picked, or the one the route was asked for, or now. */
internal fun RouteModel.leaveAtTime(): LocalTime =
    transitPick?.time ?: (transitAt?.at ?: Clock.System.now()).toLocalDateTime(TimeZone.currentSystemDefault()).time

/** "14:05": hours and minutes, as public transport times show. */
internal fun clockTime(time: LocalTime): String = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** [clockTime] of a moment, on this device's clock. */
internal fun clockTime(at: Instant): String = clockTime(at.toLocalDateTime(TimeZone.currentSystemDefault()).time)

private const val MIN_WALK_CHIP_SECONDS = 60

/** The Go here and Route buttons of the pin [at]. */
internal fun RouteModel.pinRoutes(at: GeoPoint, hasLocation: Boolean) = PinRoutesState(
    isGoHereOn = isGoingTo(at),
    hasLocation = hasLocation,
    isRouteTo = isRouteTo(at),
    isRouteOn = isRouteEnd(at),
    mode = travelMode,
)

/**
 * The from/to bar: the start while "to" is picked after Route from, or the route between two points that shows, the end
 * whose pill was tapped being its field.
 */
internal fun RouteModel.routeBar(): RouteBarState? {
    start?.let {
        val label = startLabel ?: UiText.Server(it.name)
        return RouteBarState(label, to = null, mode = travelMode)
    }
    val between = between ?: return null
    // The end picked again is its field; its place stays an end on the map until another is picked.
    return RouteBarState(
        from = (between.fromLabel ?: UiText.Server(between.from.name)).takeIf { editing != RouteSide.From },
        to = (between.toLabel ?: UiText.Server(between.to.name)).takeIf { editing != RouteSide.To },
        mode = travelMode,
        isEditing = editing != null,
    )
}

/** The route's ends on the map (where I am has my own dot instead): the end kept while the other is picked, or both. */
internal fun RouteModel.endPlaces(): List<Pair<MapPlace, Boolean>> {
    val ends = start?.let { listOf(it to true) } ?: between?.let { listOf(it.from to true, it.to to false) }.orEmpty()
    return ends.filter { (place, _) -> place.id != MY_LOCATION_ID }
}

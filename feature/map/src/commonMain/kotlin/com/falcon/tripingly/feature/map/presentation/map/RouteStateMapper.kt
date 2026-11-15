package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.Route
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.RouteLeg
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_transit_change_walk
import com.falcon.tripingly.feature.map.generated.resources.map_transit_changes
import com.falcon.tripingly.feature.map.generated.resources.map_transit_joined
import com.falcon.tripingly.feature.map.generated.resources.map_transit_no_change
import com.falcon.tripingly.feature.map.generated.resources.map_transit_stops
import com.falcon.tripingly.feature.map.generated.resources.map_transit_times
import com.falcon.tripingly.feature.map.generated.resources.map_transit_towards
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk_detail
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk_total
import com.falcon.tripingly.feature.map.presentation.map.ui.distanceText
import com.falcon.tripingly.feature.map.presentation.map.ui.durationText
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
    transitTimes = plan?.route?.takeIf { planMode == TravelMode.Transit }?.let { route ->
        route.times()?.let { UiText.Resource(Res.string.map_transit_joined, it, changesText(route.transfers)) }
    },
    transitLegs = plan?.takeIf { planMode == TravelMode.Transit }?.route?.legs.orEmpty().flatMap { it.steps }.mapNotNull { step ->
        when {
            step.isRide -> step.line?.let { TransitChip.Ride(it, step.durationSeconds) }
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

/** "09:12 – 09:36": when a public transport route sets off and arrives, once both are known. */
internal fun Route.times(): UiText? {
    val departure = departure ?: return null
    val arrival = arrival ?: return null
    return UiText.Resource(Res.string.map_transit_times, clockTime(departure), clockTime(arrival))
}

/** "no change", "1 change", "3 changes". */
internal fun changesText(transfers: Int): UiText =
    if (transfers == 0) UiText.Resource(Res.string.map_transit_no_change) else UiText.Plural(Res.plurals.map_transit_changes, transfers, transfers)

/** The steps sheet's top of [route]: "24 min · 1 change" over "09:12 – 09:36 · walk 10 min". */
internal fun transitStepsHeader(route: Route): Pair<UiText, UiText?> {
    val title = UiText.Resource(Res.string.map_transit_joined, durationText(route.durationSeconds), changesText(route.transfers))
    val walking = route.legs.flatMap { it.steps }.filter { !it.isRide }.sumOf { it.durationSeconds }
    val walk = UiText.Resource(Res.string.map_transit_walk_total, durationText(walking)).takeIf { walking > 0 }
    val times = route.times()
    val subtitle = when {
        times != null && walk != null -> UiText.Resource(Res.string.map_transit_joined, times, walk)
        else -> times ?: walk
    }
    return title to subtitle
}

/**
 * A public transport route's [legs] as the canvas's timeline: a row where each walk or ride sets off (its time, place
 * and mark), then one where the route ends. A leg's start is [fromTitle] (a day's stop, or where A to B starts), the end
 * [toTitle]; a day's stops on the way show as stops, their arrival and departure one row.
 */
internal fun transitStepRows(legs: List<RouteLeg>, fromTitle: (RouteLeg) -> UiText?, toTitle: (RouteLeg) -> UiText?): List<TransitStepRow> {
    val withSteps = legs.filter { it.steps.isNotEmpty() }
    val rows = withSteps.flatMapIndexed { legIndex, leg ->
        leg.steps.mapIndexed { index, step ->
            val before = leg.steps.getOrNull(index - 1)
            val isChange = !step.isRide && before?.isRide == true && leg.steps.getOrNull(index + 1)?.isRide == true
            val place = (if (index == 0) fromTitle(leg) else null) ?: step.from.name?.let(UiText::Server)
            val mark = when {
                index == 0 && legIndex > 0 -> TransitMark.Stop
                isChange -> TransitMark.Change
                step.isRide -> TransitMark.Line(step.line?.color)
                before != null && before.isRide -> TransitMark.Line(before.line?.color)
                else -> TransitMark.Walk
            }
            val duration = durationText(step.durationSeconds)
            TransitStepRow(
                key = "step-$legIndex-$index",
                time = clockTime(step.from.time),
                place = place,
                mark = mark,
                path = when {
                    step.isRide -> TransitPath.Ride(step.line?.color)
                    isChange -> TransitPath.Change
                    else -> TransitPath.Walk
                },
                ride = if (step.isRide) TransitRide(step.line, step.headsign?.let { UiText.Resource(Res.string.map_transit_towards, it) }) else null,
                detail = when {
                    step.isRide -> UiText.Resource(Res.string.map_transit_joined, UiText.Plural(Res.plurals.map_transit_stops, step.stops, step.stops), duration)
                    isChange -> UiText.Resource(Res.string.map_transit_change_walk, duration)
                    else -> UiText.Resource(Res.string.map_transit_walk_detail, duration, distanceText(step.distanceMeters))
                },
            )
        }
    }
    val lastLeg = withSteps.lastOrNull() ?: return rows
    val last = lastLeg.steps.last()
    val end = TransitStepRow(
        key = "end",
        time = clockTime(last.to.time),
        place = toTitle(lastLeg) ?: last.to.name?.let(UiText::Server),
        mark = TransitMark.End,
        path = null,
    )
    return rows + end
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

package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.route.Route
import com.falcon.tripingly.core.model.route.RouteLeg
import com.falcon.tripingly.core.model.route.TransitLine
import com.falcon.tripingly.core.model.route.TransitStep
import com.falcon.tripingly.core.model.route.TransitStop
import com.falcon.tripingly.core.model.route.TransitVehicle
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_transit_change_walk
import com.falcon.tripingly.feature.map.generated.resources.map_transit_joined
import com.falcon.tripingly.feature.map.generated.resources.map_transit_no_change
import com.falcon.tripingly.feature.map.generated.resources.map_transit_towards
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk_detail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** The canvas's example: Mariahilfer Kirche, walk, U3, change at Stephansplatz, U1, walk, Riesenrad. */
class TransitStepRowsTest {

    private val u3 = TransitLine("U3", 0xFFEF7C00, 0xFFFFFFFF)
    private val u1 = TransitLine("U1", 0xFFE3000F, 0xFFFFFFFF)
    private val steps = listOf(
        walk(null, 12, "Neubaugasse", 16),
        ride(u3, "Simmering", "Neubaugasse", 16, "Stephansplatz", 22),
        walk("Stephansplatz", 22, "Stephansplatz", 25),
        ride(u1, "Leopoldau", "Stephansplatz", 25, "Praterstern", 30),
        walk("Praterstern", 30, null, 36),
    )
    private val leg = RouteLeg(null, null, 5000, 1440, steps)

    @Test
    fun aRouteFromAToBIsATimelineFromItsStartToAFlag() {
        val rows = transitStepRows(listOf(leg), fromTitle = { UiText.Server("Mariahilfer Kirche") }, toTitle = { UiText.Server("Wiener Riesenrad") })

        assertEquals(
            listOf(TransitMark.Walk, TransitMark.Line(u3.color), TransitMark.Change, TransitMark.Line(u1.color), TransitMark.Line(u1.color), TransitMark.End),
            rows.map { it.mark },
        )
        assertEquals(listOf(TransitPath.Walk, TransitPath.Ride(u3.color), TransitPath.Change, TransitPath.Ride(u1.color), TransitPath.Walk, null), rows.map { it.path })
        assertEquals(
            listOf("Mariahilfer Kirche", "Neubaugasse", "Stephansplatz", "Stephansplatz", "Praterstern", "Wiener Riesenrad"),
            rows.map { (it.place as UiText.Server).text },
        )
        // Each row at the time its walk or ride sets off, the flag when the last one arrives (on this device's clock).
        assertEquals(steps.map { clockTime(it.from.time) } + clockTime(steps.last().to.time), rows.map { it.time })
        assertEquals(u3, rows[1].ride?.line)
        assertEquals(Res.string.map_transit_towards, (rows[1].ride?.towards as UiText.Resource).resource)
        assertEquals(Res.string.map_transit_walk_detail, (rows[0].detail as UiText.Resource).resource)
        assertEquals(Res.string.map_transit_change_walk, (rows[2].detail as UiText.Resource).resource)
        assertNull(rows.last().detail)
    }

    @Test
    fun aDaysStopOnTheWayIsOneRowWhereTheNextLegSetsOff() {
        val second = RouteLeg("m2", "m3", 2000, 600, listOf(walk(null, 40, null, 50)))
        val rows = transitStepRows(listOf(leg.copy(fromMarkerId = "m1", toMarkerId = "m2"), second), fromTitle = { UiText.Server(it.fromMarkerId!!) }, toTitle = { UiText.Server(it.toMarkerId!!) })

        // Leg one's six rows lose their end; the day's second stop sets off on foot; the third ends it.
        assertEquals(7, rows.size)
        assertEquals(TransitMark.Stop, rows[5].mark)
        assertEquals("m2", (rows[5].place as UiText.Server).text)
        assertEquals(TransitMark.End, rows[6].mark)
        assertEquals("m3", (rows[6].place as UiText.Server).text)
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test
    fun theSheetsTopIsTheTimeAndChangesOverTheTimesAndTheWalking() {
        val route = Route(emptyList(), 5000, 1440, listOf(leg), steps.first().from.time, steps.last().to.time, transfers = 0)

        val (title, subtitle) = transitStepsHeader(route)

        val parts = assertIs<UiText.Resource>(title)
        assertEquals(Res.string.map_transit_joined, parts.resource)
        assertEquals(Res.string.map_transit_no_change, (parts.args[1] as UiText.Resource).resource)
        assertEquals(Res.string.map_transit_joined, assertIs<UiText.Resource>(subtitle).resource)
    }

    private fun walk(from: String?, fromMinute: Int, to: String?, toMinute: Int) =
        TransitStep(false, null, null, null, stop(from, fromMinute), stop(to, toMinute), 0, 300, (toMinute - fromMinute) * 60, emptyList())

    private fun ride(line: TransitLine, headsign: String, from: String, fromMinute: Int, to: String, toMinute: Int) =
        TransitStep(true, TransitVehicle.Subway, line, headsign, stop(from, fromMinute), stop(to, toMinute), 4, 2000, (toMinute - fromMinute) * 60, emptyList())

    private fun stop(name: String?, minute: Int) = TransitStop(name, GeoPoint(48.2, 16.37), Instant.parse("2026-11-16T09:${minute.toString().padStart(2, '0')}:00Z"))
}

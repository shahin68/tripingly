package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.NumberUtils
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_along_add
import com.falcon.tripingly.feature.map.generated.resources.map_along_the_way
import com.falcon.tripingly.feature.map.generated.resources.map_along_the_way_item
import com.falcon.tripingly.feature.map.generated.resources.map_mode_cycling
import com.falcon.tripingly.feature.map.generated.resources.map_mode_driving
import com.falcon.tripingly.feature.map.generated.resources.map_mode_walking
import com.falcon.tripingly.feature.map.generated.resources.map_route_estimated
import com.falcon.tripingly.feature.map.generated.resources.map_route_hours
import com.falcon.tripingly.feature.map.generated.resources.map_route_km
import com.falcon.tripingly.feature.map.generated.resources.map_route_meters
import com.falcon.tripingly.feature.map.generated.resources.map_route_minutes
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.RouteSummaryState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewRoute
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Fast all the way, slowing only at the very end: how the route card grows and shrinks (the plan sheet canvas). */
internal val RouteGrowEasing = CubicBezierEasing(0.2f, 0.6f, 0.1f, 1f)

/** How long the route card takes to grow or shrink. */
internal const val ROUTE_GROW_MILLIS = 280

/** How to travel: walk, bike or car as icons, uncolored; the names are their descriptions. */
@Composable
internal fun TravelModeToggle(
    mode: TravelMode,
    onModeSelected: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedToggle(modifier) {
        TravelMode.entries.forEach { entry ->
            SegmentedToggleOption(
                label = stringResource(entry.label()),
                selected = entry == mode,
                onClick = { onModeSelected(entry) },
                icon = entry.icon(),
                showLabel = false,
            )
        }
    }
}

/**
 * The route card while there is a [route], and nothing otherwise. It grows down from nothing and shrinks back with
 * its rounded shape whole the whole time (its own background animates with its height); its rows slide in one after
 * another, the places come in from the end, and on the way out everything only fades.
 */
@Composable
internal fun RouteCardSlot(route: RouteSummaryState?, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier, isAnimated: Boolean = true) {
    val grow = tween<IntSize>(ROUTE_GROW_MILLIS, easing = RouteGrowEasing)
    // The card's background is a plain box around the animation, sized by it, so it grows and shrinks with the card
    // and stays drawn when the animation rests. An opaque colour (a touch darker or lighter than the sheet's glass,
    // worked out once), not a see-through shade: translucent paint on the translucent glass didn't show on devices.
    val cardColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CARD_SHADE).compositeOver(GlassDefaults.opaqueTint.compositeOver(MaterialTheme.colorScheme.surface))
    Box(modifier.clip(MaterialTheme.shapes.large).background(cardColor)) {
        AnimatedContent(
            targetState = route,
            contentKey = { it != null },
            transitionSpec = {
                if (isAnimated) {
                    fadeIn(tween(CARD_FADE_IN_MILLIS)) togetherWith fadeOut(tween(CARD_FADE_OUT_MILLIS, CARD_FADE_OUT_DELAY_MILLIS)) using SizeTransform { _, _ -> grow }
                } else {
                    EnterTransition.None togetherWith ExitTransition.None using null
                }
            },
            contentAlignment = Alignment.TopStart,
            label = "route card",
        ) { shown ->
            if (shown != null) RouteCardContent(shown, isAnimated, onAction)
        }
    }
}

/**
 * The route: its icon (a spinner while a way of travelling loads), time and length, how to travel, and the places
 * along the way scrolling out to the card's edges. Everything sits [spacing.small] in from every side of the card.
 */
@Composable
private fun AnimatedVisibilityScope.RouteCardContent(route: RouteSummaryState, isAnimated: Boolean, onAction: (MapAction) -> Unit) {
    val inset = MaterialTheme.spacing.small
    Column(Modifier.fillMaxWidth().padding(vertical = inset), verticalArrangement = Arrangement.spacedBy(inset)) {
        Row(
            modifier = Modifier.padding(horizontal = inset).then(rowMotion(0, isAnimated)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            RouteIcon(route.isLoading)
            RouteTotals(route, Modifier.weight(1f))
            TravelModeToggle(route.mode, onModeSelected = { onAction(MapAction.Plan.SelectTravelMode(it)) }, modifier = Modifier.width(ModesWidth))
        }
        if (route.isEstimated) {
            Text(
                text = stringResource(Res.string.map_route_estimated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = inset).then(rowMotion(1, isAnimated)),
            )
        }
        if (route.alongTheWay.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.map_along_the_way),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = inset).then(rowMotion(2, isAnimated)),
            )
            val selectedIndex = route.alongTheWay.indexOfFirst { it.place.id == route.selectedPlaceId }
            // A place picked on the map is the card in view, also when the plan first shows.
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
            val onScreen = remember { OnScreen() }
            LaunchedEffect(route.selectedPlaceId) { listState.reveal(selectedIndex, onScreen) }
            LazyRow(
                state = listState,
                modifier = Modifier.horizontalFadingEdge(listState).trackOnScreen(onScreen),
                contentPadding = PaddingValues(horizontal = inset),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                itemsIndexed(route.alongTheWay, key = { _, item -> item.place.id }) { index, item ->
                    AlongTheWayCard(
                        item = item,
                        dayNumber = route.addToDayNumber,
                        onOpen = { onAction(MapAction.Plan.AlongTheWayClick(it)) },
                        onAdd = { onAction(MapAction.Plan.AddAlongTheWay(it)) },
                        // Places arriving with a route left on from last time just show, like the rest of the card.
                        modifier = (if (isAnimated) Modifier.animateItem() else Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null))
                            .then(alongMotion(index, isAnimated)),
                    )
                }
            }
        }
    }
}

/** Row [index] of the card slides down a little into place after the one before; it only fades on the way out. */
private fun AnimatedVisibilityScope.rowMotion(index: Int, isAnimated: Boolean): Modifier {
    if (!isAnimated) return Modifier
    val delay = ROW_DELAY_MILLIS + index * ROW_STAGGER_MILLIS
    return Modifier.animateEnterExit(
        enter = fadeIn(tween(ROW_FADE_MILLIS, delay)) + slideInVertically(tween(ROW_SLIDE_MILLIS, delay, RouteGrowEasing)) { -it / ROW_SLIDE_FRACTION },
        exit = fadeOut(tween(ROW_EXIT_MILLIS)),
    )
}

/** The places come in from the end one after another (the first few; later ones are off screen anyway). */
private fun AnimatedVisibilityScope.alongMotion(index: Int, isAnimated: Boolean): Modifier {
    if (!isAnimated) return Modifier
    val delay = ALONG_DELAY_MILLIS + index.coerceAtMost(ALONG_STAGGERED) * ALONG_STAGGER_MILLIS
    return Modifier.animateEnterExit(
        enter = fadeIn(tween(ROW_FADE_MILLIS, delay)) + slideInHorizontally(tween(ROUTE_GROW_MILLIS, delay, RouteGrowEasing)) { it / ALONG_SLIDE_FRACTION },
        exit = fadeOut(tween(ROW_EXIT_MILLIS)),
    )
}

/** The route's sign, as tall as the travel modes beside it; a spinner in its place while the route loads. */
@Composable
private fun RouteIcon(isLoading: Boolean) {
    Box(
        modifier = Modifier.size(RowHeight).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = { (fadeIn(tween(ICON_SWAP_MILLIS)) + scaleIn(initialScale = 0.5f)) togetherWith (fadeOut(tween(ICON_SWAP_MILLIS)) + scaleOut(targetScale = 0.5f)) },
            label = "route icon",
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(SpinnerSize), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Icon(Icons.Outlined.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** The route's time over its length ("1 h 53 min" over "9.4 km"); empty until the first route arrives. */
@Composable
private fun RouteTotals(route: RouteSummaryState, modifier: Modifier = Modifier) {
    Column(modifier) {
        route.durationSeconds?.let {
            Text(durationLabel(it), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        route.distanceMeters?.let {
            Text(distanceText(it).asString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A place near the route: its kind, name, when the route gets there and how far off it it is; + adds it to the open day. */
@Composable
private fun AlongTheWayCard(item: AlongTheWayPlace, dayNumber: Int?, onOpen: (MapPlace) -> Unit, onAdd: (MapPlace) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onOpen(item.place) },
        shape = MaterialTheme.shapes.medium,
        // The sheet's own glass colour, so the places stand out of the shaded card in both themes.
        color = GlassDefaults.opaqueTint,
        modifier = modifier.widthIn(max = AlongCardMaxWidth),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.small),
        ) {
            Icon(item.place.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small).weight(1f, fill = false)) {
                Text(item.place.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(Res.string.map_along_the_way_item, durationLabel(item.etaFromStartSeconds), distanceText(item.distanceFromRouteMeters).asString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (dayNumber != null) {
                FilledIconButton(onClick = { onAdd(item.place) }, modifier = Modifier.size(AddButtonSize)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.map_along_add, dayNumber), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The travel modes' height (40 dp options in 4 dp of track), which the route icon matches. */
private val RowHeight = 48.dp
/** Three 40 dp options in 4 dp of track and gaps: each picked option is a circle. */
private val ModesWidth = 136.dp
private val SpinnerSize = 20.dp
private val AlongCardMaxWidth = 240.dp
private val AddButtonSize = 36.dp

// The canvas's timings: the card fades in fast and out late while its height eases; rows follow one after another.
private const val CARD_FADE_IN_MILLIS = 80
private const val CARD_FADE_OUT_MILLIS = 120
private const val CARD_FADE_OUT_DELAY_MILLIS = 160
private const val ROW_DELAY_MILLIS = 90
private const val ROW_STAGGER_MILLIS = 40
private const val ROW_FADE_MILLIS = 180
private const val ROW_SLIDE_MILLIS = 260
private const val ROW_EXIT_MILLIS = 80
private const val ROW_SLIDE_FRACTION = 8
private const val ALONG_DELAY_MILLIS = 170
private const val ALONG_STAGGER_MILLIS = 45
private const val ALONG_STAGGERED = 4
private const val ALONG_SLIDE_FRACTION = 9
private const val ICON_SWAP_MILLIS = 160

/** How much of the content colour shades the route card over the sheet. */
private const val CARD_SHADE = 0.05f

/** The routes' credits ("© openrouteservice.org | © OpenStreetMap contributors"), tappable like the map's. */
@Composable
fun RouteAttribution(text: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clickable { runCatching { uriHandler.openUri(COPYRIGHT_URL) } }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "45 min", or "1 h 20 min" from an hour on; at least a minute. */
@Composable
fun durationLabel(seconds: Int): String = durationText(seconds).asString()

/** [durationLabel] for state built outside composition. */
internal fun durationText(seconds: Int): UiText {
    val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) {
        UiText.Resource(Res.string.map_route_minutes, minutes)
    } else {
        UiText.Resource(Res.string.map_route_hours, minutes / 60, minutes % 60)
    }
}

/** "350 m" up to a kilometre, then "2.4 km" ("2,4 km" in German and Hungarian), as on Nearby. */
internal fun distanceText(meters: Int): UiText =
    if (meters < 1000) {
        UiText.Resource(Res.string.map_route_meters, meters)
    } else {
        UiText.Resource(Res.string.map_route_km, NumberUtils.kilometers(meters))
    }

private fun TravelMode.label() = when (this) {
    TravelMode.Walking -> Res.string.map_mode_walking
    TravelMode.Cycling -> Res.string.map_mode_cycling
    TravelMode.Driving -> Res.string.map_mode_driving
}

private fun TravelMode.icon() = when (this) {
    TravelMode.Walking -> Icons.AutoMirrored.Filled.DirectionsWalk
    TravelMode.Cycling -> Icons.AutoMirrored.Filled.DirectionsBike
    TravelMode.Driving -> Icons.Default.DirectionsCar
}

private const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

@Composable
private fun RouteCardPreviewContent() {
    PlanPreviewSheet {
        Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            // An owner's or editor's, with Add; one loading a new way of travelling; a viewer's with estimated times.
            RouteCardSlot(previewRoute, onAction = {})
            RouteCardSlot(previewRoute.copy(mode = TravelMode.Cycling, isLoading = true, alongTheWay = persistentListOf()), onAction = {})
            RouteCardSlot(previewRoute.copy(mode = TravelMode.Driving, isEstimated = true, addToDayNumber = null), onAction = {})
        }
    }
}

@Preview
@Composable
private fun RouteCardPreview() {
    TripinglyTheme { RouteCardPreviewContent() }
}

@Preview
@Composable
private fun RouteCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { RouteCardPreviewContent() }
}

@Preview
@Composable
private fun RouteAttributionPreview() {
    TripinglyTheme {
        RouteAttribution(
            text = "© openrouteservice.org | © OpenStreetMap contributors",
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

package com.falcon.tripingly.feature.map.presentation.map.ui

import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.floor
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.presentation.map.DayTab
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.preview.DayTabsPreviewStates
import org.jetbrains.compose.resources.stringResource

/**
 * The trip's days as pills on a floating glass bar at the bottom, the open one filled; owners and editors add a day
 * with +. [isJoined]: the plan sheet sits on top, so the bar is its bottom part (square top corners, no shadow).
 */
@Composable
internal fun DayTabs(
    tabs: DayTabsState,
    isJoined: Boolean,
    canAddDay: Boolean,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.days.isEmpty()) return Spacer(modifier.navigationBarsPadding())
    val corner = MaterialTheme.shapes.extraLarge.topStart
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()
    // Read by the drag on the open day, which outlives recompositions.
    val activeIndex by rememberUpdatedState(tabs.activeIndex)
    val currentOnAction by rememberUpdatedState(onAction)
    // The green pill is its own layer behind the days, at a fractional day: it slides to a tapped day and follows a
    // drag that starts on it, like the bottom bar's pill.
    val pill = remember { Animatable(tabs.activeIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tabs.activeIndex, isDragging) { if (!isDragging) pill.animateTo(tabs.activeIndex.toFloat(), PillSpring) }
    val pillColor = MaterialTheme.colorScheme.primary
    val pillShape = MaterialTheme.shapes.large
    // Keeps the open day in view, e.g. after adding one at the end.
    LaunchedEffect(tabs.activeIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == tabs.activeIndex }) listState.animateScrollToItem(tabs.activeIndex)
    }
    GlassSurface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium)
            .fillMaxWidth(),
        shape = if (isJoined) RoundedCornerShape(topStart = ZeroCornerSize, topEnd = ZeroCornerSize, bottomStart = corner, bottomEnd = corner) else MaterialTheme.shapes.extraLarge,
        shadowElevation = if (isJoined) 0.dp else GlassDefaults.ShadowElevation,
        blur = false,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .drawBehind {
                        val bounds = listState.pillBounds(pill.value) ?: return@drawBehind
                        translate(left = bounds.first) {
                            drawOutline(pillShape.createOutline(Size(bounds.second, size.height), layoutDirection, this), pillColor)
                        }
                    }
                    .dragOpenDay(
                        listState = listState,
                        active = { activeIndex },
                        onDrag = { x ->
                            isDragging = true
                            val position = listState.dayPosition(x) ?: return@dragOpenDay
                            scope.launch { pill.snapTo(position) }
                            position.roundToInt().takeIf { it != activeIndex }?.let { day ->
                                haptics.tick()
                                currentOnAction(MapAction.Days.Select(day))
                            }
                        },
                        onDragEnd = { isDragging = false },
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                itemsIndexed(tabs.days, key = { _, day -> day.id }) { index, day ->
                    DayPill(day, selected = index == tabs.activeIndex, onClick = { onAction(MapAction.Days.Select(index)) }, modifier = Modifier.animateItem())
                }
            }
            if (canAddDay) {
                // A round button as tall as the day pills, so it sits as far in from the end as they do from the start.
                FilledTonalIconButton(
                    onClick = { onAction(MapAction.Header.AddDay) },
                    modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall).size(DayPillHeight),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.map_add_day))
                }
            }
        }
    }
}

/**
 * Like the bottom bar's pill, the open day can be dragged: a drag that starts on it reports the finger's x ([onDrag])
 * until it lifts ([onDragEnd]). Watched before the row's own scrolling, which it takes over once the finger moves past
 * the touch slop; a drag that starts on another day scrolls the row, and taps stay taps.
 */
private fun Modifier.dragOpenDay(listState: LazyListState, active: () -> Int, onDrag: (x: Float) -> Unit, onDragEnd: () -> Unit): Modifier =
    pointerInput(listState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val onOpenDay = listState.layoutInfo.visibleItemsInfo.any { it.index == active() && down.position.x >= it.offset && down.position.x < it.offset + it.size }
            if (!onOpenDay) return@awaitEachGesture
            var isDragging = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!isDragging) isDragging = abs(change.position.x - down.position.x) > viewConfiguration.touchSlop
                    if (isDragging) {
                        change.consume()
                        onDrag(change.position.x)
                    }
                }
            } finally {
                if (isDragging) onDragEnd()
            }
        }
    }

/** The fractional day under [x], between the centres of the days around it; null before the row is laid out. */
private fun LazyListState.dayPosition(x: Float): Float? {
    val centres = layoutInfo.visibleItemsInfo.map { it.index to it.offset + it.size / 2f }.ifEmpty { return null }
    if (x <= centres.first().second) return centres.first().first.toFloat()
    if (x >= centres.last().second) return centres.last().first.toFloat()
    val before = centres.indexOfLast { it.second <= x }
    val (day, from) = centres[before]
    val to = centres[before + 1].second
    return day + (x - from) / (to - from)
}

/** Where the pill at fractional day [position] is drawn: its left edge and width, between the two days around it. */
private fun LazyListState.pillBounds(position: Float): Pair<Float, Float>? {
    val items = layoutInfo.visibleItemsInfo
    val day = floor(position).toInt()
    val from = items.firstOrNull { it.index == day } ?: return null
    val to = items.firstOrNull { it.index == day + 1 } ?: from
    val fraction = position - day
    return lerp(from.offset.toFloat(), to.offset.toFloat(), fraction) to lerp(from.size.toFloat(), to.size.toFloat(), fraction)
}

private val PillSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * "Day 2" over its date, in white while open (the green pill is drawn behind the days by [DayTabs]). A red dot on the top end corner of that fill (open
 * or not): a stop of the day has a "new" mark.
 */
@Composable
private fun DayPill(day: DayTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    val haptics = rememberHaptics()
    Box(modifier) {
        Box(
            modifier = Modifier
                .heightIn(min = DayPillHeight)
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, role = Role.Tab) {
                    if (!selected) haptics.tick()
                    onClick()
                }
                .padding(horizontal = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.map_day_label_no_date, day.number), style = MaterialTheme.typography.labelLarge, color = content)
                day.date?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = content) }
            }
        }
        if (day.hasNew) {
            // Centred on the rounded corner's curve, outside the clip so it isn't cut.
            val onCorner = NewDotSize / 2 - CornerRadius * (1 - CORNER_CURVE_POINT)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = onCorner, y = -onCorner)
                    .size(NewDotSize)
                    .background(MapColors.NewActivity, CircleShape),
            )
        }
    }
}

private val NewDotSize = 10.dp

/** The radius of the day pill's corners ([MaterialTheme.shapes] large). */
private val CornerRadius = 20.dp

/** How far round a corner's curve its middle point lies from the corner's edges: cos 45°. */
private const val CORNER_CURVE_POINT = 0.7071f

private val DayPillHeight = 48.dp

@Preview
@Composable
private fun DayTabsPreview(@PreviewParameter(DayTabsPreviewStates::class) tabs: DayTabsState) {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) { DayTabs(tabs = tabs, isJoined = false, canAddDay = true, onAction = {}) }
    }
}

@Preview
@Composable
private fun DayTabsDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            DayTabs(tabs = DayTabsPreviewStates().values.first(), isJoined = false, canAddDay = false, onAction = {})
        }
    }
}

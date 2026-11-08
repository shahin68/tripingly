package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.NewIndicator
import com.falcon.tripingly.core.designsystem.component.bottomSystemBar
import com.falcon.tripingly.core.designsystem.component.onTopEndCorner
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day
import com.falcon.tripingly.feature.map.presentation.map.DayTab
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.preview.DayTabsPreviewStates
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
    if (tabs.days.isEmpty()) return Spacer(modifier.windowInsetsPadding(WindowInsets.bottomSystemBar))
    val corner = MaterialTheme.shapes.extraLarge.topStart
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()
    // Read by the drag on the open day, which outlives recompositions.
    val activeIndex by rememberUpdatedState(tabs.activeIndex)
    // The green pill is its own layer behind the days, at a fractional day: it slides to a tapped day and follows a
    // drag that starts on it, like the bottom bar's pill. While dragged it sits at [dragPosition] (a plain float state: no coroutine or recomposition per move, only a
    // redraw); otherwise [pill] animates it onto the open day. Only the draw phase reads either.
    val pill = remember { Animatable(tabs.activeIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    var dragPosition by remember { mutableFloatStateOf(Float.NaN) }
    val isDragging by remember { derivedStateOf { !dragPosition.isNaN() } }
    LaunchedEffect(tabs.activeIndex, isDragging) {
        if (isDragging) return@LaunchedEffect
        pill.animateTo(tabs.activeIndex.toFloat(), PillSpring)
    }
    val pillAt = { if (dragPosition.isNaN()) pill.value else dragPosition }
    // The day last opened, so a drag opens each day once even before the open day comes back from the view model.
    var opened by remember { mutableIntStateOf(tabs.activeIndex) }
    LaunchedEffect(tabs.activeIndex) { opened = tabs.activeIndex }
    // The open day's own drag: the pill follows the finger and a day opens once the pill is mostly on it (so it
    // doesn't flip back and forth halfway between two). Read through updated state, so the gesture never restarts.
    val onDayDrag by rememberUpdatedState { x: Float ->
        listState.dayPosition(x)?.let { position ->
            dragPosition = position
            position.roundToInt().takeIf { it != opened && abs(position - it) < OPEN_DAY_REACH }?.let { day ->
                opened = day
                haptics.tick()
                onAction(MapAction.Days.Select(day))
            }
        }
    }
    val onDayDragEnd by rememberUpdatedState {
        // The spring takes over from where the finger let go.
        if (!dragPosition.isNaN()) scope.launch { pill.snapTo(dragPosition) }.invokeOnCompletion { dragPosition = Float.NaN }
    }
    val pillColor = MaterialTheme.colorScheme.primary
    val pillShape = MaterialTheme.shapes.large
    // Keeps the open day in view, e.g. after adding one at the end.
    LaunchedEffect(tabs.activeIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == tabs.activeIndex }) listState.animateScrollToItem(tabs.activeIndex)
    }
    GlassSurface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.bottomSystemBar)
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
                    .clip(MaterialTheme.shapes.extraLarge)
                    .drawBehind {
                        val bounds = listState.pillBounds(pillAt()) ?: return@drawBehind
                        translate(left = bounds.first) {
                            drawOutline(pillShape.createOutline(Size(bounds.second, size.height), layoutDirection, this), pillColor)
                        }
                    }
                    .dragOpenDay(
                        listState = listState,
                        active = { activeIndex },
                        onDrag = { onDayDrag(it) },
                        onDragEnd = { onDayDragEnd() },
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                itemsIndexed(tabs.days, key = { _, day -> day.id }) { index, day ->
                    DayPill(
                        day = day,
                        selected = index == tabs.activeIndex,
                        // How much of the pill is on this day right now, read while drawing (no recomposition per frame).
                        onPill = { pillAt().roundToInt() == index },
                        onClick = { onAction(MapAction.Days.Select(index)) },
                        onDelete = {
                            // The confirmation (and the delete) is about the open day.
                            onAction(MapAction.Days.Select(index))
                            onAction(MapAction.Header.DeleteDay)
                        }.takeIf { day.canDelete },
                        modifier = Modifier.animateItem(),
                    )
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
    // A spring's overshoot past the first or last day stays drawn on it (it vanished for a frame: the blink).
    val clamped = position.coerceIn(0f, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0).toFloat())
    val day = floor(clamped).toInt()
    val from = items.firstOrNull { it.index == day } ?: return null
    val to = items.firstOrNull { it.index == day + 1 } ?: from
    val fraction = clamped - day
    return lerp(from.offset.toFloat(), to.offset.toFloat(), fraction) to lerp(from.size.toFloat(), to.size.toFloat(), fraction)
}

/** How close to a day the dragged pill must be to open it, in days. */
private const val OPEN_DAY_REACH = 0.35f

private val PillSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * "Day 2" over its date, in white while open (the green pill is drawn behind the days by [DayTabs]). A red dot on the top end corner of that fill (open
 * or not): a stop of the day has a "new" mark. A long press opens a menu to delete the day, when the user may ([onDelete]).
 */
@Composable
private fun DayPill(day: DayTab, selected: Boolean, onPill: () -> Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    val on = MaterialTheme.colorScheme.onPrimary
    // White while the sliding pill is mostly on it, grey otherwise: no fading, read while drawing.
    val content = ColorProducer { if (onPill()) on else off }
    val haptics = rememberHaptics()
    var isMenuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .heightIn(min = DayPillHeight)
                .clip(MaterialTheme.shapes.large)
                .semantics { this.selected = selected }
                .combinedClickable(
                    role = Role.Tab,
                    onLongClick = onDelete?.let { { isMenuOpen = true } },
                    onLongClickLabel = onDelete?.let { stringResource(Res.string.map_delete_day) },
                ) {
                    if (!selected) haptics.tick()
                    onClick()
                }
                .padding(horizontal = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(stringResource(Res.string.map_day_label_no_date, day.number), style = MaterialTheme.typography.labelLarge, color = content)
                day.date?.let { BasicText(it, style = MaterialTheme.typography.labelSmall, color = content) }
            }
        }
        if (day.hasNew) {
            // Centred on the rounded corner's curve, outside the clip so it isn't cut.
            NewIndicator(Modifier.align(Alignment.TopEnd).onTopEndCorner(CornerRadius), strokeColor = GlassDefaults.opaqueTint)
        }
        if (onDelete != null) {
            AppDropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                actions = listOf(
                    DropdownAction(stringResource(Res.string.map_delete_day), isDestructive = true, icon = Icons.Outlined.Delete) {
                        isMenuOpen = false
                        onDelete()
                    },
                ),
            )
        }
    }
}


/** The radius of the day pill's corners ([MaterialTheme.shapes] large). */
private val CornerRadius = 20.dp


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
private fun DayTabsJoinedPreview(@PreviewParameter(DayTabsPreviewStates::class) tabs: DayTabsState) {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) { DayTabs(tabs = tabs, isJoined = true, canAddDay = true, onAction = {}) }
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

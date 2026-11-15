package com.falcon.tripingly.feature.map.presentation.map.ui

import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_plan_handle
import com.falcon.tripingly.feature.map.presentation.map.preview.StopsCardPreviewStates
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** How much of the plan sheet shows while it's pulled down: its handle and its title row. */
internal val PlanSheetPeek = 72.dp

private val HandleAreaHeight = 24.dp

internal enum class PlanSheetValue { Collapsed, Expanded }

/** Where the plan sheet is pulled to, all the way up at first; kept over rotation and process death. */
@Composable
internal fun rememberPlanSheetState(): AnchoredDraggableState<PlanSheetValue> =
    rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(PlanSheetValue.Expanded) }

/** How far, in px, the sheet is pulled up above its peek right now; 0 before it's laid out. */
internal fun AnchoredDraggableState<PlanSheetValue>.pulledUp(): Float =
    (anchors.maxPosition() - offset).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f

/**
 * The open day's [plan] in a sheet, the top part of the bottom card whose bottom part is the day tabs (BottomCards
 * draws the card's glass). A drag anywhere on it pulls it up to show all of it, or down to leave [PlanSheetPeek] of it;
 * a tap on its handle does the same. It isn't modal: the map stays usable around it, and the map keeps out of the peek
 * only, so pulling the sheet doesn't move the map; a fit keeps out of all that shows of it (see [pulledUp]). When a
 * plan comes the card grows up from the tabs with the sheet on its top edge; when it goes the card shrinks back and
 * the sheet slides into the tabs, cut off at their top line (Shahin, 2026-11-14, D2a).
 */
@Composable
internal fun <T : Any> PlanSheet(
    plan: T?,
    state: AnchoredDraggableState<PlanSheetValue>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    // A tick as the sheet settles up or down.
    LaunchedEffect(state) { snapshotFlow { state.settledValue }.drop(1).collect { haptics.tick() } }
    val handleLabel = stringResource(Res.string.map_plan_handle)
    AnimatedContent(
        targetState = plan,
        // The pulled-down part of the sheet sits below this box and is cut off there, at the day tabs' top line.
        modifier = modifier.fillMaxWidth().clipToBounds(),
        // A plan replacing another (stops, a route from A to B) only updates; the card grows and shrinks for coming and going.
        contentKey = { it != null },
        // The sheet keeps its top on the card's top edge: it's uncovered from the top coming, cut from the bottom going.
        transitionSpec = {
            (expandVertically(expandFrom = Alignment.Top) togetherWith shrinkVertically(shrinkTowards = Alignment.Top)).using(SizeTransform(clip = true))
        },
    ) { shown ->
        if (shown == null) return@AnimatedContent
        // A sheet that comes in (not one shown again after a rotation) comes in all the way up.
        val isComingIn = transition.currentState == EnterExitState.PreEnter
        LaunchedEffect(Unit) { if (isComingIn) state.snapTo(PlanSheetValue.Expanded) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Dragging anywhere on the sheet pulls it; the plan scrolls only once it's pulled all the way up.
                .nestedScroll(remember(state) { state.pullFirst() })
                .anchoredDraggable(state, Orientation.Vertical)
                .pulledDown(state),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HandleAreaHeight)
                    .semantics { contentDescription = handleLabel }
                    .clickable {
                        val next = if (state.settledValue == PlanSheetValue.Expanded) PlanSheetValue.Collapsed else PlanSheetValue.Expanded
                        scope.launch { state.animateTo(next) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
            }
            // In a short window (landscape) the expanded plan scrolls.
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content(shown) }
        }
    }
}

/**
 * The sheet takes only the height it shows: its full height less how far it's pulled down, so whatever sits above it
 * rides on its top. Read while laying out, so dragging moves it without recomposing anything.
 */
private fun Modifier.pulledDown(state: AnchoredDraggableState<PlanSheetValue>) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    state.updateAnchors(
        DraggableAnchors {
            PlanSheetValue.Expanded at 0f
            PlanSheetValue.Collapsed at (placeable.height - PlanSheetPeek.toPx()).coerceAtLeast(0f)
        },
    )
    val shown = placeable.height - state.requireOffset().roundToInt()
    layout(placeable.width, shown.coerceAtLeast(0)) { placeable.place(0, 0) }
}

/**
 * Lets a drag that starts on the plan's scrolling part move the sheet first: up until it's all shown, then the plan
 * scrolls; down once the plan is back at its top. A fling ends on whichever end it heads for.
 */
private fun AnchoredDraggableState<PlanSheetValue>.pullFirst() = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0 && source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity =
        // Flinging up while the sheet isn't all the way up opens it instead of scrolling the plan.
        if (available.y < 0 && offset > anchors.minPosition()) {
            animateTo(PlanSheetValue.Expanded)
            available
        } else {
            Velocity.Zero
        }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Whatever the plan didn't scroll ends the pull, at the end the sheet heads for or, without speed, the nearer one.
        when {
            available.y > 0 -> animateTo(PlanSheetValue.Collapsed)
            available.y < 0 -> animateTo(PlanSheetValue.Expanded)
            else -> animateTo(if (offset > anchors.maxPosition() / 2) PlanSheetValue.Collapsed else PlanSheetValue.Expanded)
        }
        return available
    }
}

/** The plan sheet with the day's stops, on the card's glass. */
@Preview(heightDp = 400)
@Composable
private fun PlanSheetPreview() {
    TripinglyTheme { PlanSheetPreviewContent() }
}

@Preview(heightDp = 400)
@Composable
private fun PlanSheetDarkPreview() {
    TripinglyTheme(darkTheme = true) { PlanSheetPreviewContent() }
}

@Composable
private fun PlanSheetPreviewContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        GlassSurface(modifier = Modifier.padding(MaterialTheme.spacing.medium), blur = false) {
            PlanSheet(plan = StopsCardPreviewStates().values.elementAt(1), state = rememberPlanSheetState()) { card ->
                StopsCard(card = card, dayNumber = 1, editorMenu = null, onAction = {})
            }
        }
    }
}

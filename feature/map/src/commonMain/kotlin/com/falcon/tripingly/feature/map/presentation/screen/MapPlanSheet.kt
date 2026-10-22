package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_plan_handle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** How much of the plan sheet shows while it's pulled down: its handle and its title row. */
internal val PlanSheetPeek = 72.dp

private val HandleAreaHeight = 24.dp

internal enum class PlanSheetValue { Collapsed, Expanded }

/**
 * The open day's plan in a sheet that rests on the day tabs. A drag anywhere on it pulls it up to show all of it, or down
 * to leave [PlanSheetPeek] of it; a tap on its handle does the same. It isn't modal: the map stays usable around it. The map keeps out of that peek only, so the sheet moves
 * without the map moving. [above] (the credits, the location button) rides on the sheet's top. Without [content] only
 * [above] shows.
 */
@Composable
internal fun PlanSheet(
    above: @Composable ColumnScope.() -> Unit,
    content: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state = rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(PlanSheetValue.Collapsed) }
    val peek = with(LocalDensity.current) { PlanSheetPeek.toPx() }
    val scope = rememberCoroutineScope()
    val handleLabel = stringResource(Res.string.map_plan_handle)
    // The pulled-down part of the sheet slides behind the day tabs.
    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        Column(
            modifier = Modifier.offset {
                val drop = if (content != null) state.offset.takeUnless { it.isNaN() } ?: 0f else 0f
                IntOffset(0, drop.roundToInt())
            },
        ) {
            above()
            if (content != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Dragging anywhere on the sheet pulls it; the plan scrolls only once it's pulled all the way up.
                        .nestedScroll(remember(state) { state.pullFirst() })
                        .anchoredDraggable(state, Orientation.Vertical)
                        .onSizeChanged { size ->
                            state.updateAnchors(
                                DraggableAnchors {
                                    PlanSheetValue.Expanded at 0f
                                    PlanSheetValue.Collapsed at (size.height - peek).coerceAtLeast(0f)
                                },
                            )
                        },
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 4.dp,
                ) {
                    Column {
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
                        Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                    }
                }
            }
        }
    }
}

/**
 * Lets a drag that starts on the plan's scrolling part move the sheet first: up until it's all shown, then the plan
 * scrolls; down once the plan is back at its top.
 */
private fun AnchoredDraggableState<PlanSheetValue>.pullFirst() = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0 && source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y < 0 && offset > anchors.minPosition()) {
            settle(available.y)
            available
        } else {
            Velocity.Zero
        }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle(available.y)
        return available
    }
}

package com.falcon.tripingly.feature.map.presentation.map.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
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
            LazyRow(state = listState, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
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

/** "Day 2" over its date; filled with the brand's green while open. */
@Composable
private fun DayPill(day: DayTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f))
    val content by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    Box(
        modifier = modifier
            .heightIn(min = DayPillHeight)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.map_day_label_no_date, day.number), style = MaterialTheme.typography.labelLarge, color = content)
            day.date?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = content) }
        }
    }
}

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

package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_filter_close
import com.falcon.tripingly.feature.map.generated.resources.map_filter_open
import com.falcon.tripingly.feature.map.presentation.map.CategoryChip
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PlaceFilterState
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * The map's filter at the top end, mirroring the join pill at the start: a glass circle that grows toward the start
 * into a pill of chips (All and every kind), its circle staying at the end as the close button (the canvas, Shahin
 * 2026-11-17). While kinds are picked the closed circle is lit, so a filtered map is never a surprise.
 */
@Composable
internal fun PlaceFilterPill(filter: PlaceFilterState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    GlassSurface(modifier = modifier, shape = CircleShape, blur = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = filter.isOpen,
                // The weight lets the chips take what room there is, scrolling within it.
                modifier = Modifier.weight(1f, fill = false),
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
            ) {
                CategoryChipRow(
                    chips = filter.chips,
                    onToggle = { onAction(MapAction.PlaceFilter.ToggleCategory(it)) },
                    // As far from the pill's start as the chips are from each other and from the circle.
                    edgePadding = MaterialTheme.spacing.extraSmall,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall),
                )
            }
            val label = stringResource(if (filter.isOpen) Res.string.map_filter_close else Res.string.map_filter_open)
            // The whole 40 dp circle is the button, as tall as the join pill; the lit disc sits 4 dp inside it.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ButtonSize)
                    .clip(CircleShape)
                    .clickable(onClickLabel = label, role = Role.Button) {
                        haptics.tick()
                        onAction(MapAction.PlaceFilter.Toggle)
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(CircleSize)
                        .background(if (filter.isActive && !filter.isOpen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape),
                ) {
                    Icon(
                        imageVector = if (filter.isOpen) Icons.Default.Close else Icons.Default.FilterList,
                        contentDescription = label,
                        tint = if (filter.isActive && !filter.isOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
        }
    }
}

/** As big as the join pill's faces, so the two circles under the header match. */
private val CircleSize = 32.dp
private val ButtonSize = 40.dp
private val IconSize = 20.dp

@Composable
private fun PlaceFilterPreviewContent() {
    val chips = persistentListOf(
        CategoryChip(null, null, isSelected = false),
        CategoryChip(PlaceCategory.Cafe, null, isSelected = true),
        CategoryChip(PlaceCategory.Museum, null, isSelected = false),
    )
    Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), horizontalAlignment = Alignment.End) {
        PlaceFilterPill(PlaceFilterState(isOpen = false, isActive = false, chips), onAction = {})
        PlaceFilterPill(PlaceFilterState(isOpen = false, isActive = true, chips), onAction = {})
        PlaceFilterPill(PlaceFilterState(isOpen = true, isActive = true, chips), onAction = {})
    }
}

@Preview
@Composable
private fun PlaceFilterPreview() {
    TripinglyTheme { PlaceFilterPreviewContent() }
}

@Preview
@Composable
private fun PlaceFilterDarkPreview() {
    TripinglyTheme(darkTheme = true) { PlaceFilterPreviewContent() }
}

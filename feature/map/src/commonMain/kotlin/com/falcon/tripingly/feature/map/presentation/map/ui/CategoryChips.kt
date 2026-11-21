package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.component.PillIconChip
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_category_clear
import com.falcon.tripingly.feature.map.generated.resources.map_category_count
import com.falcon.tripingly.feature.map.presentation.map.CategoryChip
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * Kinds of places to pick, one at a time, scrolling out to the edges: each kind with its icon and, where known, how many
 * there are ("Café 4"). A tap picks only that kind; tapping it again shows every kind. A round clear button stays at the
 * end while the kinds scroll, clearing the pick; it is never picked itself and dims while nothing is picked (Shahin,
 * 2026-11-21: one kind at a time, no All chip). Used by the route card, the map filter and Around. [chipHeight] makes the
 * chips smaller where the row sits in a slim pill.
 */
@Composable
internal fun CategoryChipRow(
    chips: ImmutableList<CategoryChip>,
    onToggle: (PlaceCategory?) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = MaterialTheme.spacing.small,
    chipHeight: Dp = Dp.Unspecified,
) {
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).horizontalFadingEdge(listState),
            // The clear button keeps the chips' gap to the last kind, which scrolls under the fade beside it.
            contentPadding = PaddingValues(start = edgePadding, end = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            items(chips, key = { it.category.name }) { chip ->
                CategoryChipItem(chip, onToggle, chipHeight, Modifier.animateItem())
            }
        }
        PillIconChip(
            icon = Icons.Outlined.FilterAltOff,
            contentDescription = stringResource(Res.string.map_category_clear),
            onClick = {
                haptics.tick()
                onToggle(null)
            },
            enabled = chips.any { it.isSelected },
            size = chipHeight.takeOrElse { ClearButtonSize },
            modifier = Modifier.padding(end = edgePadding),
        )
    }
}

@Composable
private fun CategoryChipItem(chip: CategoryChip, onToggle: (PlaceCategory?) -> Unit, chipHeight: Dp, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val name = stringResource(chip.category.label())
    PillChip(
        label = chip.count?.let { stringResource(Res.string.map_category_count, name, it) } ?: name,
        selected = chip.isSelected,
        onClick = {
            haptics.tick()
            onToggle(chip.category)
        },
        // The icon sits as far in from its edge as the label from the other, so the chip is even on both sides.
        leading = { Icon(chip.category.icon, contentDescription = null, modifier = Modifier.padding(start = MaterialTheme.spacing.medium - MaterialTheme.spacing.extraSmall).size(ChipIconSize)) },
        modifier = modifier.height(chipHeight),
    )
}

/** The chips' own height where the row doesn't set one. */
private val ClearButtonSize = 40.dp
private val ChipIconSize = 18.dp

@Composable
private fun CategoryChipsPreviewContent() {
    CategoryChipRow(
        chips = persistentListOf(
            CategoryChip(PlaceCategory.Cafe, 5, isSelected = true),
            CategoryChip(PlaceCategory.Museum, 4, isSelected = false),
            CategoryChip(PlaceCategory.Park, 3, isSelected = false),
        ),
        onToggle = {},
    )
}

@Preview
@Composable
private fun CategoryChipsPreview() {
    TripinglyTheme { CategoryChipsPreviewContent() }
}

@Preview
@Composable
private fun CategoryChipsDarkPreview() {
    TripinglyTheme(darkTheme = true) { CategoryChipsPreviewContent() }
}

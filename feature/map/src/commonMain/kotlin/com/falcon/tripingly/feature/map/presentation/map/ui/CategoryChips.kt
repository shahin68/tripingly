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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_category_all
import com.falcon.tripingly.feature.map.generated.resources.map_category_count
import com.falcon.tripingly.feature.map.presentation.map.CategoryChip
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * Kinds of places to pick, scrolling out to the edges: All first, then each kind with its icon and, where known, how many
 * there are ("Café 4"). One tap picks or unpicks a kind; All clears the pick. Used by the route card, the map filter and
 * Around (the canvas, Shahin 2026-11-17). [chipHeight] makes the chips smaller where the row sits in a slim pill.
 * [isAllPinned] keeps All at the start while the kinds scroll past it (the map filter, Shahin 2026-11-17).
 */
@Composable
internal fun CategoryChipRow(
    chips: ImmutableList<CategoryChip>,
    onToggle: (PlaceCategory?) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = MaterialTheme.spacing.small,
    chipHeight: Dp = Dp.Unspecified,
    isAllPinned: Boolean = false,
) {
    val listState = rememberLazyListState()
    val all = chips.firstOrNull { it.category == null }.takeIf { isAllPinned }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        all?.let { CategoryChipItem(it, onToggle, chipHeight, Modifier.padding(start = edgePadding)) }
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).horizontalFadingEdge(listState),
            // Pinned, All keeps the chips' gap to the first kind, which scrolls under the fade beside it.
            contentPadding = PaddingValues(start = if (all != null) MaterialTheme.spacing.small else edgePadding, end = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            items(chips.filter { it !== all }, key = { it.category?.name ?: ALL_KEY }) { chip ->
                CategoryChipItem(chip, onToggle, chipHeight, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun CategoryChipItem(chip: CategoryChip, onToggle: (PlaceCategory?) -> Unit, chipHeight: Dp, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val name = chip.category?.let { stringResource(it.label()) } ?: stringResource(Res.string.map_category_all)
    PillChip(
        label = chip.count?.let { stringResource(Res.string.map_category_count, name, it) } ?: name,
        selected = chip.isSelected,
        onClick = {
            haptics.tick()
            onToggle(chip.category)
        },
        // The icon sits as far in from its edge as the label from the other, so the chip is even on both sides.
        leading = chip.category?.let { category ->
            { Icon(category.icon, contentDescription = null, modifier = Modifier.padding(start = MaterialTheme.spacing.medium - MaterialTheme.spacing.extraSmall).size(ChipIconSize)) }
        },
        modifier = modifier.height(chipHeight),
    )
}

private const val ALL_KEY = "all"
private val ChipIconSize = 18.dp

@Composable
private fun CategoryChipsPreviewContent() {
    CategoryChipRow(
        chips = persistentListOf(
            CategoryChip(null, 12, isSelected = false),
            CategoryChip(PlaceCategory.Cafe, 5, isSelected = true),
            CategoryChip(PlaceCategory.Museum, 4, isSelected = false),
            CategoryChip(PlaceCategory.Park, 3, isSelected = false),
        ),
        onToggle = {},
        isAllPinned = true,
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

package com.falcon.tripingly.feature.social.presentation.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.appIcon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.config.ConfigItem
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_explore_period
import com.falcon.tripingly.feature.social.generated.resources.social_near_me
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Action
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * Explore's filters in the top bar: a pill naming the [picked] period ("This month", with a pin while Near me is on)
 * that opens a menu with Near me (on and off, the app's own) and one of the server's [periods] to pick.
 */
@Composable
internal fun ExplorePeriodMenu(
    periods: ImmutableList<ConfigItem>,
    picked: ConfigItem?,
    isNearbyFilter: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = picked?.label ?: stringResource(Res.string.social_near_me)
    val description = stringResource(Res.string.social_explore_period, label)
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier.heightIn(min = PillHeight).padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                if (isNearbyFilter) Icon(Icons.Outlined.NearMe, contentDescription = null, modifier = Modifier.size(IconSize))
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(IconSize))
            }
        }
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            actions = buildList {
                add(DropdownAction(stringResource(Res.string.social_near_me), icon = Icons.Outlined.NearMe, selected = isNearbyFilter) { onAction(Action.ToggleNearby) })
                periods.forEach { entry ->
                    add(DropdownAction(entry.label, icon = appIcon(entry.icon), selected = entry.key == picked?.key) { onAction(Action.PeriodSelected(entry.key)) })
                }
            },
        )
    }
}

private val PillHeight = 40.dp
private val IconSize = 18.dp

private val PreviewPeriods = persistentListOf(
    ConfigItem("week", "This week", 60),
    ConfigItem("month", "This month", 60, isDefault = true),
    ConfigItem("year", "This year", 60),
    ConfigItem("all", "All time", 61),
)

@Composable
private fun ExplorePeriodMenuPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        ExplorePeriodMenu(PreviewPeriods, PreviewPeriods[1], isNearbyFilter = false, onAction = {})
        ExplorePeriodMenu(PreviewPeriods, PreviewPeriods[0], isNearbyFilter = true, onAction = {})
    }
}

@Preview
@Composable
private fun ExplorePeriodMenuPreview() {
    TripinglyTheme { ExplorePeriodMenuPreviewContent() }
}

@Preview
@Composable
private fun ExplorePeriodMenuDarkPreview() {
    TripinglyTheme(darkTheme = true) { ExplorePeriodMenuPreviewContent() }
}

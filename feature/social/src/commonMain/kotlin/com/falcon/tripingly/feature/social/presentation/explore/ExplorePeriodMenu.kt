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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_explore_all
import com.falcon.tripingly.feature.social.generated.resources.social_explore_fall
import com.falcon.tripingly.feature.social.generated.resources.social_explore_month
import com.falcon.tripingly.feature.social.generated.resources.social_explore_period
import com.falcon.tripingly.feature.social.generated.resources.social_explore_spring
import com.falcon.tripingly.feature.social.generated.resources.social_explore_summer
import com.falcon.tripingly.feature.social.generated.resources.social_explore_week
import com.falcon.tripingly.feature.social.generated.resources.social_explore_winter
import com.falcon.tripingly.feature.social.generated.resources.social_explore_year
import com.falcon.tripingly.feature.social.generated.resources.social_near_me
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Action
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Explore's filters in the top bar: a pill naming the period ("This month", with a pin while Near me is on) that
 * opens a menu with Near me (on and off) and one period to pick.
 */
@Composable
internal fun ExplorePeriodMenu(period: ExplorePeriod, isNearbyFilter: Boolean, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(period.label())
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
                ExplorePeriod.entries.forEach { entry ->
                    add(DropdownAction(stringResource(entry.label()), icon = entry.icon, selected = entry == period) { onAction(Action.PeriodSelected(entry)) })
                }
            },
        )
    }
}

private val ExplorePeriod.icon: ImageVector
    get() = when (this) {
        ExplorePeriod.Week, ExplorePeriod.Month, ExplorePeriod.Year -> Icons.Outlined.CalendarMonth
        ExplorePeriod.Season -> Icons.Outlined.WbSunny
        ExplorePeriod.All -> Icons.Outlined.History
    }

private fun ExplorePeriod.label(): StringResource = when (this) {
    ExplorePeriod.Week -> Res.string.social_explore_week
    ExplorePeriod.Month -> Res.string.social_explore_month
    ExplorePeriod.Year -> Res.string.social_explore_year
    ExplorePeriod.Season -> currentSeasonLabel()
    ExplorePeriod.All -> Res.string.social_explore_all
}

/** The season the server filters by: meteorological, so fall is September to November. */
private fun currentSeasonLabel(): StringResource = when (Clock.System.todayIn(TimeZone.UTC).month) {
    Month.MARCH, Month.APRIL, Month.MAY -> Res.string.social_explore_spring
    Month.JUNE, Month.JULY, Month.AUGUST -> Res.string.social_explore_summer
    Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> Res.string.social_explore_fall
    Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Res.string.social_explore_winter
}

private val PillHeight = 40.dp
private val IconSize = 18.dp

@Composable
private fun ExplorePeriodMenuPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        ExplorePeriodMenu(ExplorePeriod.Month, isNearbyFilter = false, onAction = {})
        ExplorePeriodMenu(ExplorePeriod.Week, isNearbyFilter = true, onAction = {})
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

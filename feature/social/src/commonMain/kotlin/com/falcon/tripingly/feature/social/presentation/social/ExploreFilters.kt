package com.falcon.tripingly.feature.social.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_explore_all
import com.falcon.tripingly.feature.social.generated.resources.social_explore_fall
import com.falcon.tripingly.feature.social.generated.resources.social_explore_filters
import com.falcon.tripingly.feature.social.generated.resources.social_explore_month
import com.falcon.tripingly.feature.social.generated.resources.social_explore_spring
import com.falcon.tripingly.feature.social.generated.resources.social_explore_summer
import com.falcon.tripingly.feature.social.generated.resources.social_explore_week
import com.falcon.tripingly.feature.social.generated.resources.social_explore_winter
import com.falcon.tripingly.feature.social.generated.resources.social_explore_year
import com.falcon.tripingly.feature.social.generated.resources.social_nearby
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Action
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.State
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Explore's filters in one scrolling row: Nearby switches on and off, then one period is picked. */
@Composable
internal fun ExploreFilters(state: State, onAction: (Action) -> Unit) {
    val description = stringResource(Res.string.social_explore_filters)
    LazyRow(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        item {
            PillChip(
                label = stringResource(Res.string.social_nearby),
                onClick = { onAction(Action.OnToggleExploreNearby) },
                selected = state.isExploreNearby,
                leading = {
                    Icon(
                        Icons.Default.NearMe,
                        contentDescription = null,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.small).size(18.dp),
                    )
                },
            )
        }
        items(ExplorePeriod.entries, key = { it.name }) { period ->
            PillChip(
                label = stringResource(period.label()),
                onClick = { onAction(Action.OnExplorePeriodSelected(period)) },
                selected = period == state.explorePeriod,
            )
        }
    }
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

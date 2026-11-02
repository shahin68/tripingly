package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_blocked
import com.falcon.tripingly.feature.settings.generated.resources.settings_blocked_empty
import com.falcon.tripingly.feature.settings.generated.resources.settings_blocked_hint
import com.falcon.tripingly.feature.settings.generated.resources.settings_unblock
import com.falcon.tripingly.feature.settings.presentation.BlockedPeopleViewModel.Action
import com.falcon.tripingly.feature.settings.presentation.BlockedPeopleViewModel.State
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** The people I blocked, each with Unblock; while the first page loads, placeholder rows as a [Skeleton]. */
@Composable
internal fun BlockedPeopleSheet(state: State, onAction: (Action) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(title = stringResource(Res.string.settings_blocked), subtitle = stringResource(Res.string.settings_blocked_hint), onClose = onClose)
        val people = state.people
        when {
            state.loadError != null -> ErrorScreen(onRetry = { onAction(Action.Retry) }, message = state.loadError.asString())
            people == null -> Skeleton { Column { PlaceholderPeople.forEach { BlockedPersonRow(it, onUnblock = {}) } } }
            people.isEmpty() -> Text(
                stringResource(Res.string.settings_blocked_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MaterialTheme.spacing.large),
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = MaterialTheme.spacing.large)) {
                items(people, key = { it.id }) { user ->
                    BlockedPersonRow(user, onUnblock = { onAction(Action.Unblock(user)) }, Modifier.animateItem())
                }
                if (state.hasMore) item(key = "more") { LaunchedEffect(Unit) { onAction(Action.LoadMore) } }
            }
        }
    }
}

@Composable
private fun BlockedPersonRow(user: UserSummary, onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(user, size = AvatarSize.Row)
        Column(Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        }
        SecondaryButton(stringResource(Res.string.settings_unblock), onUnblock, size = ButtonSize.Small)
    }
}

private val RowHeight = 64.dp
private val PlaceholderPeople = listOf(UserSummary("p1", "traveller", "Alex Traveller"), UserSummary("p2", "explorer", "Sam Explorer"))

/** Loading, two people, and nobody. */
internal class BlockedPeoplePreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(isOpen = true),
        State(isOpen = true, people = persistentListOf(UserSummary("1", "anna", "Anna"), UserSummary("2", "jonas.k", "Jonas"))),
        State(isOpen = true, people = persistentListOf()),
    )
}

@Preview
@Composable
private fun BlockedPeopleSheetPreview(@PreviewParameter(BlockedPeoplePreviewStates::class) state: State) {
    TripinglyTheme { Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { BlockedPeopleSheet(state, onAction = {}, onClose = {}) } }
}

@Preview
@Composable
private fun BlockedPeopleSheetDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { BlockedPeopleSheet(BlockedPeoplePreviewStates().values.elementAt(1), onAction = {}, onClose = {}) }
    }
}

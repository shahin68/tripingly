package com.falcon.tripingly.feature.social.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.PersonRow
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.Tag
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PeoplePage
import com.falcon.tripingly.core.model.social.Person
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_follows_you
import com.falcon.tripingly.feature.social.generated.resources.social_friend_tag
import com.falcon.tripingly.feature.social.generated.resources.social_people_see_all
import com.falcon.tripingly.feature.social.generated.resources.social_people_tip_body
import com.falcon.tripingly.feature.social.generated.resources.social_people_tip_close
import com.falcon.tripingly.feature.social.generated.resources.social_people_tip_search
import com.falcon.tripingly.feature.social.generated.resources.social_people_tip_title
import com.falcon.tripingly.feature.social.presentation.component.FollowButton
import com.falcon.tripingly.feature.social.presentation.component.previewFoundPeople
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.Action
import com.falcon.tripingly.feature.social.presentation.search.SearchViewModel.PeopleState
import org.jetbrains.compose.resources.stringResource

/*
 * People in the Search tab (the rename canvas, Shahin 2026-11-24): a search shows its best [TOP_PEOPLE] matches as rows
 * above the spots, with "See all" opening the people sheet (layout A); until dismissed, a tip card on top of the spots
 * says people can be searched too (hint 3).
 */

/** How many of the people a search found show above its spots. */
internal const val TOP_PEOPLE = 3

/** The search's best people in a box above the spots, and See all when there are more; none found, no box. */
internal fun LazyGridScope.topPeople(people: PeopleState, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit) {
    if (people.people.isEmpty()) return
    fullWidthItem("people") { TopPeople(people, onAction, onOpenProfile, Modifier.animateItem()) }
}

/** The "Travel with friends" tip on top of the spots, while [isShown]; dismissed, the grid glides up into its place. */
internal fun LazyGridScope.peopleTip(isShown: Boolean, onAction: (Action) -> Unit) {
    if (!isShown) return
    fullWidthItem("peopleTip") {
        PeopleTipCard(onSearch = { onAction(Action.SearchPeople) }, onDismiss = { onAction(Action.DismissPeopleTip) }, Modifier.animateItem())
    }
}

/**
 * The search's best people in a box. The rows sit 12 dp in from the box's sides, as far as the first avatar is from its
 * top (the box's 4 dp and the row's own 8 dp), so the avatars and Follow buttons are as far in from the sides as from the
 * top and bottom.
 */
@Composable
internal fun TopPeople(people: PeopleState, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit, modifier: Modifier = Modifier) {
    val inset = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = MaterialTheme.spacing.extraSmall)) {
            people.people.take(TOP_PEOPLE).forEach { person ->
                key(person.user.id) { FoundPersonRow(person, onAction, onOpenProfile, inset) }
            }
            if (people.total > TOP_PEOPLE) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SeeAllPeople(people.total, inset, onClick = { onAction(Action.OpenPeople) })
            }
        }
    }
}

/**
 * Someone a search found, [inset] in from both sides: name, @handle, Friend (we follow each other) or Follows you, and
 * Follow / Following. Friend and Follows you switch with the Follow tap at once. A tap elsewhere opens their profile.
 */
@Composable
internal fun FoundPersonRow(person: Person, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit, inset: Dp, modifier: Modifier = Modifier) {
    PersonRow(
        user = person.user,
        detail = "@${person.user.username}",
        // PersonRow pads its own 4 dp.
        modifier = modifier.clickable { onOpenProfile(person.user.username) }.padding(horizontal = inset - MaterialTheme.spacing.extraSmall),
    ) {
        when {
            person.isFriend -> Tag(stringResource(Res.string.social_friend_tag))
            person.followsMe -> Tag(stringResource(Res.string.social_follows_you))
        }
        FollowButton(person.isFollowing, onToggle = { onAction(Action.ToggleFollow(person)) }, size = ButtonSize.Small)
    }
}

/** "See all 42 people ›" (up to "999+"), opening the people sheet; the text starts under the avatars, the arrow ends under Follow. */
@Composable
private fun SeeAllPeople(total: Int, inset: Dp, onClick: () -> Unit) {
    val count = if (total >= PeoplePage.TOTAL_CAP) "${PeoplePage.TOTAL_CAP}+" else total.toString()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.extraLarge + MaterialTheme.spacing.medium)
            .clickable(onClick = onClick).padding(horizontal = inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            stringResource(Res.string.social_people_see_all, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The tip in the Ivy container colour: title, one line on what can be found, Search people (opens the search field) and
 * ✕ at the top end. Everything sits [MaterialTheme.spacing.medium] in on every side; the ✕ button keeps its touch area,
 * and the 4 dp around it put its glyph that far in too.
 */
@Composable
internal fun PeopleTipCard(onSearch: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                Text(
                    stringResource(Res.string.social_people_tip_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    // Clear of the ✕: its 24 dp glyph and a gap.
                    modifier = Modifier.padding(end = MaterialTheme.spacing.large + MaterialTheme.spacing.small),
                )
                Text(stringResource(Res.string.social_people_tip_body), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(
                    text = stringResource(Res.string.social_people_tip_search),
                    onClick = onSearch,
                    size = ButtonSize.Small,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacing.extraSmall)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.social_people_tip_close))
            }
        }
    }
}

@Composable
private fun PeoplePartsPreviewContent() {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        PeopleTipCard(onSearch = {}, onDismiss = {})
        TopPeople(previewFoundPeople, onAction = {}, onOpenProfile = {})
        TopPeople(previewFoundPeople.copy(people = previewFoundPeople.people.take(2), total = 2), onAction = {}, onOpenProfile = {})
    }
}

@Preview
@Composable
private fun PeoplePartsPreview() {
    TripinglyTheme { PeoplePartsPreviewContent() }
}

@Preview
@Composable
private fun PeoplePartsDarkPreview() {
    TripinglyTheme(darkTheme = true) { PeoplePartsPreviewContent() }
}

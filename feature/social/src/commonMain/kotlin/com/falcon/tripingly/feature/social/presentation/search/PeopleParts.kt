package com.falcon.tripingly.feature.social.presentation.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.Bloom
import com.falcon.tripingly.core.designsystem.component.BloomState
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.PersonRow
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.Tag
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
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
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/*
 * People in the Search tab (the rename canvas, Shahin 2026-11-24): a search shows its best [TOP_PEOPLE] matches as rows
 * above the spots, with "See all" opening the people sheet (layout A); until dismissed, a tip card on top of the spots
 * says people can be searched too (hint 3).
 */

/** How many of the people a search found show above its spots. */
internal const val TOP_PEOPLE = 3

/**
 * The search's best [people] (null while not searching) as the grid's first items, each in a card of its own, and a See
 * all card when there are more (Shahin, 2026-11-24): grid items like the tiles, so they fade in and out and the spots
 * glide along, scrolled or not. Each card spans three columns ([PEOPLE_SPAN]): the whole width on a phone, two side by
 * side in landscape ([columns] of the grid), where See all is as high as a person's card beside it (Shahin, 2026-11-24).
 */
internal fun LazyGridScope.topPeople(people: PeopleState?, columns: Int, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit) {
    if (people == null) return
    val top = people.people.take(TOP_PEOPLE)
    items(top, key = { "person:${it.user.id}" }, span = { peopleSpan() }) { person ->
        PeopleCard(onClick = { onOpenProfile(person.user.username) }, Modifier.animateItem()) {
            FoundPersonRow(person, person.user.id in people.pendingFollows, onAction, PeopleInset)
        }
    }
    if (people.total > TOP_PEOPLE && top.isNotEmpty()) {
        item(key = "people:seeAll", span = { peopleSpan() }) {
            PeopleCard(onClick = { onAction(Action.OpenPeople) }, Modifier.animateItem(), padded = false) {
                val seeAll = { modifier: Modifier -> SeeAllPeople(people.total, PeopleInset, modifier) }
                if (columns * UNITS_PER_COLUMN < 2 * PEOPLE_SPAN) {
                    seeAll(Modifier)
                } else {
                    Box {
                        // Only the room of a person's card: a row laid out, never drawn, read out or tapped.
                        Box(Modifier.alpha(0f).clearAndSetSemantics {}.padding(vertical = MaterialTheme.spacing.extraSmall)) {
                            PersonRow(top.first().user, detail = "@${top.first().user.username}")
                        }
                        seeAll(Modifier.matchParentSize())
                    }
                }
            }
        }
    }
}

/** The "Travel with friends" tip on top of the spots: it blooms out of its top end and, dismissed, goes back into its ✕. */
internal fun LazyGridScope.peopleTip(bloom: BloomState, onAction: (Action) -> Unit) {
    if (!bloom.isShown) return
    fullWidthItem("peopleTip") {
        Bloom(bloom, CardRadius, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
            PeopleTipCard(onSearch = { onAction(Action.SearchPeople) }, onDismiss = { onAction(Action.DismissPeopleTip) })
        }
    }
}

/** The tip's corners, [MaterialTheme.shapes] large: the bloom keeps them while it grows. */
private val CardRadius = 20.dp

/** A people card's span: three columns, or the whole line if it is narrower. */
private fun LazyGridItemSpanScope.peopleSpan() = GridItemSpan(minOf(PEOPLE_SPAN, maxLineSpan))

private const val PEOPLE_SPAN = 3 * UNITS_PER_COLUMN

/**
 * How far a person's row sits in from its card's sides: as far as the avatar is from its top (the card's 4 dp and the
 * row's own 8 dp), so the avatar and Follow are as far in from the sides as from the top and bottom.
 */
private val PeopleInset = 12.dp

/**
 * A card of the people: the box colour and corners with the glass's edge, 4 dp above and under a person's row ([padded]; none around See all,
 * whose own height centres it). The whole card is the tap ([onClick]): its ripple fills it edge to edge, inside its corners.
 */
@Composable
private fun PeopleCard(onClick: () -> Unit, modifier: Modifier = Modifier, padded: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        // The glass's thin light edge, as the bars have (Shahin, 2026-11-24).
        border = BorderStroke(GlassDefaults.Edge, MaterialTheme.extendedColors.glassEdge),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(vertical = if (padded) MaterialTheme.spacing.extraSmall else 0.dp)) { content() }
    }
}

/**
 * Someone a search found, [inset] in from both sides: name, @handle, Friend (we follow each other) or Follows you, and
 * Follow / Following, spinning while a tap waits for the server ([isFollowPending]); Friend and Follows you switch with it
 * once the server took it. Whatever holds it opens their profile on a tap elsewhere (a card, the sheet's row), so the
 * ripple fills that.
 */
@Composable
internal fun FoundPersonRow(
    person: Person,
    isFollowPending: Boolean,
    onAction: (Action) -> Unit,
    inset: Dp,
    modifier: Modifier = Modifier,
) {
    PersonRow(
        user = person.user,
        detail = "@${person.user.username}",
        // PersonRow pads its own 4 dp.
        modifier = modifier.padding(horizontal = inset - MaterialTheme.spacing.extraSmall),
    ) {
        when {
            person.isFriend -> Tag(stringResource(Res.string.social_friend_tag))
            person.followsMe -> Tag(stringResource(Res.string.social_follows_you))
        }
        FollowButton(person.isFollowing, onToggle = { onAction(Action.ToggleFollow(person)) }, size = ButtonSize.Small, isPending = isFollowPending)
    }
}

/** "See all 42 people ›" (up to "999+"), opening the people sheet; the text starts under the avatars, the arrow ends under Follow. */
@Composable
private fun SeeAllPeople(total: Int, inset: Dp, modifier: Modifier = Modifier) {
    val count = if (total >= PeoplePage.TOTAL_CAP) "${PeoplePage.TOTAL_CAP}+" else total.toString()
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.extraLarge + MaterialTheme.spacing.medium)
            .padding(horizontal = inset),
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
        // The cards as the grid lays them out: with See all, and two people without.
        LazyVerticalGrid(GridCells.Fixed(1), userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) { topPeople(previewFoundPeople, columns = 3, onAction = {}, onOpenProfile = {}) }
        LazyVerticalGrid(GridCells.Fixed(1), userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            topPeople(previewFoundPeople.copy(people = previewFoundPeople.people.take(2).toImmutableList(), total = 2), columns = 3, onAction = {}, onOpenProfile = {})
        }
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

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PeoplePartsLandscapePreview() {
    TripinglyTheme { PeoplePartsPreviewContent() }
}

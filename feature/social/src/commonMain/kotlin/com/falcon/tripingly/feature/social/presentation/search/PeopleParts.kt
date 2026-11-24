package com.falcon.tripingly.feature.social.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.Bloom
import com.falcon.tripingly.core.designsystem.component.BloomState
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
 * The search's best [people] (null while not searching) above the spots, and See all when there are more: each row is
 * its own grid item (the people card canvas, B, Shahin 2026-11-24), so the grid's own item motion fades rows in and out
 * and slides the spots along, as with the tiles, scrolled or not. Together the rows look like one box: the first has its
 * top corners, the last its bottom corners, and no gap between them ([PeopleBoxPart]).
 */
internal fun LazyGridScope.topPeople(people: PeopleState?, onAction: (Action) -> Unit, onOpenProfile: (String) -> Unit) {
    if (people == null) return
    val top = people.people.take(TOP_PEOPLE)
    val hasMore = people.total > TOP_PEOPLE
    top.forEachIndexed { index, person ->
        fullWidthItem("person:${person.user.id}") {
            PeopleBoxPart(isFirst = index == 0, isLast = !hasMore && index == top.lastIndex, Modifier.animateItem()) {
                FoundPersonRow(person, person.user.id in people.pendingFollows, onAction, onOpenProfile, PeopleInset)
            }
        }
    }
    if (hasMore && top.isNotEmpty()) {
        fullWidthItem("people:seeAll") {
            PeopleBoxPart(isFirst = false, isLast = true, Modifier.animateItem(), isSeeAll = true) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = PeopleInset, end = PeopleInset, top = MaterialTheme.spacing.small),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SeeAllPeople(people.total, PeopleInset, onClick = { onAction(Action.OpenPeople) })
                }
            }
        }
    }
}

/** The "Travel with friends" tip on top of the spots: it blooms out of its top end and, dismissed, goes back into its ✕. */
internal fun LazyGridScope.peopleTip(bloom: BloomState, onAction: (Action) -> Unit) {
    if (!bloom.isShown) return
    fullWidthItem("peopleTip") {
        // The grid has no gap between rows of its own: the tip keeps the one under it, and takes it along as it goes.
        Bloom(bloom, CardRadius, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null), padding = PaddingValues(bottom = MaterialTheme.spacing.small)) {
            PeopleTipCard(onSearch = { onAction(Action.SearchPeople) }, onDismiss = { onAction(Action.DismissPeopleTip) })
        }
    }
}

/** The tip's corners, [MaterialTheme.shapes] large: the bloom keeps them while it grows. */
private val CardRadius = 20.dp

/**
 * How far the people rows sit in from the box's sides: as far as the first avatar is from its top (the box's 4 dp and
 * the row's own 8 dp), so the avatars and Follow buttons are as far in from the sides as from the top and bottom.
 */
private val PeopleInset = 12.dp

/**
 * One part of the people box: the box's colour, its top corners on the [isFirst] part with 4 dp above the row, its bottom
 * corners on the [isLast] with 4 dp under the row (none under See all, [isSeeAll], so its ripple fills the bottom
 * corners), and the grid's gap after the box. The rows' ripples stay inside the box's corners.
 */
@Composable
private fun PeopleBoxPart(isFirst: Boolean, isLast: Boolean, modifier: Modifier = Modifier, isSeeAll: Boolean = false, content: @Composable () -> Unit) {
    val box = MaterialTheme.shapes.large
    val square = CornerSize(0.dp)
    val shape = box.copy(
        topStart = if (isFirst) box.topStart else square,
        topEnd = if (isFirst) box.topEnd else square,
        bottomEnd = if (isLast) box.bottomEnd else square,
        bottomStart = if (isLast) box.bottomStart else square,
    )
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(bottom = if (isLast) MaterialTheme.spacing.small else 0.dp),
    ) {
        Box(
            Modifier.padding(
                top = if (isFirst) MaterialTheme.spacing.extraSmall else 0.dp,
                bottom = if (isLast && !isSeeAll) MaterialTheme.spacing.extraSmall else 0.dp,
            ),
        ) { content() }
    }
}

/**
 * Someone a search found, [inset] in from both sides: name, @handle, Friend (we follow each other) or Follows you, and
 * Follow / Following, spinning while a tap waits for the server ([isFollowPending]); Friend and Follows you switch with it
 * once the server took it. A tap elsewhere opens their profile.
 */
@Composable
internal fun FoundPersonRow(
    person: Person,
    isFollowPending: Boolean,
    onAction: (Action) -> Unit,
    onOpenProfile: (String) -> Unit,
    inset: Dp,
    modifier: Modifier = Modifier,
) {
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
        FollowButton(person.isFollowing, onToggle = { onAction(Action.ToggleFollow(person)) }, size = ButtonSize.Small, isPending = isFollowPending)
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
        // The rows as the grid lays them out: with See all, and two people without.
        LazyVerticalGrid(GridCells.Fixed(1), userScrollEnabled = false) { topPeople(previewFoundPeople, onAction = {}, onOpenProfile = {}) }
        LazyVerticalGrid(GridCells.Fixed(1), userScrollEnabled = false) {
            topPeople(previewFoundPeople.copy(people = previewFoundPeople.people.take(2).toImmutableList(), total = 2), onAction = {}, onOpenProfile = {})
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

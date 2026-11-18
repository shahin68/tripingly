package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.NewIndicator
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_add_button
import com.falcon.tripingly.feature.trips.generated.resources.members_add_friend
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_share
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_subtitle
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_title
import com.falcon.tripingly.feature.trips.generated.resources.members_join_accept
import com.falcon.tripingly.feature.trips.generated.resources.members_join_decline
import com.falcon.tripingly.feature.trips.generated.resources.members_more
import com.falcon.tripingly.feature.trips.generated.resources.members_remove
import com.falcon.tripingly.feature.trips.generated.resources.members_role_editor
import com.falcon.tripingly.feature.trips.generated.resources.members_role_owner
import org.jetbrains.compose.resources.stringResource

/*
 * The members sheet's parts (design: the members sheet canvas, board "Search icon in the header", Shahin 2026-11-18):
 * cards of people, a friend's face that adds them, and the invite link pill.
 */

/**
 * A card in a lazy list: its [title] on top, then one item per entry of [entries] (each animating in place), the card's
 * round corners on its first and last item. Nothing but the title shows while [entries] is empty, unless [empty] does.
 */
internal fun <T> LazyListScope.card(
    key: String,
    title: @Composable () -> Unit,
    entries: List<T>,
    entryKey: (T) -> Any,
    empty: (@Composable () -> Unit)? = null,
    entry: @Composable (T) -> Unit,
) {
    item(key = "$key:title") { CardPart(isFirst = true, isLast = entries.isEmpty() && empty == null, Modifier.animateItem()) { title() } }
    if (entries.isEmpty() && empty != null) item(key = "$key:empty") { CardPart(isFirst = false, isLast = true, Modifier.animateItem()) { empty() } }
    itemsIndexed(entries, key = { _, it -> "$key:${entryKey(it)}" }) { index, it ->
        CardPart(isFirst = false, isLast = index == entries.lastIndex, Modifier.animateItem()) { entry(it) }
    }
    item(key = "$key:gap") { Spacer(Modifier.padding(top = MaterialTheme.spacing.small)) }
}

/** One slice of a card: the card's colour, its top corners on the [isFirst] slice and its bottom ones on the [isLast]. */
@Composable
internal fun CardPart(isFirst: Boolean, isLast: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val radius = CardRadius
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .background(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                RoundedCornerShape(
                    topStart = if (isFirst) radius else 0.dp,
                    topEnd = if (isFirst) radius else 0.dp,
                    bottomStart = if (isLast) radius else 0.dp,
                    bottomEnd = if (isLast) radius else 0.dp,
                ),
            )
            .padding(
                start = CardPadding,
                end = CardPadding,
                top = if (isFirst) CardPadding else 0.dp,
                bottom = if (isLast) CardPadding - MaterialTheme.spacing.extraSmall else 0.dp,
            ),
    ) { content() }
}

/** A card's title; [hasNews] puts the app's red dot before it (people asking to join). */
@Composable
internal fun CardTitle(text: String, hasNews: Boolean = false) {
    Row(
        modifier = Modifier.heightIn(min = CardTitleHeight).padding(horizontal = MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (hasNews) NewIndicator(strokeWidth = 0.dp)
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.skeletonText())
    }
}

/** A person in a card: their avatar, name and [detail] ("@lena · yesterday"), and [actions] at the end. */
@Composable
internal fun PersonRow(
    user: UserSummary,
    detail: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = PersonRowHeight).padding(horizontal = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(user, size = AvatarSize.Row)
        Column(Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        }
        actions()
    }
}

/** Someone on the trip: their role as a tag, and for an editor the owner can remove, ⋮ with Remove. */
@Composable
internal fun MemberRow(member: TripMember, canRemove: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier, reserveMenu: Boolean = canRemove) {
    val isOwner = member.role == TripRole.OWNER
    PersonRow(user = member.user, detail = "@${member.user.username}", modifier = modifier) {
        RoleTag(isOwner)
        when {
            canRemove -> MemberMenu(member.user, onRemove)
            // Every tag ends at the same place, with or without ⋮ after it.
            reserveMenu -> Spacer(Modifier.width(MenuButtonWidth))
        }
    }
}

/** Owner or Editor; both labels are laid out in every tag, so the two pills are the same size in every language. */
@Composable
private fun RoleTag(isOwner: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .skeleton(CircleShape)
            .clip(CircleShape)
            .background(if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, vertical = MaterialTheme.spacing.extraSmall),
    ) {
        val color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        RoleLabel(stringResource(Res.string.members_role_owner), color, isShown = isOwner)
        RoleLabel(stringResource(Res.string.members_role_editor), color, isShown = !isOwner)
    }
}

/** A hidden label still takes its room (and isn't read out), so it only sets the tag's width. */
@Composable
private fun RoleLabel(text: String, color: Color, isShown: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = if (isShown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
    )
}

@Composable
private fun MemberMenu(user: UserSummary, onRemove: () -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.members_more, user.displayName), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppDropdownMenu(
            expanded = isOpen,
            onDismissRequest = { isOpen = false },
            actions = listOf(DropdownAction(stringResource(Res.string.members_remove), isDestructive = true, icon = Icons.Outlined.PersonRemove, onClick = onRemove)),
        )
    }
}

/** A request to join with round ✕ and ✓; both wait while the answer is on its way ([isAnswering]). */
@Composable
internal fun JoinRequestRow(request: JoinRequest, detail: String, isAnswering: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    PersonRow(user = request.user, detail = detail, modifier = modifier) {
        OutlinedIconButton(onClick = onDecline, enabled = !isAnswering, modifier = Modifier.skeleton(CircleShape)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.members_join_decline))
        }
        FilledIconButton(onClick = onAccept, enabled = !isAnswering, modifier = Modifier.skeleton(CircleShape)) {
            Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.members_join_accept))
        }
    }
}

/** Friends who aren't on the trip, as faces with a +: a tap adds them at once. */
@Composable
internal fun FriendFaces(friends: List<UserSummary>, onAdd: (UserSummary) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        items(friends, key = { it.id }) { friend -> FriendFace(friend, onAdd = { onAdd(friend) }, Modifier.animateItem()) }
    }
}

@Composable
internal fun FriendFace(friend: UserSummary, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.members_add_friend, friend.displayName)
    Column(
        modifier = modifier
            .width(FaceWidth)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onAdd)
            .padding(vertical = MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Box {
            AvatarCircle(friend, size = AvatarSize.Medium)
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(FaceBadge)
                    .skeleton(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                    .padding(FaceBadgeRing)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(FaceBadgeRing),
            )
        }
        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.skeletonText(),
        )
    }
}

/** Someone found on the Add people page: Add, or where they already are ([status], e.g. "On this trip"). */
@Composable
internal fun FoundPersonRow(user: UserSummary, status: String?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    PersonRow(user = user, detail = "@${user.username}", modifier = modifier) {
        if (status != null) {
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SecondaryButton(text = stringResource(Res.string.members_add_button), onClick = onAdd, size = ButtonSize.Small)
        }
    }
}

/** The invite link, pinned under the cards: its link icon, what it does, and Share (which makes a link first if needed). */
@Composable
internal fun InvitePill(isCreating: Boolean, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.small, top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        ) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.members_invite_title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.members_invite_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            PrimaryButton(text = stringResource(Res.string.members_invite_share), onClick = onShare, isLoading = isCreating, size = ButtonSize.Small)
        }
    }
}

private val CardRadius = 20.dp
private val CardPadding = 12.dp
private val CardTitleHeight = 32.dp
private val PersonRowHeight = 56.dp
private val MenuButtonWidth = 48.dp
private val FaceWidth = 72.dp
private val FaceBadge = 24.dp
private val FaceBadgeRing = 2.dp

/** A request, the owner and an editor the owner can remove, a friend to add and the invite pill. */
@Preview
@Composable
private fun MemberRowsPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = MaterialTheme.spacing.medium)) {
            CardPart(isFirst = true, isLast = false) { CardTitle("Asking to join · 1", hasNews = true) }
            CardPart(isFirst = false, isLast = true) { JoinRequestRow(previewJoinRequest, "@mia · yesterday", isAnswering = false, onAccept = {}, onDecline = {}) }
            Spacer(Modifier.padding(top = MaterialTheme.spacing.small))
            CardPart(isFirst = true, isLast = false) { CardTitle("On this trip · 2") }
            previewMembers.forEachIndexed { index, member ->
                CardPart(isFirst = false, isLast = index == previewMembers.lastIndex) {
                    MemberRow(member = member, canRemove = member.role != TripRole.OWNER, onRemove = {}, reserveMenu = true)
                }
            }
            Spacer(Modifier.padding(top = MaterialTheme.spacing.small))
            CardPart(isFirst = true, isLast = true) { FriendFaces(previewFriends, onAdd = {}) }
            InvitePill(isCreating = false, onShare = {}, modifier = Modifier.padding(MaterialTheme.spacing.medium))
        }
    }
}

@Preview
@Composable
private fun MemberRowsDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = MaterialTheme.spacing.medium)) {
            CardPart(isFirst = true, isLast = true) { JoinRequestRow(previewJoinRequest, "@mia · yesterday", isAnswering = true, onAccept = {}, onDecline = {}) }
            CardPart(isFirst = true, isLast = true) { FoundPersonRow(previewFriends.first(), status = null, onAdd = {}) }
            InvitePill(isCreating = true, onShare = {}, modifier = Modifier.padding(MaterialTheme.spacing.medium))
        }
    }
}

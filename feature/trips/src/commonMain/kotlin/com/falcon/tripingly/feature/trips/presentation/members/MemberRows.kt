package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_expires
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_revoke
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_share
import com.falcon.tripingly.feature.trips.generated.resources.members_join_accept
import com.falcon.tripingly.feature.trips.generated.resources.members_join_decline
import com.falcon.tripingly.feature.trips.generated.resources.members_remove
import com.falcon.tripingly.feature.trips.generated.resources.members_role_editor
import com.falcon.tripingly.feature.trips.generated.resources.members_role_owner
import org.jetbrains.compose.resources.stringResource

/* The members sheet's rows: people, the username field and invite links. */

/** A person in the list: their avatar, name and [detail] ("@lena · Owner"), and [actions] at the end. */
@Composable
private fun PersonRow(
    user: UserSummary,
    detail: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = PersonRowHeight).padding(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(user, size = AvatarSize.Small)
        Column(Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        }
        actions()
    }
}

/** A member row that's still loading: the row itself with a placeholder person, as a [Skeleton]. */
@Composable
internal fun MemberSkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier) { MemberRow(PlaceholderMember, canRemove = false, onRemove = {}) }
}

private val PlaceholderMember = TripMember(UserSummary("placeholder", "traveller", "Alex Traveller"), TripRole.EDITOR)

@Composable
internal fun MemberRow(member: TripMember, canRemove: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val role = stringResource(if (member.role == TripRole.OWNER) Res.string.members_role_owner else Res.string.members_role_editor)
    PersonRow(user = member.user, detail = "@${member.user.username} · $role", modifier = modifier) {
        if (canRemove) {
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.members_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun JoinRequestRow(request: JoinRequest, isAnswering: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    PersonRow(user = request.user, detail = "@${request.user.username}", modifier = modifier) {
        TextButton(onClick = onDecline, enabled = !isAnswering) {
            Text(stringResource(Res.string.members_join_decline), color = MaterialTheme.colorScheme.error)
        }
        PrimaryButton(text = stringResource(Res.string.members_join_accept), onClick = onAccept, isLoading = isAnswering, size = ButtonSize.Small)
    }
}

@Composable
internal fun InviteRow(invite: TripInvite, onShare: () -> Unit, onRevoke: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        // The text buttons' own padding makes up the end, so their labels line up with the text at the start.
        modifier = modifier.fillMaxWidth().padding(start = MaterialTheme.spacing.large, end = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.members_invite_expires, DateUtils.formatFormal(invite.expiresAt)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShare) { Text(stringResource(Res.string.members_invite_share)) }
        TextButton(onClick = onRevoke) {
            Text(stringResource(Res.string.members_invite_revoke), color = MaterialTheme.colorScheme.error)
        }
    }
}

private val PersonRowHeight = 56.dp

/** The owner, who can't be removed, and an editor the owner can remove. */
@Preview
@Composable
private fun MemberRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            previewMembers.forEach { member -> MemberRow(member = member, canRemove = member.role != TripRole.OWNER, onRemove = {}) }
        }
    }
}

/** A request to answer, and one whose answer waits for the server. */
@Preview
@Composable
private fun JoinRequestRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            JoinRequestRow(previewJoinRequest, isAnswering = false, onAccept = {}, onDecline = {})
            JoinRequestRow(previewJoinRequest, isAnswering = true, onAccept = {}, onDecline = {})
        }
    }
}

@Preview
@Composable
private fun InviteRowPreview() {
    TripinglyTheme {
        InviteRow(invite = previewInvite, onShare = {}, onRevoke = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

@Preview
@Composable
private fun MembersRowsDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            JoinRequestRow(previewJoinRequest, isAnswering = false, onAccept = {}, onDecline = {})
            previewMembers.forEach { member -> MemberRow(member = member, canRemove = member.role != TripRole.OWNER, onRemove = {}) }
            MemberSkeleton()
            InviteRow(invite = previewInvite, onShare = {}, onRevoke = {})
        }
    }
}

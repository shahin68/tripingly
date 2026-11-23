package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary

/*
 * People as the members sheet shows them (the members sheet canvas, Shahin 2026-11-18), shared with Nearby's faces row
 * and people sheet (Shahin, 2026-11-23): a row with the avatar, name and a detail line, a face with the name under it,
 * and a small tag.
 */

/** A person in a list: their avatar, name and [detail] ("@lena · yesterday"), and [actions] at the end. */
@Composable
fun PersonRow(
    user: UserSummary,
    detail: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = PersonRowHeight)
            .padding(horizontal = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(user, size = AvatarSize.Row)
        Column(Modifier.weight(1f)) {
            Text(
                user.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.skeletonText()
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.skeletonText()
            )
        }
        actions()
    }
}

/**
 * A face to tap ([onClick], read out as [onClickLabel]): the avatar with the name under it, [PersonFaceDefaults.Width]
 * wide, and an optional [badge] on the avatar (the members sheet's +).
 */
@Composable
fun PersonFace(
    user: UserSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    badge: @Composable BoxScope.() -> Unit = {},
) {
    FaceSlot(user.displayName, onClick, modifier, onClickLabel) {
        Box {
            AvatarCircle(user, size = AvatarSize.Medium)
            badge()
        }
    }
}

/** A face's frame: the [circle] with the [name] under it, so a "more" circle lines up with the faces beside it. */
@Composable
fun FaceSlot(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    circle: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(PersonFaceDefaults.Width)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        circle()
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.skeletonText(),
        )
    }
}

object PersonFaceDefaults {
    val Width = 72.dp
}

/**
 * A small tag ("Owner", "Friend"), in the accent's container when [isHighlighted]. [sizedFor] are labels the tag makes
 * room for, so tags that take turns (Owner and Editor) are the same size in every language.
 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, isHighlighted: Boolean = false, sizedFor: List<String> = emptyList()) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .skeleton(CircleShape)
            .clip(CircleShape)
            .background(if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(
                horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall,
                vertical = MaterialTheme.spacing.extraSmall
            ),
    ) {
        val color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        sizedFor.filter { it != text }.forEach { TagLabel(it, color, isShown = false) }
        TagLabel(text, color, isShown = true)
    }
}

/** A hidden label still takes its room (and isn't read out), so it only sets the tag's width. */
@Composable
private fun TagLabel(text: String, color: Color, isShown: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = if (isShown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
    )
}

private val PersonRowHeight = 56.dp

@Composable
private fun PersonPartsPreviewContent() {
    val lena = UserSummary(id = "1", username = "lena", displayName = "Lena Berg")
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(MaterialTheme.spacing.medium)) {
        PersonRow(lena, "@lena") { Tag("Friend") }
        PersonRow(lena, "@lena") { Tag("Owner", isHighlighted = true, sizedFor = listOf("Owner", "Editor")) }
        PersonFace(lena, onClick = {})
    }
}

@Preview
@Composable
private fun PersonPartsPreview() {
    TripinglyTheme { PersonPartsPreviewContent() }
}

@Preview
@Composable
private fun PersonPartsDarkPreview() {
    TripinglyTheme(darkTheme = true) { PersonPartsPreviewContent() }
}

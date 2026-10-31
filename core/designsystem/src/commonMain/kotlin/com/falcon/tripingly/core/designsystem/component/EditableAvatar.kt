package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.profile_photo_choose
import com.falcon.tripingly.core.designsystem.generated.resources.profile_photo_edit
import com.falcon.tripingly.core.designsystem.generated.resources.profile_photo_remove
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.ProfilePhoto
import org.jetbrains.compose.resources.stringResource

/**
 * My own avatar with a small pen at its bottom right (like Telegram). Tapping it opens the photo picker
 * ([onChoose]); with a [photo] it first offers Choose and Remove ([onRemove]). [isSaving] dims it under a
 * progress ring while a new photo goes up.
 */
@Composable
fun EditableAvatar(
    name: String,
    photo: ProfilePhoto?,
    isSaving: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSize.Medium,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val edit = stringResource(Res.string.profile_photo_edit)
    Box(modifier) {
        AvatarCircle(
            name = name,
            photo = photo,
            size = size,
            modifier = Modifier.clip(CircleShape).clickable(enabled = !isSaving, onClickLabel = edit, role = Role.Button) {
                if (photo == null) onChoose() else menuOpen = true
            },
        )
        if (isSaving) {
            Box(Modifier.size(size).background(Color.Black.copy(alpha = SAVING_SCRIM), CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(size / 2), color = Color.White, strokeWidth = 2.dp)
            }
        }
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(PenSize)
                .border(PenRing, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(PenPadding),
        )
        AppDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            actions = listOf(
                DropdownAction(stringResource(Res.string.profile_photo_choose), icon = Icons.Outlined.PhotoLibrary) {
                    menuOpen = false
                    onChoose()
                },
                DropdownAction(stringResource(Res.string.profile_photo_remove), isDestructive = true, icon = Icons.Outlined.Delete) {
                    menuOpen = false
                    onRemove()
                },
            ),
        )
    }
}

private const val SAVING_SCRIM = 0.35f
private val PenSize = 22.dp
private val PenRing = 2.dp
private val PenPadding = 4.dp

@Composable
private fun EditableAvatarPreviewContent() {
    Row(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
        EditableAvatar(name = "Jonas", photo = null, isSaving = false, onChoose = {}, onRemove = {})
        EditableAvatar(name = "Jonas", photo = null, isSaving = true, onChoose = {}, onRemove = {}, modifier = Modifier.padding(start = MaterialTheme.spacing.medium))
    }
}

@Preview
@Composable
private fun EditableAvatarPreview() {
    TripinglyTheme { EditableAvatarPreviewContent() }
}

@Preview
@Composable
private fun EditableAvatarDarkPreview() {
    TripinglyTheme(darkTheme = true) { EditableAvatarPreviewContent() }
}

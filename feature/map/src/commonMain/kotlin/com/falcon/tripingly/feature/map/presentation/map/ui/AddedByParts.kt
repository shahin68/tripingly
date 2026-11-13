package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_added_by_you
import com.falcon.tripingly.feature.map.generated.resources.map_by_you
import com.falcon.tripingly.feature.map.generated.resources.map_place_added_by
import com.falcon.tripingly.feature.map.presentation.map.AddedBy
import org.jetbrains.compose.resources.stringResource

/**
 * Who added a pin, inline after its type on the big card (Shahin's B, 2026-11-13): "· ", their face and @handle, or "you".
 * The handle ends in "…" when it doesn't fit; the face stays.
 */
@Composable
internal fun AddedByInline(addedBy: AddedBy, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
        Text("·", style = MaterialTheme.typography.bodySmall)
        addedBy.user?.let { AvatarCircle(it, size = AvatarSize.Tiny) }
        Text(
            if (addedBy.isMe) stringResource(Res.string.map_by_you) else "@${addedBy.user?.username.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Added by @handle" or "Added by you", for the details. */
@Composable
internal fun AddedBy.text(): String =
    if (isMe) stringResource(Res.string.map_added_by_you) else stringResource(Res.string.map_place_added_by, user?.username.orEmpty())

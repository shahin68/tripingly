package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.mark_hot
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/** A flame with a place's hot count, in the same quiet color as every count; filled while I marked it hot. Never a like. */
@Composable
fun HotButton(
    hot: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .toggleable(value = hot, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = MaterialTheme.spacing.small, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (hot) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
            contentDescription = stringResource(Res.string.mark_hot),
            tint = if (hot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        if (count > 0) {
            Text(text = compactCount(count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun HotButtonPreview() {
    TripinglyTheme {
        Row {
            HotButton(hot = true, count = 12, onToggle = {})
            HotButton(hot = false, count = 0, onToggle = {})
        }
    }
}

@Preview
@Composable
private fun HotButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
            HotButton(hot = true, count = 1_204, onToggle = {})
            HotButton(hot = false, count = 3, onToggle = {})
        }
    }
}

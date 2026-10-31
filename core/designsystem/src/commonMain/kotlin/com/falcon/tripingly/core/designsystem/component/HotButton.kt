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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.mark_hot
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A flame with a place's hot count, in the same quiet color as every count; filled while I marked it hot, and marking
 * it pops the flame with a burst of dots. No padding at the start, so the flame lines up with the text above it.
 * Never a like.
 */
@Composable
fun HotButton(
    hot: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var burst by remember { mutableIntStateOf(0) }
    Row(
        modifier = modifier
            .toggleable(
                value = hot,
                interactionSource = null,
                indication = ripple(bounded = false),
                role = Role.Checkbox,
                onValueChange = { markHot ->
                    if (markHot) burst++
                    onToggle()
                },
            )
            .height(LikeButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BurstIcon(
            icon = if (hot) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
            contentDescription = stringResource(Res.string.mark_hot),
            tint = if (hot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            burst = burst,
            modifier = Modifier.size(20.dp).skeleton(CircleShape),
        )
        if (count > 0) {
            Text(text = compactCount(count), style = MaterialTheme.typography.labelLarge, modifier = Modifier.skeletonText())
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

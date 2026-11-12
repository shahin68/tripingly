package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/** One row of a [PickerSheet]: a title and an optional line under it. */
@Immutable
data class PickerRow(val id: String, val title: String, val subtitle: String? = null)

/**
 * A sheet's content that picks one of its [rows], e.g. one of my trips and then one of its days when a place or stop is
 * added to a trip (Nearby, the trip map). [rows] null: they're loading, shown as shimmering rows.
 */
@Composable
fun PickerSheet(title: String, rows: List<PickerRow>?, onPick: (PickerRow) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(Modifier.padding(bottom = MaterialTheme.spacing.large)) {
            SheetHeader(title = title, onClose = onClose)
            if (rows == null) {
                // Placeholder rows only: drawn as the rows' shimmering bars, never as text.
                Skeleton(Modifier.padding(vertical = MaterialTheme.spacing.small)) {
                    Column { repeat(SKELETON_ROWS) { PickerRowItem(PickerRow("", "Day 1", "Mon, 1 Jan"), onClick = {}) } }
                }
            } else {
                LazyColumn {
                    items(rows, key = { it.id }) { row -> PickerRowItem(row, onClick = { onPick(row) }, Modifier.animateItem()) }
                }
            }
        }
        MessageBannerHost(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun PickerRowItem(row: PickerRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        row.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.skeletonText())
        }
    }
}

private val RowHeight = 56.dp
private const val SKELETON_ROWS = 3

/** The trips loaded (one name too long for a line, one without dates), and the days loading. */
private class PickerSheetPreviewStates : PreviewParameterProvider<Pair<String, List<PickerRow>?>> {
    override val values = sequenceOf(
        "Add Café Central to which trip?" to listOf(
            PickerRow("1", "A week in Vienna", "Jun 1 – Jun 7, 2026"),
            PickerRow("2", "A very long trip name that goes on and on and doesn't fit on one line"),
        ),
        "Which day of A week in Vienna?" to null,
    )
}

@Composable
private fun PickerSheetPreviewContent(state: Pair<String, List<PickerRow>?>) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        PickerSheet(title = state.first, rows = state.second, onPick = {}, onClose = {})
    }
}

@Preview
@Composable
private fun PickerSheetPreview(@PreviewParameter(PickerSheetPreviewStates::class) state: Pair<String, List<PickerRow>?>) {
    TripinglyTheme { PickerSheetPreviewContent(state) }
}

@Preview
@Composable
private fun PickerSheetDarkPreview(@PreviewParameter(PickerSheetPreviewStates::class) state: Pair<String, List<PickerRow>?>) {
    TripinglyTheme(darkTheme = true) { PickerSheetPreviewContent(state) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PickerSheetLandscapePreview() {
    TripinglyTheme { PickerSheetPreviewContent(PickerSheetPreviewStates().values.first()) }
}

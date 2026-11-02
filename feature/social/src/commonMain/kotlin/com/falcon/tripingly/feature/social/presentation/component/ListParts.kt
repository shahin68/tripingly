package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Asks for the next page once the list is scrolled to within a few items of its end. */
@Composable
internal fun LoadMoreAtEnd(listState: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}

/** [LoadMoreAtEnd] for a grid. */
@Composable
internal fun LoadMoreAtEnd(gridState: LazyGridState, onLoadMore: () -> Unit) {
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}

/** A list's quiet "nothing here" line. */
@Composable
internal fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge),
    )
}

private const val LOAD_MORE_THRESHOLD = 3

@Preview
@Composable
private fun EmptyTextPreview() {
    TripinglyTheme { EmptyText("Nobody found by that name.", Modifier.background(MaterialTheme.colorScheme.background)) }
}

@Preview
@Composable
private fun EmptyTextDarkPreview() {
    TripinglyTheme(darkTheme = true) { EmptyText("Nobody found by that name.", Modifier.background(MaterialTheme.colorScheme.background)) }
}

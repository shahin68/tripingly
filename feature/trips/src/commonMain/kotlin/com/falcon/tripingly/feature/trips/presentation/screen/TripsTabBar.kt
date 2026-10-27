package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.home_fab_add_trip
import com.falcon.tripingly.feature.trips.generated.resources.home_search_placeholder_trips
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/*
 * The Trips tab's parts of the tab shell's shared bars. They use the same [HomeViewModel] as the tab itself (the
 * view model belongs to the shell's back stack entry).
 */

/** Searches My Trips as the user types; shown under the shell's top bar on the Trips tab. */
@Composable
fun TripsSearchField(viewModel: HomeViewModel = koinViewModel()) {
    AppTextField(
        state = viewModel.searchField,
        placeholder = stringResource(Res.string.home_search_placeholder_trips),
        onTextChange = { viewModel.onAction(Action.SearchQueryChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        style = AppTextFieldStyle.Search,
    )
}

/** Opens the New trip sheet; it shrinks away when the user swipes off the Trips tab and grows back on return. */
@Composable
fun AddTripButton(visible: Boolean, modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel()) {
    AddTripButton(visible, onClick = { viewModel.onAction(Action.Trips.Add) }, modifier)
}

@Composable
private fun AddTripButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (visible) 1f else 0f, tween(durationMillis = 200))
    if (scale > 0f) {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(Res.string.home_fab_add_trip)) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = modifier.scale(scale),
        )
    }
}

@Preview
@Composable
private fun AddTripButtonPreview() {
    TripinglyTheme { AddTripButton(visible = true, onClick = {}) }
}

@Preview
@Composable
private fun AddTripButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { AddTripButton(visible = true, onClick = {}) }
}

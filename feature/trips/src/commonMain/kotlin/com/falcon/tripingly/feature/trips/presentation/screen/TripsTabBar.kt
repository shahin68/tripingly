package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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

/**
 * Opens the New trip sheet; it shrinks away when the user swipes off the Trips tab and grows back on return, and folds
 * to its icon once the trips are scrolled, back to "Add Trip" at the top, as Gmail's Compose (Shahin, 2026-11-08).
 */
@Composable
fun AddTripButton(visible: Boolean, modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel()) {
    val expanded by remember(viewModel) { derivedStateOf { viewModel.tripList.firstVisibleItemIndex == 0 } }
    AddTripButton(visible, expanded, onClick = { viewModel.onAction(Action.Trips.Add) }, modifier)
}

/**
 * The brand's green, with a very thin glass edge so it still shows over today's green cards (Shahin, 2026-11-08). It
 * grows up from its bottom edge when the Trips tab shows and shrinks back into it when it goes.
 */
@Composable
private fun AddTripButton(visible: Boolean, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    AnimatedVisibility(visible = visible, enter = FabEnter, exit = FabExit, modifier = modifier) {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(Res.string.home_fab_add_trip)) },
            icon = { Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.home_fab_add_trip).takeUnless { expanded }) },
            onClick = onClick,
            expanded = expanded,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = shape,
            modifier = Modifier.border(Dp.Hairline, GlassStroke, shape),
        )
    }
}

/** Grows from and shrinks into its bottom edge, quickly (Shahin, 2026-11-08). */
private val FabOrigin = TransformOrigin(0.5f, 1f)
private val FabEnter = scaleIn(tween(durationMillis = 150, easing = EaseOut), transformOrigin = FabOrigin)
private val FabExit = scaleOut(tween(durationMillis = 150, easing = EaseOut), transformOrigin = FabOrigin)

/** A glass edge that reads on the green in both themes. */
private val GlassStroke = Color.White.copy(alpha = 0.6f)

@Preview
@Composable
private fun AddTripButtonPreview() {
    TripinglyTheme { AddTripButton(visible = true, expanded = true, onClick = {}) }
}

@Preview
@Composable
private fun AddTripButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { AddTripButton(visible = true, expanded = false, onClick = {}) }
}

package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.RoundedTextField
import com.falcon.tripingly.core.designsystem.component.SectionLabel
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_button_create
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_dates_add
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_destination_clear
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_destination_placeholder
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_name_label
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_section_length
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_section_visibility
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_title
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_private
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_private_hint
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_public
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_public_hint
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import org.jetbrains.compose.resources.stringResource

/**
 * The New trip sheet, sliding up from the bottom. Stateless: everything it shows is in [form]
 * (kept by the view model), so typing, picks and the open date picker survive a rotation.
 * It can't be swiped away while the trip is being saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateTripSheet(
    form: CreateTripForm,
    isSaving: Boolean,
    error: UiText?,
    onAction: (Action) -> Unit,
) {
    val saving by rememberUpdatedState(isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving },
    )
    ModalBottomSheet(
        onDismissRequest = { onAction(Action.OnDismissDialog) },
        sheetState = sheetState,
    ) {
        CreateTripSheetContent(form = form, isSaving = isSaving, error = error, onAction = onAction)
    }
}

@Composable
internal fun CreateTripSheetContent(
    form: CreateTripForm,
    isSaving: Boolean,
    error: UiText?,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    if (showDatePicker) {
        TripDatesPickerDialog(
            initialStartDate = form.startDate,
            initialEndDate = form.endDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                showDatePicker = false
                onAction(Action.OnCreateDatesChanged(start, end))
            },
        )
    }

    // The fields scroll (landscape, keyboard open) while the Create button stays at the bottom.
    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(text = stringResource(Res.string.create_trip_title), style = MaterialTheme.typography.headlineMedium)
            RoundedTextField(
                value = form.name,
                onValueChange = { onAction(Action.OnCreateNameChanged(it)) },
                placeholder = stringResource(Res.string.create_trip_name_label),
                enabled = !isSaving,
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            DestinationField(form = form, isSaving = isSaving, onAction = onAction)

            SectionLabel(stringResource(Res.string.create_trip_section_length))
            val dates = formatDateRange(form.startDate, form.endDate ?: form.startDate)
            PillChip(
                label = dates ?: stringResource(Res.string.create_trip_dates_add),
                onClick = { showDatePicker = true },
                leading = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(start = MaterialTheme.spacing.small)) },
                enabled = !isSaving,
            )

            SectionLabel(stringResource(Res.string.create_trip_section_visibility))
            SegmentedToggle(Modifier.fillMaxWidth()) {
                SegmentedToggleOption(
                    label = stringResource(Res.string.create_trip_visibility_public),
                    selected = form.visibility == TripVisibility.PUBLIC,
                    onClick = { onAction(Action.OnCreateVisibilityChanged(TripVisibility.PUBLIC)) },
                    icon = Icons.Default.Public,
                    enabled = !isSaving,
                )
                SegmentedToggleOption(
                    label = stringResource(Res.string.create_trip_visibility_private),
                    selected = form.visibility == TripVisibility.PRIVATE,
                    onClick = { onAction(Action.OnCreateVisibilityChanged(TripVisibility.PRIVATE)) },
                    icon = Icons.Default.Lock,
                    enabled = !isSaving,
                )
            }
            Text(
                text = stringResource(
                    if (form.visibility == TripVisibility.PUBLIC) {
                        Res.string.create_trip_visibility_public_hint
                    } else {
                        Res.string.create_trip_visibility_private_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CreateTripMembers(form = form, isSaving = isSaving, onAction = onAction)
            DialogError(error?.asString())
        }
        PrimaryButton(
            text = stringResource(Res.string.create_trip_button_create),
            onClick = { onAction(Action.OnConfirmCreateTrip) },
            enabled = form.canCreate,
            isLoading = isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        )
    }
}

@Composable
private fun DestinationField(form: CreateTripForm, isSaving: Boolean, onAction: (Action) -> Unit) {
    Column {
        RoundedTextField(
            value = form.destinationQuery,
            onValueChange = { onAction(Action.OnDestinationQueryChanged(it)) },
            placeholder = stringResource(Res.string.create_trip_destination_placeholder),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    form.isSearchingDestination -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    form.destinationQuery.isNotEmpty() -> IconButton(
                        onClick = { onAction(Action.OnDestinationQueryChanged("")) },
                        enabled = !isSaving,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.create_trip_destination_clear))
                    }
                }
            },
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.destination == null && form.destinationResults.isNotEmpty()) {
            form.destinationResults.take(MAX_DESTINATION_RESULTS).forEach { result ->
                SuggestionItem(
                    title = result.name,
                    subtitle = result.address,
                    onClick = { onAction(Action.OnDestinationSelected(result)) },
                )
            }
            OsmAttribution()
        }
    }
}

private const val MAX_DESTINATION_RESULTS = 5

@Preview
@Composable
private fun CreateTripSheetPreview() {
    TripinglyTheme {
        CreateTripSheetContent(
            form = createTripFormPreviewData,
            isSaving = false,
            error = null,
            onAction = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview
@Composable
private fun CreateTripSheetDarkErrorPreview() {
    TripinglyTheme(darkTheme = true) {
        CreateTripSheetContent(
            form = createTripFormWithErrorsPreviewData,
            isSaving = false,
            error = UiText.Server("Some people can’t be added. Remove them and try again."),
            onAction = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun CreateTripSheetLandscapePreview() {
    TripinglyTheme {
        CreateTripSheetContent(
            form = CreateTripForm(),
            isSaving = true,
            error = null,
            onAction = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

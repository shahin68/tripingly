package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.FieldButton
import com.falcon.tripingly.core.designsystem.component.FormField
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_button_create
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_dates_add
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_dates_label
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_destination_label
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_destination_placeholder
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_members_label
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_name_label
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_name_placeholder
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_title
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_label
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_private
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_visibility_public
import com.falcon.tripingly.feature.trips.generated.resources.trip_day_count
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.pluralStringResource
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
    onClose: () -> Unit,
) {
    Box {
        CreateTripSheetContent(form = form, isSaving = isSaving, error = error, onAction = onAction, onClose = onClose)
        MessageBannerHost(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun CreateTripSheetContent(
    form: CreateTripForm,
    isSaving: Boolean,
    error: UiText?,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = { onAction(Action.TripDialog.Dismiss) },
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    if (showDatePicker) {
        TripDatesPickerDialog(
            initialStartDate = form.startDate,
            initialEndDate = form.endDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                showDatePicker = false
                onAction(Action.CreateTrip.DatesChanged(start, end))
            },
        )
    }

    // The fields scroll (landscape, keyboard open) while the Create button stays at the bottom.
    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        SheetHeader(title = stringResource(Res.string.create_trip_title), onClose = onClose, closeEnabled = !isSaving)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            FormField(stringResource(Res.string.create_trip_name_label)) {
                AppTextField(
                    state = form.name,
                    placeholder = stringResource(Res.string.create_trip_name_placeholder),
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FormField(stringResource(Res.string.create_trip_destination_label)) {
                DestinationField(form = form, isSaving = isSaving, onAction = onAction)
            }
            FormField(stringResource(Res.string.create_trip_dates_label)) {
                val end = form.endDate ?: form.startDate
                FieldButton(
                    text = DateUtils.formatRange(form.startDate, end),
                    placeholder = stringResource(Res.string.create_trip_dates_add),
                    onClick = { showDatePicker = true },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    trailing = {
                        if (form.startDate != null && end != null) {
                            val days = form.startDate.daysUntil(end) + 1
                            Text(pluralStringResource(Res.plurals.trip_day_count, days, days), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    enabled = !isSaving,
                )
            }
            FormField(stringResource(Res.string.create_trip_visibility_label)) {
                SegmentedToggle(Modifier.fillMaxWidth()) {
                    SegmentedToggleOption(
                        label = stringResource(Res.string.create_trip_visibility_public),
                        selected = form.visibility == TripVisibility.PUBLIC,
                        onClick = { onAction(Action.CreateTrip.VisibilityChanged(TripVisibility.PUBLIC)) },
                        icon = Icons.Outlined.Public,
                        enabled = !isSaving,
                    )
                    SegmentedToggleOption(
                        label = stringResource(Res.string.create_trip_visibility_private),
                        selected = form.visibility == TripVisibility.PRIVATE,
                        onClick = { onAction(Action.CreateTrip.VisibilityChanged(TripVisibility.PRIVATE)) },
                        icon = Icons.Outlined.Lock,
                        enabled = !isSaving,
                    )
                }
            }
            FormField(stringResource(Res.string.create_trip_members_label)) {
                CreateTripMembers(form = form, isSaving = isSaving, onAction = onAction)
            }
            error?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        PrimaryButton(
            text = stringResource(Res.string.create_trip_button_create),
            onClick = { onAction(Action.CreateTrip.Confirm) },
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
        AppTextField(
            state = form.destinationQuery,
            placeholder = stringResource(Res.string.create_trip_destination_placeholder),
            onTextChange = { onAction(Action.CreateTrip.DestinationQueryChanged(it)) },
            leadingIcon = Icons.Default.Search,
            trailing = if (form.isSearchingDestination) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
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
                    onClick = { onAction(Action.CreateTrip.DestinationSelected(result)) },
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

package com.falcon.tripingly.feature.auth.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.FieldButton
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.FormField
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.RoundedTextField
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.common_cancel
import com.falcon.tripingly.feature.auth.generated.resources.common_continue
import com.falcon.tripingly.feature.auth.generated.resources.common_ok
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_birth_date
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_birth_date_placeholder
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_birth_date_private
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_title
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_display_name
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_display_name_placeholder
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_error_load_documents
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_profile_title
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_sign_out
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_under_age
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_available
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_checking
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_invalid
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_placeholder
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_taken
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Action
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Step
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.UsernameStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel


@Composable
internal fun OnboardingRoute() {
    val viewModel: OnboardingViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onAction = viewModel::onAction)
}

/** The profile (name, username, birth date), then the terms and privacy to accept; both under the floating top bar. */
@Composable
internal fun OnboardingScreen(
    state: OnboardingViewModel.State,
    onAction: (Action) -> Unit,
) {
    val consentFailed = state.step == Step.CONSENT && !state.isLoadingDocuments && state.documents.isEmpty()
    Scaffold(
        topBar = {
            FloatingTopBar(
                title = stringResource(if (state.step == Step.PROFILE) Res.string.onboarding_profile_title else Res.string.onboarding_consent_title),
                actions = {
                    TextButton(onClick = { onAction(Action.OnSignOutClick) }) { Text(stringResource(Res.string.onboarding_sign_out)) }
                },
            )
        },
        bottomBar = {
            // The consent choice and its button stay anchored at the bottom; the documents scroll above.
            if (state.step == Step.CONSENT && !state.isLoadingDocuments && state.documents.isNotEmpty()) {
                ConsentFooter(state, onAction)
            }
        },
    ) { padding ->
        ShowMessage(message = state.error?.asString(), onShown = { onAction(Action.OnDismissError) })
        if (consentFailed) {
            ErrorScreen(
                onRetry = { onAction(Action.OnRetryDocumentsClick) },
                message = stringResource(Res.string.onboarding_error_load_documents),
                contentPadding = padding,
            )
            return@Scaffold
        }
        // One readable column, also on tablets and in landscape.
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                when (state.step) {
                    Step.PROFILE -> ProfileStep(state, onAction)
                    Step.CONSENT -> ConsentStep(state)
                }
            }
        }
    }
    if (state.isBirthDatePickerVisible) {
        BirthDatePicker(initial = state.birthDate, onPicked = { onAction(Action.OnBirthDatePicked(it)) })
    }
}

@Composable
private fun ColumnScope.ProfileStep(state: OnboardingViewModel.State, onAction: (Action) -> Unit) {
    FormField(stringResource(Res.string.onboarding_display_name)) {
        RoundedTextField(
            value = state.displayName,
            onValueChange = { onAction(Action.OnDisplayNameChange(it)) },
            placeholder = stringResource(Res.string.onboarding_display_name_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    FormField(stringResource(Res.string.onboarding_username)) {
        val isError = state.usernameStatus == UsernameStatus.TAKEN || state.usernameStatus == UsernameStatus.INVALID
        RoundedTextField(
            value = state.username,
            onValueChange = { onAction(Action.OnUsernameChange(it)) },
            placeholder = stringResource(Res.string.onboarding_username_placeholder),
            leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
            isError = isError,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        usernameHint(state.usernameStatus)?.let { FieldHint(stringResource(it), isError) }
    }
    FormField(stringResource(Res.string.onboarding_birth_date)) {
        // No date is preselected, so no age is suggested.
        FieldButton(
            text = state.birthDate?.let(DateUtils::formatFormal),
            placeholder = stringResource(Res.string.onboarding_birth_date_placeholder),
            onClick = { onAction(Action.OnBirthDateClick) },
            leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        FieldHint(stringResource(if (state.isUnderAge) Res.string.onboarding_under_age else Res.string.onboarding_birth_date_private), state.isUnderAge)
    }
    PrimaryButton(
        text = stringResource(Res.string.common_continue),
        onClick = { onAction(Action.OnSaveProfileClick) },
        enabled = state.canSaveProfile,
        isLoading = state.isSaving,
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
    )
}

/** The small line under a field: what's wrong with it in red, or a note. */
@Composable
private fun FieldHint(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePicker(initial: LocalDate?, onPicked: (LocalDate?) -> Unit) {
    val todayMillis = remember { Clock.System.now().toEpochMilliseconds() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        yearRange = 1900..Clock.System.now().toLocalDateTime(TimeZone.UTC).year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = { onPicked(null) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(
                    pickerState.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    },
                )
            }) { Text(stringResource(Res.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onPicked(null) }) { Text(stringResource(Res.string.common_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun usernameHint(status: UsernameStatus) = when (status) {
    UsernameStatus.IDLE -> null
    UsernameStatus.CHECKING -> Res.string.onboarding_username_checking
    UsernameStatus.AVAILABLE -> Res.string.onboarding_username_available
    UsernameStatus.TAKEN -> Res.string.onboarding_username_taken
    UsernameStatus.INVALID -> Res.string.onboarding_username_invalid
}

private val ContentMaxWidth = 600.dp

@Preview
@Composable
private fun OnboardingScreenPreview(@PreviewParameter(OnboardingScreenPreviewStates::class) state: OnboardingViewModel.State) {
    TripinglyTheme { OnboardingScreen(state = state, onAction = {}) }
}

@Preview
@Composable
private fun OnboardingScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { OnboardingScreen(state = OnboardingScreenPreviewStates().values.first(), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun OnboardingScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) { OnboardingScreen(state = OnboardingScreenPreviewStates().values.last(), onAction = {}) }
}

@Preview
@Composable
private fun BirthDatePickerPreview() {
    TripinglyTheme { BirthDatePicker(initial = LocalDate(1995, 4, 12), onPicked = {}) }
}

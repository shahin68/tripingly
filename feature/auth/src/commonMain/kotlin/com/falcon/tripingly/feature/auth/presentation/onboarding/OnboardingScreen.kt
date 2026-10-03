package com.falcon.tripingly.feature.auth.presentation.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ErrorBanner
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.common_cancel
import com.falcon.tripingly.feature.auth.generated.resources.common_continue
import com.falcon.tripingly.feature.auth.generated.resources.common_ok
import com.falcon.tripingly.feature.auth.generated.resources.common_retry
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_birth_date
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_birth_date_private
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_accept
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_marketing
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_privacy
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_read
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_terms
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_title
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_display_name
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_profile_title
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_sign_out
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_under_age
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_available
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_checking
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_invalid
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_username_taken
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Action
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Step
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.UsernameStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun OnboardingRoute() {
    val viewModel: OnboardingViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingScreen(
    state: OnboardingViewModel.State,
    onAction: (Action) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.step == Step.PROFILE) Res.string.onboarding_profile_title else Res.string.onboarding_consent_title,
                        ),
                    )
                },
                actions = {
                    TextButton(onClick = { onAction(Action.OnSignOutClick) }) {
                        Text(stringResource(Res.string.onboarding_sign_out))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            state.error?.let { error ->
                ErrorBanner(errorMessage = error.asString(), onDismiss = { onAction(Action.OnDismissError) })
            }
            when (state.step) {
                Step.PROFILE -> ProfileStep(state, onAction)
                Step.CONSENT -> ConsentStep(state, onAction)
            }
        }
    }
    if (state.isBirthDatePickerVisible) {
        BirthDatePicker(initial = state.birthDate, onPicked = { onAction(Action.OnBirthDatePicked(it)) })
    }
}

@Composable
private fun ColumnScope.ProfileStep(state: OnboardingViewModel.State, onAction: (Action) -> Unit) {
    OutlinedTextField(
        value = state.displayName,
        onValueChange = { onAction(Action.OnDisplayNameChange(it)) },
        label = { Text(stringResource(Res.string.onboarding_display_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.username,
        onValueChange = { onAction(Action.OnUsernameChange(it)) },
        label = { Text(stringResource(Res.string.onboarding_username)) },
        prefix = { Text("@") },
        singleLine = true,
        isError = state.usernameStatus == UsernameStatus.TAKEN || state.usernameStatus == UsernameStatus.INVALID,
        supportingText = usernameHint(state.usernameStatus)?.let { hint -> { Text(stringResource(hint)) } },
        modifier = Modifier.fillMaxWidth(),
    )
    BirthDateField(state.birthDate, onClick = { onAction(Action.OnBirthDateClick) })
    Text(
        text = stringResource(if (state.isUnderAge) Res.string.onboarding_under_age else Res.string.onboarding_birth_date_private),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.isUnderAge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { onAction(Action.OnSaveProfileClick) },
        enabled = state.canSaveProfile,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isSaving) CircularProgressIndicator() else Text(stringResource(Res.string.common_continue))
    }
}

/** A read-only field that opens the date picker; no date is preselected, so no age is suggested. */
@Composable
private fun BirthDateField(date: LocalDate?, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is PressInteraction.Release) onClick() }
    }
    OutlinedTextField(
        value = date?.toString().orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(Res.string.onboarding_birth_date)) },
        interactionSource = interactions,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ColumnScope.ConsentStep(state: OnboardingViewModel.State, onAction: (Action) -> Unit) {
    val uriHandler = LocalUriHandler.current
    if (state.isLoadingDocuments) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        return
    }
    if (state.documents.isEmpty()) {
        TextButton(onClick = { onAction(Action.OnRetryDocumentsClick) }) { Text(stringResource(Res.string.common_retry)) }
        return
    }
    state.documents.forEach { item ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(Action.OnDocumentAcceptedChange(item.document, !item.accepted)) },
        ) {
            Checkbox(
                checked = item.accepted,
                onCheckedChange = { onAction(Action.OnDocumentAcceptedChange(item.document, it)) },
            )
            Text(
                text = stringResource(item.document.type.acceptLabel()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { uriHandler.openUri(item.document.url) }) {
                Text(stringResource(Res.string.onboarding_consent_read))
            }
        }
    }
    Button(
        onClick = { onAction(Action.OnAcceptClick) },
        enabled = state.canAccept,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isSaving) CircularProgressIndicator() else Text(stringResource(Res.string.onboarding_consent_accept))
    }
}

private fun usernameHint(status: UsernameStatus) = when (status) {
    UsernameStatus.IDLE -> null
    UsernameStatus.CHECKING -> Res.string.onboarding_username_checking
    UsernameStatus.AVAILABLE -> Res.string.onboarding_username_available
    UsernameStatus.TAKEN -> Res.string.onboarding_username_taken
    UsernameStatus.INVALID -> Res.string.onboarding_username_invalid
}

private fun LegalDocumentType.acceptLabel() = when (this) {
    LegalDocumentType.TERMS -> Res.string.onboarding_consent_terms
    LegalDocumentType.PRIVACY -> Res.string.onboarding_consent_privacy
    LegalDocumentType.MARKETING -> Res.string.onboarding_consent_marketing
}

@Preview
@Composable
private fun ProfileStepPreview() {
    TripinglyTheme {
        OnboardingScreen(
            state = OnboardingViewModel.State(
                displayName = "Jonas",
                username = "jonas.k",
                usernameStatus = UsernameStatus.AVAILABLE,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ConsentStepPreview() {
    TripinglyTheme {
        OnboardingScreen(
            state = OnboardingViewModel.State(
                step = Step.CONSENT,
                documents = persistentListOf(
                    OnboardingViewModel.DocumentItem(
                        LegalDocument(LegalDocumentType.TERMS, "2026-09-01", "en", "https://example.com/terms", true),
                        accepted = true,
                    ),
                    OnboardingViewModel.DocumentItem(
                        LegalDocument(LegalDocumentType.PRIVACY, "2026-09-01", "en", "https://example.com/privacy", true),
                        accepted = false,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

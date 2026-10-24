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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
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
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_terms
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_terms_and_privacy
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_document_marketing
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_document_privacy
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_document_terms
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.background

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
        bottomBar = {
            // The consent choice and its button stay anchored at the bottom; the documents scroll above.
            if (state.step == Step.CONSENT && !state.isLoadingDocuments && state.documents.isNotEmpty()) {
                ConsentFooter(state, onAction)
            }
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
            ShowMessage(message = state.error?.asString(), onShown = { onAction(Action.OnDismissError) })
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
        value = date?.let(DateUtils::formatFormal).orEmpty(),
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        state.documents.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(item.document.type.title())) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable { uriHandler.openUri(item.document.url) },
            )
        }
    }
}

@Composable
private fun ConsentFooter(state: OnboardingViewModel.State, onAction: (Action) -> Unit) {
    val (required, optional) = state.documents.partition { it.document.required }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        if (required.isNotEmpty()) {
            // One tick accepts every required document (Terms and Privacy); each is still recorded on its own.
            ConsentRow(
                label = stringResource(requiredLabel(required.map { it.document.type })),
                checked = required.all { it.accepted },
                onCheckedChange = { onAction(Action.OnRequiredDocumentsAcceptedChange(it)) },
            )
        }
        optional.forEach { item ->
            ConsentRow(
                label = stringResource(item.document.type.acceptLabel()),
                checked = item.accepted,
                onCheckedChange = { onAction(Action.OnDocumentAcceptedChange(item.document, it)) },
            )
        }
        Button(
            onClick = { onAction(Action.OnAcceptClick) },
            enabled = state.canAccept,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) CircularProgressIndicator() else Text(stringResource(Res.string.onboarding_consent_accept))
        }
    }
}

private fun usernameHint(status: UsernameStatus) = when (status) {
    UsernameStatus.IDLE -> null
    UsernameStatus.CHECKING -> Res.string.onboarding_username_checking
    UsernameStatus.AVAILABLE -> Res.string.onboarding_username_available
    UsernameStatus.TAKEN -> Res.string.onboarding_username_taken
    UsernameStatus.INVALID -> Res.string.onboarding_username_invalid
}

@Composable
private fun ConsentRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

private fun requiredLabel(types: List<LegalDocumentType>) = when {
    LegalDocumentType.TERMS in types && LegalDocumentType.PRIVACY in types -> Res.string.onboarding_consent_terms_and_privacy
    LegalDocumentType.PRIVACY in types -> Res.string.onboarding_consent_privacy
    else -> Res.string.onboarding_consent_terms
}

private fun LegalDocumentType.title() = when (this) {
    LegalDocumentType.TERMS -> Res.string.onboarding_document_terms
    LegalDocumentType.PRIVACY -> Res.string.onboarding_document_privacy
    LegalDocumentType.MARKETING -> Res.string.onboarding_document_marketing
}

private fun LegalDocumentType.acceptLabel() = when (this) {
    LegalDocumentType.TERMS -> Res.string.onboarding_consent_terms
    LegalDocumentType.PRIVACY -> Res.string.onboarding_consent_privacy
    LegalDocumentType.MARKETING -> Res.string.onboarding_consent_marketing
}

@Preview
@Composable
private fun OnboardingScreenPreview(@PreviewParameter(OnboardingScreenPreviewStates::class) state: OnboardingViewModel.State) {
    TripinglyTheme { OnboardingScreen(state = state, onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun OnboardingScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) { OnboardingScreen(state = OnboardingScreenPreviewStates().values.last(), onAction = {}) }
}

@Preview
@Composable
private fun BirthDateFieldPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium)) {
            BirthDateField(date = null, onClick = {})
            BirthDateField(date = LocalDate(1995, 4, 12), onClick = {})
        }
    }
}

@Preview
@Composable
private fun BirthDatePickerPreview() {
    TripinglyTheme { BirthDatePicker(initial = LocalDate(1995, 4, 12), onPicked = {}) }
}

@Preview
@Composable
private fun ConsentRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            ConsentRow(label = "I accept the Terms of Service and the Privacy Policy", checked = true, onCheckedChange = {})
            ConsentRow(label = "Send me news and travel tips by email", checked = false, onCheckedChange = {})
        }
    }
}

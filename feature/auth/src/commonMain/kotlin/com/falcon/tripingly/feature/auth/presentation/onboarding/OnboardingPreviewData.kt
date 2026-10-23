package com.falcon.tripingly.feature.auth.presentation.onboarding

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.error_no_internet
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.DocumentItem
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.State
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Step
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.UsernameStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

private val previewDocuments = persistentListOf(
    DocumentItem(LegalDocument(LegalDocumentType.TERMS, "2026-09-01", "en", "https://example.com/terms", true), accepted = true),
    DocumentItem(LegalDocument(LegalDocumentType.PRIVACY, "2026-09-01", "en", "https://example.com/privacy", true), accepted = true),
    DocumentItem(LegalDocument(LegalDocumentType.MARKETING, "2026-09-01", "en", "https://example.com/marketing", false), accepted = false),
)

/**
 * The profile step empty, filled in, with a taken username, under 16, and saving; the consent step loading, failed
 * to load, and ready.
 */
internal class OnboardingScreenPreviewStates : PreviewParameterProvider<State> {
    private val filled = State(displayName = "Jonas", username = "jonas.k", usernameStatus = UsernameStatus.AVAILABLE, birthDate = LocalDate(1995, 4, 12))
    override val values = sequenceOf(
        State(),
        filled,
        filled.copy(usernameStatus = UsernameStatus.TAKEN),
        filled.copy(birthDate = LocalDate(2015, 1, 1), isUnderAge = true),
        filled.copy(isSaving = true),
        State(step = Step.CONSENT, isLoadingDocuments = true),
        State(step = Step.CONSENT, error = UiMessage.Resource(Res.string.error_no_internet)),
        State(step = Step.CONSENT, documents = previewDocuments),
    )
}

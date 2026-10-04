package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.OnboardingState
import com.falcon.tripingly.core.model.account.ProfileField
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.account.UsernameAvailability
import com.falcon.tripingly.core.network.model.LegalDocumentDto
import com.falcon.tripingly.core.network.model.MeDto
import com.falcon.tripingly.core.network.model.OnboardingStateDto
import com.falcon.tripingly.core.network.model.RecordConsentDto
import com.falcon.tripingly.core.network.model.UsernameAvailabilityDto
import kotlinx.datetime.LocalDate

internal fun MeDto.toAccount() = Account(
    id = id,
    username = username,
    displayName = displayName,
    birthDate = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    locale = locale,
    defaultTripVisibility = when (defaultTripVisibility) {
        MeDto.DefaultTripVisibility.PUBLIC -> TripVisibility.PUBLIC
        MeDto.DefaultTripVisibility.PRIVATE -> TripVisibility.PRIVATE
    },
    isAdmin = role == MeDto.Role.ADMIN,
    onboarding = onboarding.toOnboardingState(),
    entitlements = entitlements.toSet(),
)

private fun OnboardingStateDto.toOnboardingState() = OnboardingState(
    completed = completed,
    missingProfileFields = missingProfileFields.mapTo(mutableSetOf()) {
        when (it) {
            OnboardingStateDto.MissingProfileFields.USERNAME -> ProfileField.USERNAME
            OnboardingStateDto.MissingProfileFields.DISPLAY_NAME -> ProfileField.DISPLAY_NAME
            OnboardingStateDto.MissingProfileFields.BIRTH_DATE -> ProfileField.BIRTH_DATE
        }
    },
    missingConsents = missingConsents.mapTo(mutableSetOf()) {
        when (it) {
            OnboardingStateDto.MissingConsents.TERMS -> LegalDocumentType.TERMS
            OnboardingStateDto.MissingConsents.PRIVACY -> LegalDocumentType.PRIVACY
            OnboardingStateDto.MissingConsents.MARKETING -> LegalDocumentType.MARKETING
        }
    },
)

internal fun Account.toSessionState(): SessionState =
    if (onboarding.completed) SessionState.SignedIn(this) else SessionState.Onboarding(this)

internal fun LegalDocumentDto.toLegalDocument() = LegalDocument(
    type = when (documentType) {
        LegalDocumentDto.DocumentType.TERMS -> LegalDocumentType.TERMS
        LegalDocumentDto.DocumentType.PRIVACY -> LegalDocumentType.PRIVACY
        LegalDocumentDto.DocumentType.MARKETING -> LegalDocumentType.MARKETING
    },
    version = version,
    locale = locale,
    url = url,
    required = required,
)

internal fun LegalDocumentType.toConsentType() = when (this) {
    LegalDocumentType.TERMS -> RecordConsentDto.DocumentType.TERMS
    LegalDocumentType.PRIVACY -> RecordConsentDto.DocumentType.PRIVACY
    LegalDocumentType.MARKETING -> RecordConsentDto.DocumentType.MARKETING
}

internal fun UsernameAvailabilityDto.toAvailability() = when {
    available -> UsernameAvailability.AVAILABLE
    reason == UsernameAvailabilityDto.Reason.INVALID -> UsernameAvailability.INVALID
    else -> UsernameAvailability.TAKEN
}

package com.falcon.tripingly.core.model.account

import kotlinx.datetime.LocalDate

/** The signed-in user as `GET /me` describes them. */
data class Account(
    val id: String,
    val username: String?,
    val displayName: String?,
    /** Never shown to other users. */
    val birthDate: LocalDate?,
    val locale: String,
    val defaultTripVisibility: TripVisibility,
    val isAdmin: Boolean,
    val onboarding: OnboardingState,
    /** Premium features; paid UI follows this, never a local flag. */
    val entitlements: Set<String>,
)

enum class TripVisibility { PUBLIC, PRIVATE }

data class OnboardingState(
    val completed: Boolean,
    val missingProfileFields: Set<ProfileField>,
    val missingConsents: Set<LegalDocumentType>,
)

enum class ProfileField { USERNAME, DISPLAY_NAME, BIRTH_DATE }

enum class LegalDocumentType { TERMS, PRIVACY, MARKETING }

data class LegalDocument(
    val type: LegalDocumentType,
    val version: String,
    val locale: String,
    val url: String,
    /** Must be accepted before the app can be used. */
    val required: Boolean,
)

enum class UsernameAvailability { AVAILABLE, TAKEN, INVALID }

/** What the profile step sends; null fields stay unchanged. */
data class ProfileUpdate(
    val displayName: String? = null,
    val username: String? = null,
    val birthDate: LocalDate? = null,
)

/** The minimum age to use Tripinly; the server decides, this is for the quick local message. */
const val MINIMUM_AGE = 16

fun LocalDate.isAtLeast(age: Int, today: LocalDate): Boolean {
    val years = today.year - year
    val hadBirthday = today.month.ordinal > month.ordinal ||
        (today.month == month && today.day >= day)
    return (if (hadBirthday) years else years - 1) >= age
}

private val USERNAME_FORMAT = Regex("^[a-z0-9_](?:[a-z0-9_]|\\.(?!\\.)){1,28}[a-z0-9_]$")

/**
 * The username format (3–30 of `a-z 0-9 . _`, no leading/trailing or double dot),
 * checked before asking the server, which also rejects reserved names.
 */
fun String.isUsernameFormatValid(): Boolean = USERNAME_FORMAT.matches(this)

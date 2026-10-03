package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.MINIMUM_AGE
import com.falcon.tripingly.core.model.account.OnboardingState
import com.falcon.tripingly.core.model.account.ProfileField
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.account.UsernameAvailability
import com.falcon.tripingly.core.model.account.isAtLeast
import com.falcon.tripingly.core.model.account.isUsernameFormatValid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * In-memory sign-in and onboarding for `-Ptripinly.useFakeApi=true` builds and
 * tests: any sign-in starts a new account that needs onboarding; the username
 * `taken` is taken.
 */
class FakeAccountBackend(private val today: () -> LocalDate) : SessionRepository, AccountRepository {

    private val state = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val session: StateFlow<SessionState> = state.asStateFlow()
    override val developerSignInAvailable = true

    private var account: Account? = null
    private val accepted = mutableSetOf<LegalDocumentType>()

    override suspend fun restore(): AppResult<Unit, DataError.Network> {
        state.value = account?.toSessionState() ?: SessionState.SignedOut()
        return AppResult.Success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String) = signIn(name = null)

    override suspend fun signInWithApple(
        identityToken: String,
        authorizationCode: String,
        givenName: String?,
        familyName: String?,
    ) = signIn(listOfNotNull(givenName, familyName).joinToString(" ").ifBlank { null })

    override suspend fun signInForDevelopment(subject: String, name: String?) = signIn(name)

    override suspend fun refreshAccount(): AppResult<Account, DataError.Network> =
        account?.let { AppResult.Success(it) } ?: AppResult.Error(DataError.Network.Unauthorized)

    override suspend fun signOut(reason: SignOutReason?) {
        account = null
        accepted.clear()
        state.value = SessionState.SignedOut(reason)
    }

    override suspend fun checkUsername(username: String): AppResult<UsernameAvailability, DataError.Network> =
        AppResult.Success(
            when {
                !username.trim().lowercase().isUsernameFormatValid() -> UsernameAvailability.INVALID
                username.trim().lowercase() == "taken" -> UsernameAvailability.TAKEN
                else -> UsernameAvailability.AVAILABLE
            },
        )

    override suspend fun updateProfile(update: ProfileUpdate): AppResult<Account, DataError.Network> {
        val current = account ?: return AppResult.Error(DataError.Network.Unauthorized)
        val birthDate = update.birthDate
        if (birthDate != null && !birthDate.isAtLeast(MINIMUM_AGE, today())) {
            signOut(SignOutReason.UNDER_AGE)
            return AppResult.Error(
                DataError.Network.Api(422, "AGE_REQUIREMENT_NOT_MET", "You must be at least 16 to use Tripinly."),
            )
        }
        return AppResult.Success(
            publish(
                current.copy(
                    displayName = update.displayName ?: current.displayName,
                    username = update.username?.trim()?.lowercase() ?: current.username,
                    birthDate = update.birthDate ?: current.birthDate,
                ),
            ),
        )
    }

    override suspend fun legalDocuments(locale: String): AppResult<List<LegalDocument>, DataError.Network> =
        AppResult.Success(
            listOf(
                LegalDocument(LegalDocumentType.TERMS, "2026-09-01", "en", "https://example.com/terms", required = true),
                LegalDocument(LegalDocumentType.PRIVACY, "2026-09-01", "en", "https://example.com/privacy", required = true),
            ),
        )

    override suspend fun accept(documents: List<LegalDocument>): AppResult<Account, DataError.Network> {
        val current = account ?: return AppResult.Error(DataError.Network.Unauthorized)
        accepted += documents.map { it.type }
        return AppResult.Success(publish(current))
    }

    private fun signIn(name: String?): AppResult<Unit, DataError.Network> {
        publish(
            Account(
                id = "00000000-0000-4000-8000-000000000001",
                username = null,
                displayName = name,
                birthDate = null,
                locale = "en",
                defaultTripVisibility = TripVisibility.PUBLIC,
                isAdmin = false,
                onboarding = OnboardingState(false, emptySet(), emptySet()),
                entitlements = emptySet(),
            ),
        )
        return AppResult.Success(Unit)
    }

    /** Recomputes the onboarding state like the server does and updates the session. */
    private fun publish(account: Account): Account {
        val missingFields = buildSet {
            if (account.username == null) add(ProfileField.USERNAME)
            if (account.displayName == null) add(ProfileField.DISPLAY_NAME)
            if (account.birthDate == null) add(ProfileField.BIRTH_DATE)
        }
        val missingConsents = setOf(LegalDocumentType.TERMS, LegalDocumentType.PRIVACY) - accepted
        val updated = account.copy(
            onboarding = OnboardingState(
                completed = missingFields.isEmpty() && missingConsents.isEmpty(),
                missingProfileFields = missingFields,
                missingConsents = missingConsents,
            ),
        )
        this.account = updated
        state.value = updated.toSessionState()
        return updated
    }
}

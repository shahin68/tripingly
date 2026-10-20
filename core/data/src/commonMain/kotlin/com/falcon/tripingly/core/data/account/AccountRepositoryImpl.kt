package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.DataExport
import com.falcon.tripingly.core.model.account.IdentityProof
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.NotificationSettings
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.account.UsernameAvailability
import com.falcon.tripingly.core.network.account.AccountApi
import com.falcon.tripingly.core.network.baseLanguageTag
import com.falcon.tripingly.core.network.model.ConfirmedDeletionDto
import com.falcon.tripingly.core.network.model.RecordConsentDto
import com.falcon.tripingly.core.network.model.UpdateMeDto
import com.falcon.tripingly.core.network.model.UpdateNotificationSettingsDto

internal class AccountRepositoryImpl(
    private val api: AccountApi,
    private val session: SessionRepositoryImpl,
) : AccountRepository {

    override suspend fun checkUsername(username: String): AppResult<UsernameAvailability, DataError.Network> =
        api.checkUsername(username).map { it.toAvailability() }

    override suspend fun updateProfile(update: ProfileUpdate): AppResult<Account, DataError.Network> {
        val result = api.updateMe(
            UpdateMeDto(
                displayName = update.displayName,
                username = update.username,
                birthDate = update.birthDate?.toString(),
            ),
        ).map { it.toAccount() }
        when (result) {
            is AppResult.Success -> session.onAccountChanged(result.data)
            is AppResult.Error -> {
                val error = result.error
                if (error is DataError.Network.Api && error.code == AGE_REQUIREMENT_NOT_MET) {
                    // The server has deleted the account; keep nothing.
                    session.forget(SignOutReason.UNDER_AGE)
                }
            }
        }
        return result
    }

    override suspend fun legalDocuments(locale: String): AppResult<List<LegalDocument>, DataError.Network> =
        // Compose and platform locales can carry extensions (en-US-u-mu-celsius) the API rejects.
        api.legalDocuments(locale.baseLanguageTag()).map { dto -> dto.items.map { it.toLegalDocument() } }

    override suspend fun accept(documents: List<LegalDocument>): AppResult<Account, DataError.Network> {
        for (document in documents) {
            val recorded = api.recordConsent(
                RecordConsentDto(
                    documentType = document.type.toConsentType(),
                    version = document.version,
                    locale = document.locale,
                    granted = true,
                ),
            )
            if (recorded is AppResult.Error) return recorded
        }
        return session.refreshAccount()
    }

    override suspend fun setDefaultTripVisibility(visibility: TripVisibility): AppResult<Account, DataError.Network> =
        api.updateMe(
            UpdateMeDto(
                defaultTripVisibility = when (visibility) {
                    TripVisibility.PUBLIC -> UpdateMeDto.DefaultTripVisibility.PUBLIC
                    TripVisibility.PRIVATE -> UpdateMeDto.DefaultTripVisibility.PRIVATE
                },
            ),
        ).map { it.toAccount() }.also { result ->
            if (result is AppResult.Success) session.onAccountChanged(result.data)
        }

    override suspend fun notificationSettings(): AppResult<NotificationSettings, DataError.Network> =
        api.notificationSettings().map { it.toNotificationSettings() }

    override suspend fun setNotification(type: NotificationType, enabled: Boolean): AppResult<NotificationSettings, DataError.Network> =
        api.updateNotificationSettings(
            when (type) {
                NotificationType.COMMENTS -> UpdateNotificationSettingsDto(commentOnMarker = enabled)
                NotificationType.ADDED_TO_TRIP -> UpdateNotificationSettingsDto(addedToTrip = enabled)
                NotificationType.TRIP_CHANGES -> UpdateNotificationSettingsDto(tripChangedByCollaborator = enabled)
                NotificationType.LIKES -> UpdateNotificationSettingsDto(likesGrouped = enabled)
                NotificationType.JOIN_REQUESTS -> UpdateNotificationSettingsDto(joinRequested = enabled)
            },
        ).map { it.toNotificationSettings() }

    override suspend fun dataExport(): AppResult<DataExport?, DataError.Network> =
        when (val result = api.dataExport()) {
            is AppResult.Success -> AppResult.Success(result.data.toDataExport())
            is AppResult.Error -> {
                val error = result.error
                if (error is DataError.Network.Api && error.status == 404) AppResult.Success(null) else result
            }
        }

    override suspend fun requestDataExport(): AppResult<DataExport, DataError.Network> =
        api.requestDataExport().map { it.toDataExport() }

    override suspend fun deleteAccount(): AppResult<Unit, DataError.Network> = signOutOnceDeleted(api.deleteAccount())

    override suspend fun confirmAccountDeletion(proof: IdentityProof): AppResult<Unit, DataError.Network> {
        val body = when (proof) {
            is IdentityProof.Google -> ConfirmedDeletionDto(provider = ConfirmedDeletionDto.Provider.GOOGLE, token = proof.idToken)
            is IdentityProof.Apple -> ConfirmedDeletionDto(provider = ConfirmedDeletionDto.Provider.APPLE, token = proof.identityToken)
        }
        return signOutOnceDeleted(api.confirmAccountDeletion(body))
    }

    private suspend fun signOutOnceDeleted(result: AppResult<*, DataError.Network>): AppResult<Unit, DataError.Network> {
        if (result is AppResult.Error) return result
        // The server has closed every session already; keep nothing.
        session.forget(SignOutReason.ACCOUNT_DELETED)
        return AppResult.Success(Unit)
    }

    private companion object {
        const val AGE_REQUIREMENT_NOT_MET = "AGE_REQUIREMENT_NOT_MET"
    }
}

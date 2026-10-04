package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.model.account.UsernameAvailability
import com.falcon.tripingly.core.network.account.AccountApi
import com.falcon.tripingly.core.network.baseLanguageTag
import com.falcon.tripingly.core.network.model.RecordConsentDto
import com.falcon.tripingly.core.network.model.UpdateMeDto

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

    private companion object {
        const val AGE_REQUIREMENT_NOT_MET = "AGE_REQUIREMENT_NOT_MET"
    }
}

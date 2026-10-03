package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.UsernameAvailability

/** The onboarding calls: profile, username check, legal documents and consents. */
interface AccountRepository {
    suspend fun checkUsername(username: String): AppResult<UsernameAvailability, DataError.Network>

    /**
     * Saves the profile and updates the session. An under-16 birth date makes the
     * server delete the account: the result is the `AGE_REQUIREMENT_NOT_MET` error
     * and the session is signed out with [com.falcon.tripingly.core.model.account.SignOutReason.UNDER_AGE].
     */
    suspend fun updateProfile(update: ProfileUpdate): AppResult<Account, DataError.Network>

    /** The current documents in [locale] (the server falls back to English). */
    suspend fun legalDocuments(locale: String): AppResult<List<LegalDocument>, DataError.Network>

    /** Grants each document at the version shown, then updates the session. */
    suspend fun accept(documents: List<LegalDocument>): AppResult<Account, DataError.Network>
}

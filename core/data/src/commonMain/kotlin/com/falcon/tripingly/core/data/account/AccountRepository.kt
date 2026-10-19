package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.DataExport
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.NotificationSettings
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.account.UsernameAvailability

/**
 * The account calls: onboarding (profile, username check, legal documents, consents) and
 * Settings (default trip visibility, notifications, data export, deletion).
 */
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

    /** The visibility new trips start with; updates the session. */
    suspend fun setDefaultTripVisibility(visibility: TripVisibility): AppResult<Account, DataError.Network>

    suspend fun notificationSettings(): AppResult<NotificationSettings, DataError.Network>

    /** Sends only this one change, so two quick taps on different switches don't undo each other. */
    suspend fun setNotification(type: NotificationType, enabled: Boolean): AppResult<NotificationSettings, DataError.Network>

    /** The latest export, null when there has been none. */
    suspend fun dataExport(): AppResult<DataExport?, DataError.Network>

    /** Starts an export (or returns the one still being made); one a day (429 `RATE_LIMITED`). */
    suspend fun requestDataExport(): AppResult<DataExport, DataError.Network>

    /**
     * Deletes the account and, once the server has taken it, signs out with
     * [com.falcon.tripingly.core.model.account.SignOutReason.ACCOUNT_DELETED] and wipes everything
     * local. 403 `REAUTH_REQUIRED` when the last Google or Apple sign-in is older than 10 minutes.
     */
    suspend fun deleteAccount(): AppResult<Unit, DataError.Network>
}

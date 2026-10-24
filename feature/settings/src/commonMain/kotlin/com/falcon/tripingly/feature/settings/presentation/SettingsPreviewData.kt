package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.account.DataExport
import com.falcon.tripingly.core.model.account.DataExportStatus
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.NotificationSettings
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_export
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.AccountDialog
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.AccountDialogState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.ExportState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.NotificationsState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

internal val previewLegalDocuments = persistentListOf(
    LegalDocument(LegalDocumentType.TERMS, "2026-09-01", "en", "https://example.com/terms", true),
    LegalDocument(LegalDocumentType.PRIVACY, "2026-09-01", "en", "https://example.com/privacy", true),
)

private val loaded = State(
    appVersion = "1.0",
    username = "jonas.k",
    notifications = NotificationsState(NotificationSettings(NotificationType.entries.toSet() - NotificationType.LIKES)),
    export = ExportState(DataExport(DataExportStatus.PENDING, downloadUrl = null, expiresAt = null), hasLoaded = true),
    legalDocuments = previewLegalDocuments,
)

/** Loaded, still loading, with Pro and private by default, and an export that failed to request. */
internal class SettingsPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        loaded,
        State(appVersion = "1.0"),
        loaded.copy(hasPro = true, defaultVisibility = TripVisibility.PRIVATE, export = ExportState(hasLoaded = true)),
        loaded.copy(export = ExportState(hasLoaded = true), message = UiText.Resource(Res.string.settings_error_export)),
    )
}

/** Loaded, loading, and failed to load. */
internal class NotificationsPreviewStates : PreviewParameterProvider<NotificationsState> {
    override val values = sequenceOf(loaded.notifications, NotificationsState(), NotificationsState(hasFailed = true))
}

/** None yet, requesting, being made, ready to download, and failed. */
internal class ExportPreviewStates : PreviewParameterProvider<ExportState> {
    override val values = sequenceOf(
        ExportState(hasLoaded = true),
        ExportState(hasLoaded = true, isRequesting = true),
        loaded.export,
        ExportState(DataExport(DataExportStatus.READY, "https://example.com/export.zip", Instant.parse("2026-10-30T10:00:00Z")), hasLoaded = true),
        ExportState(DataExport(DataExportStatus.FAILED, downloadUrl = null, expiresAt = null), hasLoaded = true),
    )
}

/** Signing out, deleting the account, deleting under way, and the fresh sign-in before deleting. */
internal class AccountDialogPreviewStates : PreviewParameterProvider<AccountDialogState> {
    override val values = sequenceOf(
        AccountDialogState(AccountDialog.SignOut),
        AccountDialogState(AccountDialog.Delete),
        AccountDialogState(AccountDialog.Delete, isDeleting = true),
        AccountDialogState(AccountDialog.Reauth),
    )
}

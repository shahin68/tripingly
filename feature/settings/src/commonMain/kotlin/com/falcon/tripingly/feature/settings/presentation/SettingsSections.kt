package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.SectionLabel
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.DataExportStatus
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_commons
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_manrope
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_photon
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_routes
import com.falcon.tripingly.feature.settings.generated.resources.settings_credits
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_download
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_failed
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_intro
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_pending
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_ready
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_ready_until
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_request
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_added
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_changes
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_comments
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_join
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_likes
import com.falcon.tripingly.feature.settings.generated.resources.settings_notifications_hint
import com.falcon.tripingly.feature.settings.generated.resources.settings_privacy_policy
import com.falcon.tripingly.feature.settings.generated.resources.settings_retry
import com.falcon.tripingly.feature.settings.generated.resources.settings_terms
import com.falcon.tripingly.feature.settings.generated.resources.settings_version
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.Action
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.ExportState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.NotificationsState
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/* The settings' groups (notifications, data export, about) and the cards and rows they're made of. */

@Composable
internal fun Notifications(state: NotificationsState, onAction: (Action) -> Unit) {
    val settings = state.settings
    when {
        settings != null -> {
            SettingsCard {
                NotificationType.entries.forEach { type ->
                    SwitchRow(
                        label = stringResource(type.label()),
                        checked = type in settings.enabled,
                        onCheckedChange = { onAction(Action.Notifications.Changed(type, it)) },
                    )
                }
            }
            Hint(stringResource(Res.string.settings_notifications_hint))
        }
        state.hasFailed -> TextButton(onClick = { onAction(Action.Notifications.Retry) }) {
            Text(stringResource(Res.string.settings_retry))
        }
        // The labels are known; only the switches wait for the server.
        else -> Skeleton {
            SettingsCard {
                NotificationType.entries.forEach { type -> SwitchRow(label = stringResource(type.label()), checked = false, onCheckedChange = {}) }
            }
        }
    }
}

@Composable
internal fun DataExportSection(state: ExportState, onAction: (Action) -> Unit) {
    val export = state.export
    val uriHandler = LocalUriHandler.current
    Hint(
        when (export?.status) {
            DataExportStatus.PENDING -> stringResource(Res.string.settings_export_pending)
            DataExportStatus.READY -> export.expiresAt?.let {
                stringResource(Res.string.settings_export_ready_until, DateUtils.formatFormal(it))
            } ?: stringResource(Res.string.settings_export_ready)
            DataExportStatus.FAILED -> stringResource(Res.string.settings_export_failed)
            DataExportStatus.EXPIRED, null -> stringResource(Res.string.settings_export_intro)
        },
    )
    val downloadUrl = export?.downloadUrl?.takeIf { export.status == DataExportStatus.READY }
    if (downloadUrl != null) {
        PrimaryButton(
            text = stringResource(Res.string.settings_export_download),
            onClick = { runCatching { uriHandler.openUri(downloadUrl) } },
            size = ButtonSize.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (export?.status != DataExportStatus.PENDING) {
        SecondaryButton(
            text = stringResource(Res.string.settings_export_request),
            onClick = { onAction(Action.RequestExport) },
            enabled = state.hasLoaded,
            isLoading = state.isRequesting,
            size = ButtonSize.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The legal documents, where our data comes from, the open-source parts we ship, and the app's version. */
@Composable
internal fun About(legalDocuments: ImmutableList<LegalDocument>, appVersion: String) {
    val uriHandler = LocalUriHandler.current
    val open = { url: String -> { runCatching { uriHandler.openUri(url) }; Unit } }
    SettingsCard {
        legalDocuments.filter { it.type != LegalDocumentType.MARKETING }.forEach { document ->
            LinkRow(
                label = stringResource(if (document.type == LegalDocumentType.TERMS) Res.string.settings_terms else Res.string.settings_privacy_policy),
                onClick = open(document.url),
            )
        }
    }
    SettingsSection(stringResource(Res.string.settings_credits))
    SettingsCard {
        // Where our places, routes, search and place photos come from, and what the app is built with.
        OsmAttribution(Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small))
        LinkRow(stringResource(Res.string.settings_credit_routes), onClick = open(ROUTES_URL))
        LinkRow(stringResource(Res.string.settings_credit_photon), onClick = open(PHOTON_URL))
        LinkRow(stringResource(Res.string.settings_credit_commons), onClick = open(COMMONS_URL))
        LinkRow(stringResource(Res.string.settings_credit_manrope), onClick = open(MANROPE_URL))
    }
    Hint(stringResource(Res.string.settings_version, appVersion))
}

/** A group of rows on one rounded card. */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = MaterialTheme.spacing.extraSmall), content = content)
    }
}

/** A row that opens something: a [label] with an optional [detail] under it and [icon] before it. */
@Composable
internal fun LinkRow(label: String, onClick: () -> Unit, detail: String? = null, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(it, contentDescription = null, modifier = Modifier.padding(MaterialTheme.spacing.small).size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(
            if (icon != null) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The whole row toggles, so the target is large enough and screen readers read it as one switch. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.skeleton(CircleShape))
    }
}

@Composable
internal fun SettingsSection(title: String) {
    SectionLabel(title, Modifier.padding(start = MaterialTheme.spacing.extraSmall, top = MaterialTheme.spacing.medium))
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall),
    )
}

private fun NotificationType.label(): StringResource = when (this) {
    NotificationType.COMMENTS -> Res.string.settings_notification_comments
    NotificationType.ADDED_TO_TRIP -> Res.string.settings_notification_added
    NotificationType.TRIP_CHANGES -> Res.string.settings_notification_changes
    NotificationType.LIKES -> Res.string.settings_notification_likes
    NotificationType.JOIN_REQUESTS -> Res.string.settings_notification_join
}

private val RowHeight = 56.dp
private const val ROUTES_URL = "https://openrouteservice.org"
private const val PHOTON_URL = "https://photon.komoot.io"
private const val COMMONS_URL = "https://commons.wikimedia.org"
private const val MANROPE_URL = "https://github.com/sharanda/manrope"

@Preview
@Composable
private fun NotificationsPreview(@PreviewParameter(NotificationsPreviewStates::class) state: NotificationsState) {
    TripinglyTheme { Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) { Notifications(state = state, onAction = {}) } }
}

@Preview
@Composable
private fun DataExportSectionPreview(@PreviewParameter(ExportPreviewStates::class) state: ExportState) {
    TripinglyTheme { Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) { DataExportSection(state = state, onAction = {}) } }
}

@Preview
@Composable
private fun AboutPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
            SettingsSection("About")
            About(legalDocuments = previewLegalDocuments, appVersion = "1.0 (42)")
        }
    }
}

@Preview
@Composable
private fun AboutDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
            About(legalDocuments = previewLegalDocuments, appVersion = "1.0 (42)")
        }
    }
}

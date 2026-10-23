package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SectionLabel
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.DataExport
import com.falcon.tripingly.core.model.account.DataExportStatus
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.NotificationSettings
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_about
import com.falcon.tripingly.feature.settings.generated.resources.settings_back
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_commons
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_photon
import com.falcon.tripingly.feature.settings.generated.resources.settings_credit_routes
import com.falcon.tripingly.feature.settings.generated.resources.settings_data
import com.falcon.tripingly.feature.settings.generated.resources.settings_delete_account
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_download
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_failed
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_intro
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_pending
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_ready
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_ready_until
import com.falcon.tripingly.feature.settings.generated.resources.settings_export_request
import com.falcon.tripingly.feature.settings.generated.resources.settings_new_trips
import com.falcon.tripingly.feature.settings.generated.resources.settings_new_trips_hint
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_added
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_changes
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_comments
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_join
import com.falcon.tripingly.feature.settings.generated.resources.settings_notification_likes
import com.falcon.tripingly.feature.settings.generated.resources.settings_notifications
import com.falcon.tripingly.feature.settings.generated.resources.settings_notifications_hint
import com.falcon.tripingly.feature.settings.generated.resources.settings_privacy_policy
import com.falcon.tripingly.feature.settings.generated.resources.settings_pro
import com.falcon.tripingly.feature.settings.generated.resources.settings_pro_active
import com.falcon.tripingly.feature.settings.generated.resources.settings_pro_get
import com.falcon.tripingly.feature.settings.generated.resources.settings_retry
import com.falcon.tripingly.feature.settings.generated.resources.settings_sign_out
import com.falcon.tripingly.feature.settings.generated.resources.settings_signed_in_as
import com.falcon.tripingly.feature.settings.generated.resources.settings_terms
import com.falcon.tripingly.feature.settings.generated.resources.settings_title
import com.falcon.tripingly.feature.settings.generated.resources.settings_version
import com.falcon.tripingly.feature.settings.generated.resources.settings_visibility_private
import com.falcon.tripingly.feature.settings.generated.resources.settings_visibility_public
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.Action
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.ExportState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.NotificationsState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.State
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    /** Tripinly Pro's paywall: buying, restoring and managing the subscription. */
    onOpenPro: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onOpenPro = onOpenPro)

    state.account?.let { AccountConfirmation(it, appleAvailable = state.appleAvailable, onAction = viewModel::onAction) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(state: State, onAction: (Action) -> Unit, onBack: () -> Unit, onOpenPro: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.DismissMessage) },
            isError = state.message !is UiText.Note,
        )
        // One readable column, also on tablets and in landscape.
        Box(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                state.username?.let { username ->
                    Text(
                        text = stringResource(Res.string.settings_signed_in_as, username),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                    )
                }

                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_pro)) },
                    supportingContent = { Text(stringResource(if (state.hasPro) Res.string.settings_pro_active else Res.string.settings_pro_get)) },
                    leadingContent = { Icon(Icons.Default.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenPro),
                )

                Section(stringResource(Res.string.settings_new_trips))
                SegmentedToggle(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium)) {
                    SegmentedToggleOption(
                        label = stringResource(Res.string.settings_visibility_public),
                        selected = state.defaultVisibility == TripVisibility.PUBLIC,
                        onClick = { onAction(Action.VisibilityChanged(TripVisibility.PUBLIC)) },
                        icon = Icons.Default.Public,
                    )
                    SegmentedToggleOption(
                        label = stringResource(Res.string.settings_visibility_private),
                        selected = state.defaultVisibility == TripVisibility.PRIVATE,
                        onClick = { onAction(Action.VisibilityChanged(TripVisibility.PRIVATE)) },
                        icon = Icons.Default.Lock,
                    )
                }
                Hint(stringResource(Res.string.settings_new_trips_hint))

                Section(stringResource(Res.string.settings_notifications))
                Notifications(state.notifications, onAction)

                Section(stringResource(Res.string.settings_data))
                DataExportSection(state.export, onAction)

                Section(stringResource(Res.string.settings_about))
                About(state.legalDocuments, state.appVersion)

                HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.small))
                OutlinedButton(
                    onClick = { onAction(Action.Account.SignOut) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    Text(stringResource(Res.string.settings_sign_out))
                }
                TextButton(
                    onClick = { onAction(Action.Account.Delete) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    Text(stringResource(Res.string.settings_delete_account), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Notifications(state: NotificationsState, onAction: (Action) -> Unit) {
    val settings = state.settings
    when {
        settings != null -> {
            NotificationType.entries.forEach { type ->
                SwitchRow(
                    label = stringResource(type.label()),
                    checked = type in settings.enabled,
                    onCheckedChange = { onAction(Action.Notifications.Changed(type, it)) },
                )
            }
            Hint(stringResource(Res.string.settings_notifications_hint))
        }
        state.hasFailed -> TextButton(
            onClick = { onAction(Action.Notifications.Retry) },
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
        ) {
            Text(stringResource(Res.string.settings_retry))
        }
        else -> Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

/** The whole row toggles, so the target is large enough and screen readers read it as one switch. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
private fun DataExportSection(state: ExportState, onAction: (Action) -> Unit) {
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
    Box(Modifier.padding(horizontal = MaterialTheme.spacing.medium)) {
        if (downloadUrl != null) {
            PrimaryButton(
                text = stringResource(Res.string.settings_export_download),
                onClick = { runCatching { uriHandler.openUri(downloadUrl) } },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (export?.status != DataExportStatus.PENDING) {
            OutlinedButton(
                onClick = { onAction(Action.RequestExport) },
                enabled = state.hasLoaded && !state.isRequesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.settings_export_request))
            }
        }
    }
}

@Composable
private fun About(legalDocuments: ImmutableList<LegalDocument>, appVersion: String) {
    val uriHandler = LocalUriHandler.current
    legalDocuments.filter { it.type != LegalDocumentType.MARKETING }.forEach { document ->
        LinkRow(
            label = stringResource(if (document.type == LegalDocumentType.TERMS) Res.string.settings_terms else Res.string.settings_privacy_policy),
            onClick = { runCatching { uriHandler.openUri(document.url) } },
        )
    }
    // Where our places, routes, search and place photos come from.
    OsmAttribution(Modifier.padding(horizontal = MaterialTheme.spacing.small))
    LinkRow(stringResource(Res.string.settings_credit_routes)) { runCatching { uriHandler.openUri(ROUTES_URL) } }
    LinkRow(stringResource(Res.string.settings_credit_photon)) { runCatching { uriHandler.openUri(PHOTON_URL) } }
    LinkRow(stringResource(Res.string.settings_credit_commons)) { runCatching { uriHandler.openUri(COMMONS_URL) } }
    Hint(stringResource(Res.string.settings_version, appVersion))
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Section(title: String) {
    SectionLabel(title, Modifier.padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.medium))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
    )
}

private fun NotificationType.label(): StringResource = when (this) {
    NotificationType.COMMENTS -> Res.string.settings_notification_comments
    NotificationType.ADDED_TO_TRIP -> Res.string.settings_notification_added
    NotificationType.TRIP_CHANGES -> Res.string.settings_notification_changes
    NotificationType.LIKES -> Res.string.settings_notification_likes
    NotificationType.JOIN_REQUESTS -> Res.string.settings_notification_join
}

private val ContentMaxWidth = 600.dp
private const val ROUTES_URL = "https://openrouteservice.org"
private const val PHOTON_URL = "https://photon.komoot.io"
private const val COMMONS_URL = "https://commons.wikimedia.org"

@Preview
@Composable
private fun SettingsScreenPreview() {
    TripinglyTheme {
        SettingsScreen(
            state = State(
                appVersion = "1.0",
                username = "jonas.k",
                notifications = NotificationsState(NotificationSettings(NotificationType.entries.toSet() - NotificationType.LIKES)),
                export = ExportState(DataExport(DataExportStatus.PENDING, downloadUrl = null, expiresAt = null), hasLoaded = true),
            ),
            onAction = {},
            onBack = {},
            onOpenPro = {},
        )
    }
}

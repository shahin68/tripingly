package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.BackAction
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_about
import com.falcon.tripingly.feature.settings.generated.resources.settings_data
import com.falcon.tripingly.feature.settings.generated.resources.settings_delete_account
import com.falcon.tripingly.feature.settings.generated.resources.settings_new_trips
import com.falcon.tripingly.feature.settings.generated.resources.settings_new_trips_hint
import com.falcon.tripingly.feature.settings.generated.resources.settings_notifications
import com.falcon.tripingly.feature.settings.generated.resources.settings_pro
import com.falcon.tripingly.feature.settings.generated.resources.settings_pro_active
import com.falcon.tripingly.feature.settings.generated.resources.settings_pro_get
import com.falcon.tripingly.feature.settings.generated.resources.settings_sign_out
import com.falcon.tripingly.feature.settings.generated.resources.settings_signed_in_as
import com.falcon.tripingly.feature.settings.generated.resources.settings_title
import com.falcon.tripingly.feature.settings.generated.resources.settings_visibility_private
import com.falcon.tripingly.feature.settings.generated.resources.settings_visibility_public
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.Action
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.State
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

/** Settings in groups of rows on cards, under the floating top bar; one readable column on tablets and in landscape. */
@Composable
internal fun SettingsScreen(state: State, onAction: (Action) -> Unit, onBack: () -> Unit, onOpenPro: () -> Unit) {
    Scaffold(
        topBar = { FloatingTopBar(title = stringResource(Res.string.settings_title), navigationIcon = { BackAction(onBack) }) },
    ) { padding ->
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.DismissMessage) },
            isError = state.message !is UiText.Note,
        )
        Box(Modifier.fillMaxSize().glassSource().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                state.username?.let { Hint(stringResource(Res.string.settings_signed_in_as, it)) }
                SettingsCard {
                    LinkRow(
                        label = stringResource(Res.string.settings_pro),
                        detail = stringResource(if (state.hasPro) Res.string.settings_pro_active else Res.string.settings_pro_get),
                        icon = Icons.Default.Directions,
                        onClick = onOpenPro,
                    )
                }

                SettingsSection(stringResource(Res.string.settings_new_trips))
                SegmentedToggle(Modifier.fillMaxWidth()) {
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

                SettingsSection(stringResource(Res.string.settings_notifications))
                Notifications(state.notifications, onAction)

                SettingsSection(stringResource(Res.string.settings_data))
                DataExportSection(state.export, onAction)

                SettingsSection(stringResource(Res.string.settings_about))
                About(state.legalDocuments, state.appVersion)

                Column(
                    modifier = Modifier.padding(top = MaterialTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    SecondaryButton(
                        text = stringResource(Res.string.settings_sign_out),
                        onClick = { onAction(Action.Account.SignOut) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { onAction(Action.Account.Delete) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.settings_delete_account), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private val ContentMaxWidth = 600.dp

@Preview
@Composable
private fun SettingsScreenPreview(@PreviewParameter(SettingsPreviewStates::class) state: State) {
    TripinglyTheme { SettingsScreen(state = state, onAction = {}, onBack = {}, onOpenPro = {}) }
}

@Preview
@Composable
private fun SettingsScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { SettingsScreen(state = SettingsPreviewStates().values.first(), onAction = {}, onBack = {}, onOpenPro = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SettingsScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) { SettingsScreen(state = SettingsPreviewStates().values.first(), onAction = {}, onBack = {}, onOpenPro = {}) }
}

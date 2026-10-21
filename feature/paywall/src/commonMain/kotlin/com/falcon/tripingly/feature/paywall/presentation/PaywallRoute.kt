package com.falcon.tripingly.feature.paywall.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.paywall.generated.resources.Res
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_active
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_active_hint
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_back
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_benefit_along
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_benefit_between
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_benefit_routes
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_benefit_times
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_buy
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_fine_print
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_intro
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_lifetime
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_lifetime_price
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_manage
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_monthly
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_monthly_price
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_offers_failed
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_privacy
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_restore
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_retry
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_terms
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_title
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_unavailable
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_yearly
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_yearly_price
import com.falcon.tripingly.feature.paywall.presentation.PaywallViewModel.Action
import com.falcon.tripingly.feature.paywall.presentation.PaywallViewModel.State
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PaywallRoute(
    onBack: () -> Unit,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PaywallScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallScreen(state: State, onAction: (Action) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.paywall_back))
                    }
                },
            )
        },
    ) { padding ->
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.OnDismissMessage) },
            isError = state.message !is UiText.Note,
        )
        // One readable column, also on tablets and in landscape.
        Box(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(Res.string.paywall_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(Res.string.paywall_intro), style = MaterialTheme.typography.bodyLarge)
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Benefits.forEach { Benefit(stringResource(it)) }
                }
                when {
                    state.hasPro -> Active(state)
                    !state.isAvailable -> Hint(stringResource(Res.string.paywall_unavailable))
                    else -> Offers(state, onAction)
                }
                Legal(state)
            }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Active(state: State) {
    val uriHandler = LocalUriHandler.current
    Text(stringResource(Res.string.paywall_active), style = MaterialTheme.typography.titleMedium)
    Hint(stringResource(Res.string.paywall_active_hint))
    state.managementUrl?.let { url ->
        OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.paywall_manage))
        }
    }
}

@Composable
private fun Offers(state: State, onAction: (Action) -> Unit) {
    when {
        state.offers.isNotEmpty() -> {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                state.offers.forEach { offer ->
                    OfferRow(
                        offer = offer,
                        selected = offer.id == state.selectedOfferId,
                        enabled = !state.isBusy,
                        onSelect = { onAction(Action.OnSelectOffer(offer.id)) },
                    )
                }
            }
            PrimaryButton(
                text = stringResource(Res.string.paywall_buy),
                onClick = { onAction(Action.OnBuy) },
                enabled = state.selectedOfferId != null && !state.isBusy,
                isLoading = state.isBuying,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.hasOffersFailed -> {
            Hint(stringResource(Res.string.paywall_offers_failed))
            OutlinedButton(onClick = { onAction(Action.OnRetryOffers) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.paywall_retry))
            }
        }
        state.isLoadingOffers -> Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
        else -> Hint(stringResource(Res.string.paywall_offers_failed))
    }
    // Apple and Google both want earlier purchases restorable from the paywall.
    TextButton(onClick = { onAction(Action.OnRestore) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
        if (state.isRestoring) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.paywall_restore))
        }
    }
}

/** The whole card selects, so the target is large and screen readers read it as one radio button. */
@Composable
private fun OfferRow(offer: ProOffer, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val (name, price) = when (offer.period) {
        ProPeriod.Monthly -> Res.string.paywall_monthly to Res.string.paywall_monthly_price
        ProPeriod.Yearly -> Res.string.paywall_yearly to Res.string.paywall_yearly_price
        ProPeriod.Lifetime -> Res.string.paywall_lifetime to Res.string.paywall_lifetime_price
    }
    OutlinedCard(
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(price, offer.price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Legal(state: State) {
    val uriHandler = LocalUriHandler.current
    if (!state.hasPro) Hint(stringResource(Res.string.paywall_fine_print))
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        state.legalDocuments.filter { it.type != LegalDocumentType.MARKETING }.forEach { document ->
            TextButton(onClick = { runCatching { uriHandler.openUri(document.url) } }) {
                Text(
                    text = stringResource(if (document.type == LegalDocumentType.TERMS) Res.string.paywall_terms else Res.string.paywall_privacy),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val Benefits: List<StringResource> = listOf(
    Res.string.paywall_benefit_routes,
    Res.string.paywall_benefit_times,
    Res.string.paywall_benefit_along,
    Res.string.paywall_benefit_between,
)

private val ContentMaxWidth = 600.dp

@Preview
@Composable
private fun PaywallScreenPreview() {
    TripinglyTheme {
        PaywallScreen(
            state = State(
                isAvailable = true,
                offers = persistentListOf(
                    ProOffer("\$rc_monthly", ProPeriod.Monthly, "€3.99"),
                    ProOffer("\$rc_annual", ProPeriod.Yearly, "€24.99"),
                    ProOffer("\$rc_lifetime", ProPeriod.Lifetime, "€59.99"),
                ),
                selectedOfferId = "\$rc_annual",
            ),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PaywallScreenActivePreview() {
    TripinglyTheme {
        PaywallScreen(state = State(isAvailable = true, hasPro = true, managementUrl = "https://play.google.com"), onAction = {}, onBack = {})
    }
}

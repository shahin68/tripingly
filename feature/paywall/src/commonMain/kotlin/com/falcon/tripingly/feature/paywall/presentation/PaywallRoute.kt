package com.falcon.tripingly.feature.paywall.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.BackAction
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.paywall.generated.resources.Res
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_active
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_active_hint
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

/** Tripinly Pro: what it adds, the offers to pick from (or that it's active), restore, and the fine print. */
@Composable
internal fun PaywallScreen(state: State, onAction: (Action) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { FloatingTopBar(title = stringResource(Res.string.paywall_title), navigationIcon = { BackAction(onBack) }) },
    ) { padding ->
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.OnDismissMessage) },
            isError = state.message !is UiText.Note,
        )
        // One readable column, also on tablets and in landscape.
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(stringResource(Res.string.paywall_intro), style = MaterialTheme.typography.bodyLarge)
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall)) {
                        Benefits.forEach { Benefit(stringResource(it)) }
                    }
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
        SecondaryButton(text = stringResource(Res.string.paywall_manage), onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.fillMaxWidth())
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
            SecondaryButton(text = stringResource(Res.string.paywall_retry), onClick = { onAction(Action.OnRetryOffers) }, modifier = Modifier.fillMaxWidth())
        }
        state.isLoadingOffers -> Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            // The offers' own rows; the prices are placeholders, drawn as shimmering bars.
            Skeleton { Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                ProPeriod.entries.forEach { OfferRow(ProOffer(id = it.name, period = it, price = "€9.99"), selected = false, enabled = false, onSelect = {}) }
            } }
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
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.heightIn(min = OfferHeight).padding(horizontal = MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.skeleton(CircleShape))
            Column(Modifier.weight(1f)) {
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.skeletonText())
                Text(
                    text = stringResource(price, offer.price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.skeletonText(),
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
private val OfferHeight = 72.dp

@Preview
@Composable
private fun PaywallScreenPreview(@PreviewParameter(PaywallPreviewStates::class) state: State) {
    TripinglyTheme { PaywallScreen(state = state, onAction = {}, onBack = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PaywallScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) { PaywallScreen(state = PaywallPreviewStates().values.first(), onAction = {}, onBack = {}) }
}

/** The picked offer, another one, and one while a purchase is under way. */
@Preview
@Composable
private fun OfferRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium)) {
            OfferRow(previewOffers[1], selected = true, enabled = true, onSelect = {})
            OfferRow(previewOffers[0], selected = false, enabled = true, onSelect = {})
            OfferRow(previewOffers[2], selected = false, enabled = false, onSelect = {})
        }
    }
}

@Preview
@Composable
private fun BenefitPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium)) {
            Benefits.forEach { Benefit(stringResource(it)) }
        }
    }
}

@Preview
@Composable
private fun OfferRowDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            OfferRow(previewOffers[1], selected = true, enabled = true, onSelect = {})
            OfferRow(previewOffers[0], selected = false, enabled = true, onSelect = {})
        }
    }
}

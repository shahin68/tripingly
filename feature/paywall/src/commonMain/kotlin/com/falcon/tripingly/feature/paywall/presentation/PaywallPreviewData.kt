package com.falcon.tripingly.feature.paywall.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.paywall.generated.resources.Res
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_error_purchase
import com.falcon.tripingly.feature.paywall.presentation.PaywallViewModel.State
import kotlinx.collections.immutable.persistentListOf

internal val previewOffers = persistentListOf(
    ProOffer("\$rc_monthly", ProPeriod.Monthly, "€3.99"),
    ProOffer("\$rc_annual", ProPeriod.Yearly, "€24.99"),
    ProOffer("\$rc_lifetime", ProPeriod.Lifetime, "€59.99"),
)

private val offering = State(
    isAvailable = true,
    offers = previewOffers,
    selectedOfferId = "\$rc_annual",
    legalDocuments = persistentListOf(
        LegalDocument(LegalDocumentType.TERMS, "2026-09-01", "en", "https://example.com/terms", true),
        LegalDocument(LegalDocumentType.PRIVACY, "2026-09-01", "en", "https://example.com/privacy", true),
    ),
)

/**
 * The offers, loading them, offers that failed to load, buying with an error, Pro already on, and a build
 * without purchases.
 */
internal class PaywallPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        offering,
        State(isAvailable = true, isLoadingOffers = true),
        State(isAvailable = true, hasOffersFailed = true),
        offering.copy(isBuying = true, message = UiText.Resource(Res.string.paywall_error_purchase)),
        offering.copy(hasPro = true, managementUrl = "https://play.google.com"),
        State(isAvailable = false),
    )
}

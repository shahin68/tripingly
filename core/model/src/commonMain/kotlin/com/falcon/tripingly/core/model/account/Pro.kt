package com.falcon.tripingly.core.model.account

/** One way to buy Tripinly Pro, as the store sells it. */
data class ProOffer(
    /** RevenueCat's package id, handed back to buy it. */
    val id: String,
    val period: ProPeriod,
    /** The store's price in the person's currency, formatted ("€4.99"). */
    val price: String,
)

enum class ProPeriod { Monthly, Yearly, Lifetime }

/** Tripinly Pro unlocks every premium feature, so any entitlement means Pro. */
val Account.hasPro: Boolean get() = entitlements.isNotEmpty()

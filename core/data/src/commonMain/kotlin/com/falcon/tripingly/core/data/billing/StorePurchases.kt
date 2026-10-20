package com.falcon.tripingly.core.data.billing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.ProOffer

/**
 * The app store's purchases through RevenueCat: Google Play on Android, the App Store on iOS. The app
 * binds it in Koin where a RevenueCat key is set; without a binding the build has no purchases.
 */
interface StorePurchases {
    /** RevenueCat's customer becomes our user id, so the server's webhook knows whose purchase it is. */
    suspend fun logIn(userId: String)

    suspend fun logOut()

    /** Tripinly Pro as it's on sale in the current offering, with the store's prices. */
    suspend fun offers(): AppResult<List<ProOffer>, DataError.Purchase>

    /** Opens the store's purchase sheet. False when the person closed it without buying. */
    suspend fun buy(offerId: String): AppResult<Boolean, DataError.Purchase>

    /** Hands the store account's earlier purchases to this user. True when they include Tripinly Pro. */
    suspend fun restore(): AppResult<Boolean, DataError.Purchase>

    /** The store's page to manage or cancel the subscription; null without one. */
    suspend fun managementUrl(): String?
}

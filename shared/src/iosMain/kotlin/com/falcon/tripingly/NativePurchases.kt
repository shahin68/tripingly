package com.falcon.tripingly

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.billing.StorePurchases
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * App Store purchases through RevenueCat, implemented in Swift (`iosApp/NativePurchases.swift`) and handed
 * to [MainViewController]. Results come back as [NativePurchaseResult] names, offers as [NativeOffer]s.
 */
interface NativePurchases {
    /** Starts RevenueCat. False when the key is missing or the app has no RevenueCat package: no purchases. */
    fun start(apiKey: String): Boolean

    fun logIn(userId: String, completion: () -> Unit)

    fun logOut(completion: () -> Unit)

    /** The current offering's monthly, yearly and lifetime packages; null and an error name when it failed. */
    fun offers(completion: (List<NativeOffer>?, String?) -> Unit)

    fun buy(offerId: String, completion: (String) -> Unit)

    /** [NativePurchaseResult.BOUGHT] when the restored purchases include Tripinly Pro, else NOTHING. */
    fun restore(completion: (String) -> Unit)

    fun managementUrl(completion: (String?) -> Unit)
}

/** [period] is `monthly`, `yearly` or `lifetime`; [price] is the store's, formatted. */
data class NativeOffer(val id: String, val period: String, val price: String)

/** What Swift reports for a purchase or restore, and the error names offers use. */
object NativePurchaseResult {
    const val BOUGHT = "bought"
    const val CANCELLED = "cancelled"
    const val NOTHING = "nothing"
    const val PENDING = "pending"
    const val NOT_ALLOWED = "not_allowed"
    const val NO_INTERNET = "no_internet"
    const val FAILED = "failed"
}

internal class IosStorePurchases(private val native: NativePurchases) : StorePurchases {

    override suspend fun logIn(userId: String) = suspendCancellableCoroutine { continuation ->
        native.logIn(userId) { if (continuation.isActive) continuation.resume(Unit) }
    }

    override suspend fun logOut() = suspendCancellableCoroutine { continuation ->
        native.logOut { if (continuation.isActive) continuation.resume(Unit) }
    }

    override suspend fun offers(): AppResult<List<ProOffer>, DataError.Purchase> = suspendCancellableCoroutine { continuation ->
        native.offers { offers, error ->
            if (!continuation.isActive) return@offers
            continuation.resume(
                if (offers == null) {
                    AppResult.Error(error.toPurchaseError())
                } else {
                    AppResult.Success(offers.mapNotNull { it.toProOffer() })
                },
            )
        }
    }

    override suspend fun buy(offerId: String): AppResult<Boolean, DataError.Purchase> = suspendCancellableCoroutine { continuation ->
        native.buy(offerId) { result ->
            if (!continuation.isActive) return@buy
            continuation.resume(
                when (result) {
                    NativePurchaseResult.BOUGHT -> AppResult.Success(true)
                    NativePurchaseResult.CANCELLED -> AppResult.Success(false)
                    else -> AppResult.Error(result.toPurchaseError())
                },
            )
        }
    }

    override suspend fun restore(): AppResult<Boolean, DataError.Purchase> = suspendCancellableCoroutine { continuation ->
        native.restore { result ->
            if (!continuation.isActive) return@restore
            continuation.resume(
                when (result) {
                    NativePurchaseResult.BOUGHT -> AppResult.Success(true)
                    NativePurchaseResult.NOTHING -> AppResult.Success(false)
                    else -> AppResult.Error(result.toPurchaseError())
                },
            )
        }
    }

    override suspend fun managementUrl(): String? = suspendCancellableCoroutine { continuation ->
        native.managementUrl { url -> if (continuation.isActive) continuation.resume(url) }
    }

    private fun NativeOffer.toProOffer(): ProOffer? {
        val period = when (period) {
            "monthly" -> ProPeriod.Monthly
            "yearly" -> ProPeriod.Yearly
            "lifetime" -> ProPeriod.Lifetime
            else -> return null
        }
        return ProOffer(id = id, period = period, price = price)
    }

    private fun String?.toPurchaseError(): DataError.Purchase = when (this) {
        NativePurchaseResult.PENDING -> DataError.Purchase.Pending
        NativePurchaseResult.NOT_ALLOWED -> DataError.Purchase.NotAllowed
        NativePurchaseResult.NO_INTERNET -> DataError.Purchase.NoInternet
        else -> DataError.Purchase.Store()
    }
}

package com.falcon.tripingly.billing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.falcon.tripingly.BuildConfig
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.billing.StorePurchases
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import java.lang.ref.WeakReference

/** Google Play purchases through RevenueCat. The store's sheet needs the activity on screen. */
internal class RevenueCatPurchases(private val currentActivity: () -> Activity?) : StorePurchases {

    private val purchases get() = Purchases.sharedInstance

    override suspend fun logIn(userId: String) {
        // Offline: the purchase still carries this user id once the store reaches RevenueCat.
        runCatching { purchases.awaitLogIn(userId) }
    }

    override suspend fun logOut() {
        runCatching { purchases.awaitLogOut() }
    }

    override suspend fun offers(): AppResult<List<ProOffer>, DataError.Purchase> = attempt {
        val offering = purchases.awaitOfferings().current
        offering?.availablePackages.orEmpty().mapNotNull { pack ->
            val period = when (pack.packageType) {
                PackageType.MONTHLY -> ProPeriod.Monthly
                PackageType.ANNUAL -> ProPeriod.Yearly
                PackageType.LIFETIME -> ProPeriod.Lifetime
                else -> return@mapNotNull null
            }
            ProOffer(id = pack.identifier, period = period, price = pack.product.price.formatted)
        }
    }

    override suspend fun buy(offerId: String): AppResult<Boolean, DataError.Purchase> {
        val activity = currentActivity() ?: return AppResult.Error(DataError.Purchase.Unavailable)
        return attempt {
            val pack = purchases.awaitOfferings().current?.availablePackages?.firstOrNull { it.identifier == offerId }
                ?: throw IllegalStateException("The offer is no longer on sale")
            try {
                purchases.awaitPurchase(PurchaseParams.Builder(activity, pack).build())
                true
            } catch (e: PurchasesTransactionException) {
                if (e.userCancelled) false else throw e
            }
        }
    }

    override suspend fun restore(): AppResult<Boolean, DataError.Purchase> = attempt { purchases.awaitRestore().hasPro() }

    override suspend fun managementUrl(): String? =
        runCatching { purchases.awaitCustomerInfo().managementURL?.toString() }.getOrNull()

    private fun CustomerInfo.hasPro() = entitlements[PRO_ENTITLEMENT]?.isActive == true

    private suspend fun <T> attempt(block: suspend () -> T): AppResult<T, DataError.Purchase> = try {
        AppResult.Success(block())
    } catch (e: PurchasesException) {
        AppResult.Error(
            when (e.code) {
                PurchasesErrorCode.NetworkError -> DataError.Purchase.NoInternet
                PurchasesErrorCode.PaymentPendingError -> DataError.Purchase.Pending
                PurchasesErrorCode.PurchaseNotAllowedError, PurchasesErrorCode.InsufficientPermissionsError ->
                    DataError.Purchase.NotAllowed
                else -> DataError.Purchase.Store(e.message)
            },
        )
    } catch (e: IllegalStateException) {
        AppResult.Error(DataError.Purchase.Store(e.message))
    }

    companion object {
        /** RevenueCat's entitlement that is Tripinly Pro. */
        private const val PRO_ENTITLEMENT = "tripinly_pro"

        /**
         * Starts RevenueCat with the key from `local.properties` and keeps track of the activity on screen.
         * Null when the key is missing: the build has no purchases.
         */
        fun initialize(application: Application): RevenueCatPurchases? {
            if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return null
            Purchases.configure(PurchasesConfiguration.Builder(application, BuildConfig.REVENUECAT_API_KEY).build())
            var resumed = WeakReference<Activity>(null)
            application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        resumed = WeakReference(activity)
                    }

                    override fun onActivityPaused(activity: Activity) {
                        if (resumed.get() === activity) resumed = WeakReference(null)
                    }

                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                    override fun onActivityDestroyed(activity: Activity) = Unit
                },
            )
            return RevenueCatPurchases { resumed.get() }
        }
    }
}

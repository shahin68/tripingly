import Shared
#if canImport(RevenueCat)
import RevenueCat
#endif

/// App Store purchases through RevenueCat for the shared code. Results are the names in the shared
/// `NativePurchaseResult`: bought, cancelled, nothing, pending, not_allowed, no_internet, failed.
final class AppNativePurchases: NativePurchases {

    static let shared = AppNativePurchases()

    /// The key comes from local.properties (`revenuecat.iosKey`); without it the app has no purchases.
    func start(apiKey: String) -> Bool {
        #if canImport(RevenueCat)
        guard !apiKey.isEmpty else { return false }
        if !Purchases.isConfigured { Purchases.configure(withAPIKey: apiKey) }
        return true
        #else
        return false
        #endif
    }

    func logIn(userId: String, completion: @escaping () -> Void) {
        #if canImport(RevenueCat)
        Purchases.shared.logIn(userId) { _, _, _ in completion() }
        #else
        completion()
        #endif
    }

    func logOut(completion: @escaping () -> Void) {
        #if canImport(RevenueCat)
        Purchases.shared.logOut { _, _ in completion() }
        #else
        completion()
        #endif
    }

    func offers(completion: @escaping ([NativeOffer]?, String?) -> Void) {
        #if canImport(RevenueCat)
        Purchases.shared.getOfferings { offerings, error in
            if let error {
                completion(nil, Self.name(of: error))
                return
            }
            let offers = (offerings?.current?.availablePackages ?? []).compactMap { package -> NativeOffer? in
                let period: String
                switch package.packageType {
                case .monthly: period = "monthly"
                case .annual: period = "yearly"
                case .lifetime: period = "lifetime"
                default: return nil
                }
                return NativeOffer(id: package.identifier, period: period, price: package.storeProduct.localizedPriceString)
            }
            completion(offers, nil)
        }
        #else
        completion(nil, "failed")
        #endif
    }

    func buy(offerId: String, completion: @escaping (String) -> Void) {
        #if canImport(RevenueCat)
        Purchases.shared.getOfferings { offerings, error in
            guard let package = offerings?.current?.availablePackages.first(where: { $0.identifier == offerId }) else {
                completion(error.map(Self.name(of:)) ?? "failed")
                return
            }
            Purchases.shared.purchase(package: package) { _, _, error, userCancelled in
                if userCancelled {
                    completion("cancelled")
                } else if let error {
                    completion(Self.name(of: error))
                } else {
                    completion("bought")
                }
            }
        }
        #else
        completion("failed")
        #endif
    }

    func restore(completion: @escaping (String) -> Void) {
        #if canImport(RevenueCat)
        Purchases.shared.restorePurchases { info, error in
            if let error {
                completion(Self.name(of: error))
            } else {
                completion(info?.entitlements[Self.proEntitlement]?.isActive == true ? "bought" : "nothing")
            }
        }
        #else
        completion("failed")
        #endif
    }

    func managementUrl(completion: @escaping (String?) -> Void) {
        #if canImport(RevenueCat)
        Purchases.shared.getCustomerInfo { info, _ in completion(info?.managementURL?.absoluteString) }
        #else
        completion(nil)
        #endif
    }

    #if canImport(RevenueCat)
    /// RevenueCat's entitlement that is Tripinly Pro.
    private static let proEntitlement = "tripinly_pro"

    private static func name(of error: Error) -> String {
        switch error as? RevenueCat.ErrorCode {
        case .purchaseCancelledError: return "cancelled"
        case .paymentPendingError: return "pending"
        case .purchaseNotAllowedError, .insufficientPermissionsError: return "not_allowed"
        case .networkError: return "no_internet"
        default: return "failed"
        }
    }
    #endif
}

import SwiftUI
import UserNotifications
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                #if canImport(GoogleSignIn)
                // Google's sign-in sheet returns to the app through its URL scheme.
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
                #endif
        }
    }
}

/// Push needs the app delegate: APNs reports the device's address here, and a push tapped to launch
/// the app is delivered only to a delegate set before launching ends.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = AppNativePush.shared
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        AppNativePush.shared.didRegister(deviceToken: deviceToken)
    }
}

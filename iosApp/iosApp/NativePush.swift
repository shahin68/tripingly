import Shared
import UIKit
import UserNotifications
#if canImport(FirebaseMessaging)
import FirebaseCore
import FirebaseMessaging
#endif

/// Firebase Cloud Messaging for the shared code: it registers with APNs, hands out the FCM token and
/// passes tapped pushes on. The server sends the notification itself, already in the user's language.
final class AppNativePush: NSObject, NativePush, UNUserNotificationCenterDelegate {

    static let shared = AppNativePush()

    private var listener: (any NativePushListener)?
    /// A push tapped before the shared code was listening (it launched the app).
    private var pendingTap: (deepLink: String, notificationId: String?)?

    /// The values come from local.properties (`firebase.*`); without them the app has no push.
    func start(projectId: String, senderId: String, appId: String, apiKey: String) -> Bool {
        #if canImport(FirebaseMessaging)
        guard ![projectId, senderId, appId, apiKey].contains(where: \.isEmpty) else { return false }
        if FirebaseApp.app() == nil {
            let options = FirebaseOptions(googleAppID: appId, gcmSenderID: senderId)
            options.projectID = projectId
            options.apiKey = apiKey
            FirebaseApp.configure(options: options)
        }
        Messaging.messaging().delegate = self
        UIApplication.shared.registerForRemoteNotifications()
        return true
        #else
        return false
        #endif
    }

    func token(completion: @escaping (String?) -> Void) {
        #if canImport(FirebaseMessaging)
        // Fails while APNs hasn't answered; the token then arrives through the messaging delegate.
        Messaging.messaging().token { token, _ in completion(token) }
        #else
        completion(nil)
        #endif
    }

    func setListener(listener: any NativePushListener) {
        self.listener = listener
        if let tap = pendingTap {
            pendingTap = nil
            listener.onOpened(deepLink: tap.deepLink, notificationId: tap.notificationId)
        }
    }

    /// The device's APNs address, from the app delegate.
    func didRegister(deviceToken: Data) {
        #if canImport(FirebaseMessaging)
        Messaging.messaging().apnsToken = deviceToken
        #endif
    }

    /// While the app is open the push shows as a banner too, as Android does.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let data = response.notification.request.content.userInfo
        if let deepLink = data["deepLink"] as? String {
            let notificationId = data["notificationId"] as? String
            if let listener {
                listener.onOpened(deepLink: deepLink, notificationId: notificationId)
            } else {
                pendingTap = (deepLink, notificationId)
            }
        }
        completionHandler()
    }
}

#if canImport(FirebaseMessaging)
extension AppNativePush: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        if let fcmToken { listener?.onNewToken(token: fcmToken) }
    }
}
#endif

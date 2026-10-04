import AuthenticationServices
import Shared
import UIKit
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// The iOS sign-in SDKs for the shared code: they only return the provider's
/// token; the app exchanges it with the Tripinly backend.
final class AppNativeSignIn: NSObject, NativeSignIn {

    static let shared = AppNativeSignIn()

    /// Google needs the GoogleSignIn package and `GIDClientID` in Info.plist.
    var googleConfigured: Bool {
        #if canImport(GoogleSignIn)
        return Bundle.main.object(forInfoDictionaryKey: "GIDClientID") is String
        #else
        return false
        #endif
    }

    func google(completion: @escaping (String?, KotlinBoolean) -> Void) {
        #if canImport(GoogleSignIn)
        guard let presenter = Self.topViewController() else {
            completion(nil, true)
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError?, error.code == GIDSignInError.canceled.rawValue {
                completion(nil, false)
            } else if let token = result?.user.idToken?.tokenString {
                completion(token, false)
            } else {
                completion(nil, true)
            }
        }
        #else
        completion(nil, true)
        #endif
    }

    func signOut() {
        #if canImport(GoogleSignIn)
        GIDSignIn.sharedInstance.signOut()
        #endif
    }

    private var appleCompletion: ((String?, String?, String?, String?, KotlinBoolean) -> Void)?

    func apple(completion: @escaping (String?, String?, String?, String?, KotlinBoolean) -> Void) {
        appleCompletion = completion
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    private func finishApple(_ token: String?, _ code: String?, _ given: String?, _ family: String?, failed: Bool) {
        appleCompletion?(token, code, given, family, KotlinBoolean(bool: failed))
        appleCompletion = nil
    }

    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

extension AppNativeSignIn: ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let token = credential.identityToken.flatMap({ String(data: $0, encoding: .utf8) }),
            let code = credential.authorizationCode.flatMap({ String(data: $0, encoding: .utf8) })
        else {
            finishApple(nil, nil, nil, nil, failed: true)
            return
        }
        // Apple sends the name only on the first sign-in.
        finishApple(token, code, credential.fullName?.givenName, credential.fullName?.familyName, failed: false)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        finishApple(nil, nil, nil, nil, failed: !cancelled)
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        Self.topViewController()?.view.window ?? ASPresentationAnchor()
    }
}

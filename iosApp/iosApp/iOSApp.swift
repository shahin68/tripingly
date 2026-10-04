import SwiftUI
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

@main
struct iOSApp: App {
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
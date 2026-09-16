import SwiftUI
import GoogleMaps

@main
struct iOSApp: App {
    init() {
        if let path = Bundle.main.path(forResource: "Config", ofType: "plist"),
           let config = NSDictionary(contentsOfFile: path),
           let apiKey = config["MAPS_API_KEY"] as? String {
            GMSServices.provideAPIKey(apiKey)
        }
        GMSServices.addInternalUsageAttributionID("gmp_git_agentskills_v1")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
import SwiftUI

@main
struct voyageApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // Parse country data in the background while the globe scene loads
        CountryDataCache.prewarm()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

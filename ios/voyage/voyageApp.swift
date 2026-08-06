import SwiftUI

@main
struct voyageApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // Parse country data in the background while the globe scene loads
        CountryDataCache.prewarm()

        // Pre-render 3D medal artwork so the Achievements tab opens instantly
        // (medal emojis mirror the achievements built in AchievementsView)
        MedalSceneView.prewarmArtwork(
            medals: ["🌍", "🏛️", "⭐️"] + Continent.allCases.filter { $0 != .antarctica }.map(\.medal)
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

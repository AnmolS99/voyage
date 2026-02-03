import SwiftUI

struct StarryBackground: View {
    let starCount = 150

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black

                ForEach(0..<starCount, id: \.self) { i in
                    Circle()
                        .fill(Color.white.opacity(Double.random(in: 0.3...1.0)))
                        .frame(width: CGFloat.random(in: 1...3), height: CGFloat.random(in: 1...3))
                        .position(
                            x: CGFloat.random(in: 0...geometry.size.width),
                            y: CGFloat.random(in: 0...geometry.size.height)
                        )
                }
            }
        }
        .ignoresSafeArea()
    }
}

struct ContentView: View {
    @StateObject private var globeState = GlobeState()
    @State private var showingSettings = false
    @State private var showingAchievements = false

    var body: some View {
        ZStack {
            // Background - starry or warm gradient based on dark mode (only for globe)
            if globeState.viewMode == .globe {
                if globeState.isDarkMode {
                    StarryBackground()
                } else {
                    LinearGradient(
                        colors: [AppColors.backgroundLightTop, AppColors.backgroundLightBottom],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea()
                }
            }

            // Globe or Map view - fullscreen
            GlobeView(globeState: globeState)
                .ignoresSafeArea()
                .opacity(globeState.viewMode == .globe ? 1 : 0)
                .allowsHitTesting(globeState.viewMode == .globe)

            MapView(globeState: globeState)
                .ignoresSafeArea()
                .opacity(globeState.viewMode == .map ? 1 : 0)
                .allowsHitTesting(globeState.viewMode == .map)

            // UI Overlay
            VStack {
                // Header at top
                header

                Spacer()

                // Bottom info panel
                bottomPanel
            }
        }
        .animation(.easeInOut(duration: 0.3), value: globeState.viewMode)
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
        .onChange(of: globeState.viewMode) { _, newMode in
            if newMode == .map {
                OrientationManager.shared.lockToLandscape()
            } else {
                OrientationManager.shared.unlock()
                OrientationManager.shared.setNeedsOrientationUpdate()
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(globeState: globeState)
        }
        .sheet(isPresented: $showingAchievements) {
            AchievementsView(globeState: globeState)
        }
    }

    private var header: some View {
        HStack {
            Button(action: {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    globeState.viewMode = globeState.viewMode == .globe ? .map : .globe
                }
            }) {
                Text(globeState.viewMode == .globe ? "🗺️" : "🌍")
                    .font(.system(size: 32))
            }

            Spacer()

            // Achievements button
            Button(action: {
                showingAchievements = true
            }) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                    )
                    .shadow(color: AppColors.buttonColor(isDarkMode: globeState.isDarkMode).opacity(0.4), radius: 8, y: 4)
            }

            // Dark mode toggle
            Button(action: {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                    globeState.isDarkMode.toggle()
                }
            }) {
                Image(systemName: globeState.isDarkMode ? "sun.max.fill" : "moon.fill")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                    )
                    .shadow(color: AppColors.buttonColor(isDarkMode: globeState.isDarkMode).opacity(0.4), radius: 8, y: 4)
            }

            // Settings button
            Button(action: {
                showingSettings = true
            }) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                    )
                    .shadow(color: AppColors.buttonColor(isDarkMode: globeState.isDarkMode).opacity(0.4), radius: 8, y: 4)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private var bottomPanel: some View {
        VStack(spacing: 12) {
            // Selected country display with Add Visit button
            if let country = globeState.selectedCountry {
                VStack(spacing: 12) {
                    HStack(spacing: 10) {
                        Text(globeState.flagForCountry(country))
                            .font(.system(size: 24))

                        Text(country)
                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                            .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))
                    }

                    HStack(spacing: 12) {
                        // Add/Remove Visit button
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                if globeState.isVisited(country) {
                                    globeState.removeVisit(country)
                                } else {
                                    globeState.addVisit(country)
                                }
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: globeState.isVisited(country) ? "checkmark.circle.fill" : "plus.circle")
                                    .font(.system(size: 16, weight: .medium))
                                Text(globeState.isVisited(country) ? "Visited" : "Add Visit")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                Capsule()
                                    .fill(globeState.isVisited(country) ?
                                          AppColors.buttonVisited :
                                          AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                            )
                        }

                        // Add/Remove Wishlist button
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                if globeState.isInWishlist(country) {
                                    globeState.removeFromWishlist(country)
                                } else {
                                    globeState.addToWishlist(country)
                                }
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: globeState.isInWishlist(country) ? "heart.fill" : "heart")
                                    .font(.system(size: 16, weight: .medium))
                                Text(globeState.isInWishlist(country) ? "Wishlist" : "Add Wish")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                Capsule()
                                    .fill(globeState.isInWishlist(country) ?
                                          AppColors.wishlist :
                                          AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                            )
                        }

                        // Close button
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                globeState.deselectCountry()
                            }
                        }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(globeState.isDarkMode ? .white : AppColors.closeButtonText)
                                .frame(width: 36, height: 36)
                                .background(
                                    Circle()
                                        .fill(globeState.isDarkMode ? AppColors.closeButtonDark : AppColors.closeButtonLight)
                                )
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                        .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
                )
                .transition(.scale.combined(with: .opacity))
            }

            // Progress bar
            VStack(spacing: 8) {
                HStack {
                    Text("\(globeState.visitedUNCountries.count) of \(globeState.totalUNCountries) countries")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(AppColors.textSecondary(isDarkMode: globeState.isDarkMode))

                    Spacer()

                    Text("\(Int(Double(globeState.visitedUNCountries.count) / Double(globeState.totalUNCountries) * 100))%")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(globeState.isDarkMode ? AppColors.progressDarkStart : AppColors.buttonLight)
                }

                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        // Background track
                        RoundedRectangle(cornerRadius: 6)
                            .fill(AppColors.track(isDarkMode: globeState.isDarkMode))

                        // Progress fill
                        RoundedRectangle(cornerRadius: 6)
                            .fill(
                                LinearGradient(
                                    colors: globeState.isDarkMode ?
                                        [AppColors.progressDarkStart, AppColors.progressDarkEnd] :
                                        [AppColors.progressLightStart, AppColors.progressLightEnd],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: max(0, geometry.size.width * CGFloat(globeState.visitedUNCountries.count) / CGFloat(globeState.totalUNCountries)))
                            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: globeState.visitedUNCountries.count)
                    }
                }
                .frame(height: 12)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(globeState.isDarkMode ? AppColors.cardDarkSecondary.opacity(0.8) : .white.opacity(0.7))
            )
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .animation(.spring(response: 0.4, dampingFraction: 0.7), value: globeState.selectedCountry)
    }

}

enum ViewMode {
    case globe
    case map
}

class GlobeState: ObservableObject {
    @Published var selectedCountry: String?
    @Published var selectedCountries: Set<String> = []
    @Published var visitedCountries: Set<String> = []
    @Published var wishlistCountries: Set<String> = []
    @Published var zoomLevel: Float = 4.0
    @Published var isDarkMode: Bool = false
    @Published var isAutoRotating: Bool = true
    @Published var targetCountryCenter: (lat: Double, lon: Double)?
    @Published var viewMode: ViewMode = .globe
    let totalUNCountries = 195

    // Flag codes loaded from GeoJSON
    private var countryFlagCodes: [String: String] = [:]

    // Territories that are not UN member or observer states (excluded from progress count)
    static let nonUNTerritories: Set<String> = [
        "Antarctica",
        "Bermuda",
        "Falkland Islands",
        "French Guiana",
        "French Southern and Antarctic Lands",
        "Greenland",
        "Kosovo",
        "New Caledonia",
        "Northern Cyprus",
        "Puerto Rico",
        "Taiwan",
        "Western Sahara"
    ]

    // Only count UN-recognized countries toward progress
    var visitedUNCountries: Set<String> {
        visitedCountries.subtracting(Self.nonUNTerritories)
    }

    private let iCloudStore = NSUbiquitousKeyValueStore.default
    private let userDefaults = UserDefaults.standard
    private let visitedCountriesKey = "visitedCountries"
    private let wishlistCountriesKey = "wishlistCountries"

    init() {
        loadFlagCodes()
        loadData()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(iCloudDidChange),
            name: NSUbiquitousKeyValueStore.didChangeExternallyNotification,
            object: iCloudStore
        )

        iCloudStore.synchronize()
    }

    private func loadFlagCodes() {
        let countries = GeoJSONParser.loadCountries()
        for country in countries {
            if let flagCode = country.flagCode {
                countryFlagCodes[country.name] = flagCode
            }
        }
    }

    private func loadData() {
        // Load visited countries
        let localCountries = Set(userDefaults.stringArray(forKey: visitedCountriesKey) ?? [])
        let cloudCountries = Set(iCloudStore.array(forKey: visitedCountriesKey) as? [String] ?? [])
        visitedCountries = localCountries.union(cloudCountries)

        // Load wishlist countries
        let localWishlist = Set(userDefaults.stringArray(forKey: wishlistCountriesKey) ?? [])
        let cloudWishlist = Set(iCloudStore.array(forKey: wishlistCountriesKey) as? [String] ?? [])
        wishlistCountries = localWishlist.union(cloudWishlist)

        // Sync merged data back to both stores
        if visitedCountries != localCountries || visitedCountries != cloudCountries ||
           wishlistCountries != localWishlist || wishlistCountries != cloudWishlist {
            saveData()
        }
    }

    private func saveData() {
        let visitedArray = Array(visitedCountries)
        userDefaults.set(visitedArray, forKey: visitedCountriesKey)
        iCloudStore.set(visitedArray, forKey: visitedCountriesKey)

        let wishlistArray = Array(wishlistCountries)
        userDefaults.set(wishlistArray, forKey: wishlistCountriesKey)
        iCloudStore.set(wishlistArray, forKey: wishlistCountriesKey)

        iCloudStore.synchronize()
    }

    @objc private func iCloudDidChange(_ notification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.loadData()
        }
    }

    func selectCountry(_ name: String, center: (lat: Double, lon: Double)? = nil) {
        selectedCountry = name
        selectedCountries.insert(name)
        isAutoRotating = false
        targetCountryCenter = center
    }

    func addVisit(_ name: String) {
        visitedCountries.insert(name)
        saveData()
    }

    func removeVisit(_ name: String) {
        visitedCountries.remove(name)
        saveData()
    }

    func isVisited(_ name: String) -> Bool {
        visitedCountries.contains(name)
    }

    func addToWishlist(_ name: String) {
        wishlistCountries.insert(name)
        saveData()
    }

    func removeFromWishlist(_ name: String) {
        wishlistCountries.remove(name)
        saveData()
    }

    func isInWishlist(_ name: String) -> Bool {
        wishlistCountries.contains(name)
    }

    func deselectCountry() {
        selectedCountry = nil
        targetCountryCenter = nil
        isAutoRotating = true
    }

    func resetSelection() {
        selectedCountry = nil
        selectedCountries.removeAll()
        targetCountryCenter = nil
        isAutoRotating = true
    }

    func resetAllData() {
        selectedCountry = nil
        selectedCountries.removeAll()
        visitedCountries.removeAll()
        wishlistCountries.removeAll()
        targetCountryCenter = nil
        isAutoRotating = true
        saveData()
    }

    func zoomIn() {
        zoomLevel = max(1.2, zoomLevel - 0.5)
    }

    func zoomOut() {
        zoomLevel = min(10.0, zoomLevel + 0.5)
    }

    // Get flag emoji for a country
    func flagForCountry(_ name: String) -> String {
        if let code = countryFlagCodes[name] {
            return flagEmoji(from: code)
        }
        return "🌍" // Generic globe emoji as fallback
    }

    private func flagEmoji(from countryCode: String) -> String {
        let base: UInt32 = 127397
        var emoji = ""
        for scalar in countryCode.uppercased().unicodeScalars {
            if let scalar = UnicodeScalar(base + scalar.value) {
                emoji.append(Character(scalar))
            }
        }
        return emoji
    }
}

#Preview {
    ContentView()
}

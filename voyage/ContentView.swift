import SwiftUI

struct ContentView: View {
    @StateObject private var globeState = GlobeState()

    var body: some View {
        TabView {
            HomeView(globeState: globeState)
                .tabItem {
                    Label("Home", systemImage: "globe")
                }

            AchievementsView(globeState: globeState)
                .tabItem {
                    Label("Achievements", systemImage: "trophy.fill")
                }

            SettingsView(globeState: globeState)
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
        }
        .tint(globeState.isDarkMode ? AppColors.buttonDark : AppColors.buttonLight)
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }
}

enum ViewMode {
    case globe
    case map
}

enum GlobeStyle: String, CaseIterable {
    case stylized
    case natural
    case realistic

    var textureName: String {
        switch self {
        case .stylized: return "StylizedEarthTexture"
        case .natural: return "NaturalEarthTexture"
        case .realistic: return "EarthTexture"
        }
    }

    var displayName: String {
        switch self {
        case .stylized: return "Stylized"
        case .natural: return "Natural"
        case .realistic: return "Realistic"
        }
    }
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
    @Published var globeStyle: GlobeStyle = .realistic
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
    private let globeStyleKey = "globeStyle"
    private let isDarkModeKey = "isDarkMode"

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
        let countries = CountryDataCache.shared.countries
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

        // Load globe style (prefer iCloud, fall back to local)
        if let raw = iCloudStore.string(forKey: globeStyleKey) ?? userDefaults.string(forKey: globeStyleKey),
           let style = GlobeStyle(rawValue: raw) {
            globeStyle = style
        }

        // Load dark mode
        if userDefaults.object(forKey: isDarkModeKey) != nil || iCloudStore.object(forKey: isDarkModeKey) != nil {
            isDarkMode = iCloudStore.bool(forKey: isDarkModeKey) || userDefaults.bool(forKey: isDarkModeKey)
        }

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

        userDefaults.set(globeStyle.rawValue, forKey: globeStyleKey)
        iCloudStore.set(globeStyle.rawValue, forKey: globeStyleKey)

        userDefaults.set(isDarkMode, forKey: isDarkModeKey)
        iCloudStore.set(isDarkMode, forKey: isDarkModeKey)

        iCloudStore.synchronize()
    }

    func setGlobeStyle(_ style: GlobeStyle) {
        globeStyle = style
        saveData()
    }

    func toggleDarkMode() {
        isDarkMode.toggle()
        saveData()
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

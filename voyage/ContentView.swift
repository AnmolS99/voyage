import SwiftUI

struct ContentView: View {
    @StateObject private var globeState = GlobeState()
    @State private var selectedTab = 0
    @State private var showDailyBadge = false

    private static let badgeDateKey = "dailyBadgeNextDate"

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(globeState: globeState)
                .tabItem {
                    Label("Home", systemImage: "globe")
                }
                .tag(0)

            ChallengeCalendarView(globeState: globeState)
                .tabItem {
                    Label("Daily", systemImage: "calendar")
                }
                .tag(1)
                .badge(showDailyBadge ? "!" : nil)

            AchievementsView(globeState: globeState)
                .tabItem {
                    Label("Achievements", systemImage: "trophy.fill")
                }
                .tag(2)

            SettingsView(globeState: globeState)
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
                .tag(3)
        }
        .tint(globeState.isDarkMode ? AppColors.buttonDark : AppColors.buttonLight)
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
        .onAppear {
            let nextDate = UserDefaults.standard.object(forKey: Self.badgeDateKey) as? Date ?? .distantPast
            showDailyBadge = Date() >= nextDate
        }
        .onChange(of: selectedTab) {
            if selectedTab == 1 {
                showDailyBadge = false
                let tomorrow = Calendar.current.startOfDay(for: Calendar.current.date(byAdding: .day, value: 1, to: Date())!)
                UserDefaults.standard.set(tomorrow, forKey: Self.badgeDateKey)
            }
        }
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
    @Published var checkedCities: [String: Set<String>] = [:]
    @Published var checkedAttractions: [String: Set<String>] = [:]
    @Published var zoomLevel: Float = 4.0
    @Published var isDarkMode: Bool = false
    @Published var isAutoRotating: Bool = true
    @Published var targetCountryCenter: (lat: Double, lon: Double)?
    @Published var viewMode: ViewMode = .globe
    @Published var globeStyle: GlobeStyle = .realistic
    @Published var mapStyle: GlobeStyle = .realistic
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
    private let mapStyleKey = "mapStyle"
    private let isDarkModeKey = "isDarkMode"
    private let checkedCitiesKey = "checkedCities"
    private let checkedAttractionsKey = "checkedAttractions"

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

        // Load checked cities
        let localCities = userDefaults.dictionary(forKey: checkedCitiesKey) as? [String: [String]] ?? [:]
        let cloudCities = iCloudStore.dictionary(forKey: checkedCitiesKey) as? [String: [String]] ?? [:]
        checkedCities = mergeDictionaries(localCities, cloudCities)

        // Load checked attractions
        let localAttractions = userDefaults.dictionary(forKey: checkedAttractionsKey) as? [String: [String]] ?? [:]
        let cloudAttractions = iCloudStore.dictionary(forKey: checkedAttractionsKey) as? [String: [String]] ?? [:]
        checkedAttractions = mergeDictionaries(localAttractions, cloudAttractions)

        // Load globe style (prefer iCloud, fall back to local)
        if let raw = iCloudStore.string(forKey: globeStyleKey) ?? userDefaults.string(forKey: globeStyleKey),
           let style = GlobeStyle(rawValue: raw) {
            globeStyle = style
        }

        // Load map style (prefer iCloud, fall back to local)
        if let raw = iCloudStore.string(forKey: mapStyleKey) ?? userDefaults.string(forKey: mapStyleKey),
           let style = GlobeStyle(rawValue: raw) {
            mapStyle = style
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

        userDefaults.set(mapStyle.rawValue, forKey: mapStyleKey)
        iCloudStore.set(mapStyle.rawValue, forKey: mapStyleKey)

        userDefaults.set(isDarkMode, forKey: isDarkModeKey)
        iCloudStore.set(isDarkMode, forKey: isDarkModeKey)

        let citiesDict = checkedCities.mapValues { Array($0) }
        userDefaults.set(citiesDict, forKey: checkedCitiesKey)
        iCloudStore.set(citiesDict, forKey: checkedCitiesKey)

        let attractionsDict = checkedAttractions.mapValues { Array($0) }
        userDefaults.set(attractionsDict, forKey: checkedAttractionsKey)
        iCloudStore.set(attractionsDict, forKey: checkedAttractionsKey)

        iCloudStore.synchronize()
    }

    func setGlobeStyle(_ style: GlobeStyle) {
        globeStyle = style
        saveData()
    }

    func setMapStyle(_ style: GlobeStyle) {
        mapStyle = style
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

    func checkedCitiesForCountry(_ name: String) -> Set<String> {
        checkedCities[name] ?? []
    }

    func checkedAttractionsForCountry(_ name: String) -> Set<String> {
        checkedAttractions[name] ?? []
    }

    func toggleCheckedCity(_ city: String, for country: String) {
        var set = checkedCities[country] ?? []
        if set.contains(city) { set.remove(city) } else { set.insert(city) }
        checkedCities[country] = set.isEmpty ? nil : set
        saveData()
    }

    func toggleCheckedAttraction(_ attraction: String, for country: String) {
        var set = checkedAttractions[country] ?? []
        if set.contains(attraction) { set.remove(attraction) } else { set.insert(attraction) }
        checkedAttractions[country] = set.isEmpty ? nil : set
        saveData()
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
        checkedCities.removeAll()
        checkedAttractions.removeAll()
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

    private func mergeDictionaries(_ local: [String: [String]], _ cloud: [String: [String]]) -> [String: Set<String>] {
        var result: [String: Set<String>] = [:]
        for key in Set(local.keys).union(cloud.keys) {
            let merged = Set(local[key] ?? []).union(Set(cloud[key] ?? []))
            if !merged.isEmpty { result[key] = merged }
        }
        return result
    }

    private func flagEmoji(from countryCode: String) -> String {
        flagEmojiFromCode(countryCode)
    }
}

func flagEmojiFromCode(_ countryCode: String) -> String {
    let base: UInt32 = 127397
    var emoji = ""
    for scalar in countryCode.uppercased().unicodeScalars {
        if let scalar = UnicodeScalar(base + scalar.value) {
            emoji.append(Character(scalar))
        }
    }
    return emoji
}

#Preview {
    ContentView()
}

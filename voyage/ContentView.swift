import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var globeState = GlobeState()
    @State private var selectedTab = 0
    @State private var showDailyBadge = false
    @State private var showDailyToast = false
    @Namespace private var tabHighlightNamespace

    private static let badgeDateKey = "dailyBadgeNextDate"

    private struct TabBarItem {
        let title: String
        let icon: String
        let tag: Int
    }

    private static let tabBarItems: [TabBarItem] = [
        TabBarItem(title: "Home", icon: "globe", tag: 0),
        TabBarItem(title: "Daily", icon: "calendar", tag: 1),
        TabBarItem(title: "Challenges", icon: "gamecontroller.fill", tag: 2),
        TabBarItem(title: "Achievements", icon: "trophy.fill", tag: 3),
        TabBarItem(title: "Settings", icon: "gearshape.fill", tag: 4)
    ]

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(globeState: globeState)
                .toolbar(.hidden, for: .tabBar)
                .tag(0)

            ChallengeCalendarView(globeState: globeState)
                .toolbar(.hidden, for: .tabBar)
                .tag(1)

            ChallengesView(globeState: globeState)
                .toolbar(.hidden, for: .tabBar)
                .tag(2)

            AchievementsView(globeState: globeState)
                .toolbar(.hidden, for: .tabBar)
                .tag(3)

            SettingsView(globeState: globeState)
                .toolbar(.hidden, for: .tabBar)
                .tag(4)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            floatingTabBar
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
        .onAppear {
            let nextDate = UserDefaults.standard.object(forKey: Self.badgeDateKey) as? Date ?? .distantPast
            let shouldShow = Date() >= nextDate
            showDailyBadge = shouldShow
            if shouldShow {
                withAnimation(.easeOut(duration: 0.3).delay(0.5)) {
                    showDailyToast = true
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                    withAnimation(.easeIn(duration: 0.3)) {
                        showDailyToast = false
                    }
                }
            }
        }
        .onChange(of: selectedTab) {
            if selectedTab == 1 {
                showDailyBadge = false
                withAnimation { showDailyToast = false }
                let tomorrow = Calendar.current.startOfDay(for: Calendar.current.date(byAdding: .day, value: 1, to: Date())!)
                UserDefaults.standard.set(tomorrow, forKey: Self.badgeDateKey)
            }
        }
    }

    /// Custom floating tab bar: every item gets the same width, with spacing
    /// between items so the selection highlight never crowds its neighbors.
    @ViewBuilder
    private var floatingTabBar: some View {
        let bar = HStack(spacing: 4) {
            ForEach(Self.tabBarItems, id: \.tag) { item in
                tabButton(for: item)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)

        Group {
            if #available(iOS 26, *) {
                bar.glassEffect()
            } else {
                bar.background(
                    Capsule()
                        .fill(.ultraThinMaterial)
                        .shadow(color: .black.opacity(0.15), radius: 12, y: 4)
                )
            }
        }
        .frame(maxWidth: 520)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 12)
        .padding(.bottom, 4)
        .overlay(alignment: .top) {
            if showDailyToast {
                dailyToast
                    .offset(y: -52)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func tabButton(for item: TabBarItem) -> some View {
        let isSelected = selectedTab == item.tag
        return Button {
            guard selectedTab != item.tag else { return }
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                selectedTab = item.tag
            }
        } label: {
            VStack(spacing: 3) {
                Image(systemName: item.icon)
                    .font(.system(size: 19, weight: .medium))
                    .overlay(alignment: .topTrailing) {
                        if item.tag == 1 && showDailyBadge {
                            Circle()
                                .fill(.red)
                                .frame(width: 14, height: 14)
                                .overlay(
                                    Text("!")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.white)
                                )
                                .offset(x: 8, y: -6)
                        }
                    }
                Text(item.title)
                    .font(.system(size: 9, weight: .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .padding(.horizontal, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .foregroundStyle(isSelected ? Color.accentColor : Color.primary.opacity(0.85))
            .background {
                if isSelected {
                    Capsule()
                        .fill(Color.primary.opacity(0.12))
                        .matchedGeometryEffect(id: "selectedTab", in: tabHighlightNamespace)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var dailyToast: some View {
        Button {
            withAnimation { showDailyToast = false }
            selectedTab = 1
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "calendar.badge.exclamationmark")
                    .font(.system(size: 14, weight: .semibold))
                Text("New daily challenge available!")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(AppColors.buttonColor)
                    .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
            )
        }
        .fixedSize()
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
    /// A one-shot camera flight request, consumed by the globe coordinator.
    struct CameraTarget: Equatable {
        let lat: Double
        let lon: Double
        let distance: Float
    }

    @Published var selectedCountry: String?
    @Published var pendingCameraTarget: CameraTarget?
    /// Temporary per-country fill colors (challenge games); takes precedence
    /// over visited/wishlist coloring on the globe. Not persisted.
    @Published var countryHighlightColors: [String: UIColor] = [:]
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

    /// Countries renamed in the dataset (old → current official name). Saved
    /// user data — local or synced from devices running older app versions —
    /// still uses the old names, so it is migrated on load.
    static let renamedCountries = [
        "Turkey": "Türkiye",
        "Cape Verde": "Cabo Verde"
    ]

    static func migrateRenamedCountries(in names: Set<String>) -> Set<String> {
        Set(names.map { renamedCountries[$0] ?? $0 })
    }

    static func migrateRenamedCountries(inKeysOf dict: [String: Set<String>]) -> [String: Set<String>] {
        var result: [String: Set<String>] = [:]
        for (country, items) in dict {
            result[renamedCountries[country] ?? country, default: []].formUnion(items)
        }
        return result
    }

    private let iCloudStore = NSUbiquitousKeyValueStore.default
    private let userDefaults = UserDefaults.standard
    /// False for throwaway instances (e.g. challenge games): nothing is loaded
    /// from or saved to UserDefaults/iCloud.
    private let isPersistent: Bool
    private let visitedCountriesKey = "visitedCountries"
    private let wishlistCountriesKey = "wishlistCountries"
    private let globeStyleKey = "globeStyle"
    private let mapStyleKey = "mapStyle"
    private let isDarkModeKey = "isDarkMode"
    private let checkedCitiesKey = "checkedCities"
    private let checkedAttractionsKey = "checkedAttractions"

    init(inMemory: Bool = false) {
        isPersistent = !inMemory
        loadFlagCodes()

        guard isPersistent else { return }
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
        visitedCountries = Self.migrateRenamedCountries(in: localCountries.union(cloudCountries))

        // Load wishlist countries
        let localWishlist = Set(userDefaults.stringArray(forKey: wishlistCountriesKey) ?? [])
        let cloudWishlist = Set(iCloudStore.array(forKey: wishlistCountriesKey) as? [String] ?? [])
        wishlistCountries = Self.migrateRenamedCountries(in: localWishlist.union(cloudWishlist))

        // Load checked cities
        let localCities = userDefaults.dictionary(forKey: checkedCitiesKey) as? [String: [String]] ?? [:]
        let cloudCities = iCloudStore.dictionary(forKey: checkedCitiesKey) as? [String: [String]] ?? [:]
        let mergedCities = mergeDictionaries(localCities, cloudCities)
        checkedCities = Self.migrateRenamedCountries(inKeysOf: mergedCities)

        // Load checked attractions
        let localAttractions = userDefaults.dictionary(forKey: checkedAttractionsKey) as? [String: [String]] ?? [:]
        let cloudAttractions = iCloudStore.dictionary(forKey: checkedAttractionsKey) as? [String: [String]] ?? [:]
        let mergedAttractions = mergeDictionaries(localAttractions, cloudAttractions)
        checkedAttractions = Self.migrateRenamedCountries(inKeysOf: mergedAttractions)

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

        // Sync merged (and name-migrated) data back to both stores
        if visitedCountries != localCountries || visitedCountries != cloudCountries ||
           wishlistCountries != localWishlist || wishlistCountries != cloudWishlist ||
           checkedCities != mergedCities || checkedAttractions != mergedAttractions {
            saveData()
        }
    }

    private func saveData() {
        guard isPersistent else { return }
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

    func deselectCountry(resumeAutoRotation: Bool = true) {
        selectedCountry = nil
        targetCountryCenter = nil
        if resumeAutoRotation {
            isAutoRotating = true
        }
    }

    /// Sets or clears a temporary fill color for a country.
    func setCountryHighlight(_ color: UIColor?, for name: String) {
        countryHighlightColors[name] = color
    }

    /// Requests a one-shot camera flight to the given location.
    func flyTo(_ target: CameraTarget) {
        isAutoRotating = false
        pendingCameraTarget = target
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
        countryHighlightColors.removeAll()
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

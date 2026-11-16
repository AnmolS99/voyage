import SwiftUI
import UIKit

struct ContentView: View {
    @State private var globeState = GlobeState()
    @State private var selectedTab = 0
    @State private var showDailyBadge = false
    @State private var showDailyToast = false
    @Environment(\.colorScheme) private var colorScheme

    private static let badgeDateKey = "dailyBadgeNextDate"

    /// The tab selection, seeing re-selections too: tapping the tab you are
    /// already on sets the same value again, which `onChange` never reports.
    /// Home's root is the globe as it opened, so re-selecting Home turns it back
    /// there — on the globe only; the map has no launch pose to return to.
    private var tabSelection: Binding<Int> {
        Binding(
            get: { selectedTab },
            set: { tab in
                if tab == 0 && selectedTab == 0 && globeState.viewMode == .globe {
                    globeState.returnHome()
                }
                selectedTab = tab
            }
        )
    }

    var body: some View {
        TabView(selection: tabSelection) {
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

            ChallengesView(globeState: globeState)
                .tabItem {
                    Label("Challenges", systemImage: "gamecontroller.fill")
                }
                .tag(2)

            AchievementsView(globeState: globeState)
                .tabItem {
                    Label("Achievements", systemImage: "trophy.fill")
                }
                .tag(3)

            SettingsView(globeState: globeState)
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
                .tag(4)
        }
        .preferredColorScheme(globeState.colorScheme)
        .onChange(of: colorScheme, initial: true) { _, scheme in
            globeState.systemAppearanceChanged(isDark: scheme == .dark)
        }
        .task {
            // At the top of every minute, for the Daylight theme — on the
            // minute, as on Android, so it turns over when the clock reaches
            // the sunrise or sunset Settings names, not up to a minute after.
            while !Task.isCancelled {
                let now = Date().timeIntervalSince1970
                try? await Task.sleep(for: .seconds(60 - now.truncatingRemainder(dividingBy: 60)))
                globeState.refreshDaylight()
            }
        }
        .overlay { AchievementUnlockCelebration(globeState: globeState) }
        .overlay(alignment: .bottom) {
            if showDailyToast {
                Button {
                    withAnimation { showDailyToast = false }
                    selectedTab = 1
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.exclamationmark")
                            .font(.system(size: 14, weight: .semibold))
                        Text("New daily challenge available!")
                            .font(.voyageText(14, weight: .medium))
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
                // Clears Home's countries dock above the tab bar
                .padding(.bottom, 136)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
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
}

enum ViewMode {
    case globe
    case map
}

enum GlobeStyle: String, CaseIterable {
    case stylized
    case natural
    case realistic

    /// Bundled from `shared/data/textures/`, the same files Android reads.
    var textureName: String {
        switch self {
        case .stylized: return "stylized_earth_texture.jpg"
        case .natural: return "natural_earth_texture.jpg"
        case .realistic: return "earth_texture.jpg"
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

/// Appearance preference, matching Android's `ThemeMode`, in the order Settings
/// offers them.
///
/// `.daylight` is the default and follows the sun: light by day and dark by
/// night, whatever the system is set to — see `Daylight`. `.system` follows the
/// device's appearance; `.light` and `.dark` are what the old `isDarkMode`
/// boolean stored. The theme is chosen in Settings only.
enum ThemeMode: String, CaseIterable {
    case daylight
    case system
    case light
    case dark

    var displayName: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        case .daylight: return "Daylight"
        }
    }

    /// Whether to use the dark scheme, given what the system is currently set to
    /// and whether the sun is down.
    func isDark(systemIsDark: Bool, isNight: Bool) -> Bool {
        switch self {
        case .system: return systemIsDark
        case .light: return false
        case .dark: return true
        case .daylight: return isNight
        }
    }
}

/// `@Observable`, so a view is re-evaluated only when a property it reads
/// changes: a tap on the globe no longer wakes the four tabs that show no
/// selection (issue #161).
@Observable
final class GlobeState {
    /// A one-shot camera flight request, consumed by the globe coordinator.
    /// A nil distance keeps the camera at its current zoom while centering.
    struct CameraTarget: Equatable {
        let lat: Double
        let lon: Double
        let distance: Float?
    }

    var selectedCountry: String?
    var pendingCameraTarget: CameraTarget?
    /// Temporary per-country fill colors (challenge games); takes precedence
    /// over visited/wishlist coloring on the globe. Not persisted.
    var countryHighlightColors: [String: UIColor] = [:]
    /// Whether selecting a country shows its capital marker on the globe.
    /// Challenge games turn this off — the marker is irrelevant there and
    /// would hint at the answer. Not persisted.
    var showsCapitalMarker = true
    var visitedCountries: Set<String> = []
    var wishlistCountries: Set<String> = []
    var checkedCities: [String: Set<String>] = [:]
    var checkedAttractions: [String: Set<String>] = [:]
    /// The appearance actually on screen — `themeMode` resolved against the
    /// system's. Persistent instances derive it; in-memory ones (challenge games)
    /// copy it from the main state.
    var isDarkMode: Bool = false
    private(set) var themeMode: ThemeMode = .daylight
    /// Last known system appearance; only meaningful while `themeMode` is `.system`.
    @ObservationIgnored private var systemIsDark = GlobeState.currentSystemIsDark()
    var isAutoRotating: Bool = true
    var targetCountryCenter: (lat: Double, lon: Double)?
    var viewMode: ViewMode = .globe
    var globeStyle: GlobeStyle = .realistic
    var mapStyle: GlobeStyle = .realistic
    /// Whether the globe and the map shade terrain (`TerrainRelief`). One switch
    /// for both, so they never disagree; on unless turned off, as on Android.
    var showsTerrain: Bool = true
    let totalUNCountries = 195

    // Territories that are not UN member or observer states (excluded from progress count)
    static let nonUNTerritories: Set<String> = [
        "Antarctica",
        "Bermuda",
        "Falkland Islands",
        "Faroe Islands",
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
    private let showsTerrainKey = "showsTerrain"
    private let themeModeKey = "themeMode"
    /// Pre-`themeMode` boolean, read once to migrate an explicit light/dark choice.
    private let isDarkModeKey = "isDarkMode"
    private let checkedCitiesKey = "checkedCities"
    private let checkedAttractionsKey = "checkedAttractions"

    /// A throwaway globe for a challenge game: nothing saved, no idle spin and
    /// no capital seal, looking as `mainState`'s does.
    static func forGame(matching mainState: GlobeState) -> GlobeState {
        let globe = GlobeState(inMemory: true)
        globe.globeStyle = mainState.globeStyle
        globe.showsTerrain = mainState.showsTerrain
        globe.isDarkMode = mainState.isDarkMode
        globe.isAutoRotating = false
        globe.showsCapitalMarker = false
        return globe
    }

    init(inMemory: Bool = false) {
        isPersistent = !inMemory

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

    // MARK: - Persistence

    /// How a synced set was reconciled, and which stores need the result.
    struct SyncResolution<Value: Equatable>: Equatable {
        let value: Value
        let writesLocal: Bool
        let writesCloud: Bool
    }

    /// Reconciles one synced collection (visited, wishlist, checked cities or
    /// attractions) between UserDefaults and the iCloud key-value store.
    ///
    /// The cloud wins whenever it holds the key. A device writes both stores
    /// together, so they only differ when another device changed the cloud,
    /// and taking the cloud's value is what lets a removal or a reset on one
    /// device reach the others (a union could only add, issue #152). `cloud`
    /// is nil when iCloud has never had the key: then the local value stands
    /// and is pushed up, unless it is empty, so a fresh install whose iCloud
    /// data has not arrived yet cannot overwrite it with nothing.
    ///
    /// The one union is `isFirstSync`: the first time this device sees iCloud
    /// data, so a new device, or one that kept data before syncing, merges what
    /// it had instead of losing it.
    static func resolveSynced<Value: Collection & Equatable>(
        local: Value,
        cloud: Value?,
        isFirstSync: Bool,
        union: (Value, Value) -> Value,
        migrate: (Value) -> Value
    ) -> SyncResolution<Value> {
        let value: Value
        if let cloud {
            value = migrate(isFirstSync ? union(local, cloud) : cloud)
        } else {
            value = migrate(local)
        }
        return SyncResolution(
            value: value,
            writesLocal: value != local,
            writesCloud: cloud.map { value != $0 } ?? !value.isEmpty
        )
    }

    /// Stored `[country: [item]]` as sets, without countries left empty.
    static func setsByCountry(_ raw: [String: [String]]) -> [String: Set<String>] {
        raw.compactMapValues { $0.isEmpty ? nil : Set($0) }
    }

    static func unionByCountry(_ a: [String: Set<String>], _ b: [String: Set<String>]) -> [String: Set<String>] {
        a.merging(b) { $0.union($1) }
    }

    /// Set once this device has adopted iCloud's copy of the synced sets; until
    /// then the first iCloud data it sees is unioned with its own.
    private let hasSyncedKey = "hasSyncedWithICloud"

    private func loadData() {
        let isFirstSync = !userDefaults.bool(forKey: hasSyncedKey)
        var sawCloud = false

        func resolve<Value: Collection & Equatable>(
            local: Value, cloud: Value?,
            union: (Value, Value) -> Value, migrate: (Value) -> Value,
            save: (Value, _ local: Bool, _ cloud: Bool) -> Void
        ) -> Value {
            if cloud != nil { sawCloud = true }
            let r = Self.resolveSynced(local: local, cloud: cloud, isFirstSync: isFirstSync,
                                       union: union, migrate: migrate)
            if r.writesLocal || r.writesCloud { save(r.value, r.writesLocal, r.writesCloud) }
            return r.value
        }

        visitedCountries = resolve(
            local: Set(userDefaults.stringArray(forKey: visitedCountriesKey) ?? []),
            cloud: (iCloudStore.array(forKey: visitedCountriesKey) as? [String]).map { Set($0) },
            union: { $0.union($1) }, migrate: Self.migrateRenamedCountries(in:)
        ) { persist(Array($0), forKey: visitedCountriesKey, local: $1, cloud: $2) }

        wishlistCountries = resolve(
            local: Set(userDefaults.stringArray(forKey: wishlistCountriesKey) ?? []),
            cloud: (iCloudStore.array(forKey: wishlistCountriesKey) as? [String]).map { Set($0) },
            union: { $0.union($1) }, migrate: Self.migrateRenamedCountries(in:)
        ) { persist(Array($0), forKey: wishlistCountriesKey, local: $1, cloud: $2) }

        checkedCities = resolve(
            local: Self.setsByCountry(userDefaults.dictionary(forKey: checkedCitiesKey) as? [String: [String]] ?? [:]),
            cloud: (iCloudStore.dictionary(forKey: checkedCitiesKey) as? [String: [String]]).map(Self.setsByCountry),
            union: Self.unionByCountry, migrate: Self.migrateRenamedCountries(inKeysOf:)
        ) { persist($0.mapValues { Array($0) }, forKey: checkedCitiesKey, local: $1, cloud: $2) }

        checkedAttractions = resolve(
            local: Self.setsByCountry(userDefaults.dictionary(forKey: checkedAttractionsKey) as? [String: [String]] ?? [:]),
            cloud: (iCloudStore.dictionary(forKey: checkedAttractionsKey) as? [String: [String]]).map(Self.setsByCountry),
            union: Self.unionByCountry, migrate: Self.migrateRenamedCountries(inKeysOf:)
        ) { persist($0.mapValues { Array($0) }, forKey: checkedAttractionsKey, local: $1, cloud: $2) }

        if sawCloud && isFirstSync { userDefaults.set(true, forKey: hasSyncedKey) }

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

        // Load the Terrain setting (prefer iCloud, fall back to local); absent
        // before the setting existed, which reads as on
        if let shows = (iCloudStore.object(forKey: showsTerrainKey) ?? userDefaults.object(forKey: showsTerrainKey)) as? Bool {
            showsTerrain = shows
        }

        // Load theme (prefer iCloud, fall back to local); a saved legacy
        // boolean predates the system option and was always an explicit choice
        if let raw = iCloudStore.string(forKey: themeModeKey) ?? userDefaults.string(forKey: themeModeKey),
           let mode = ThemeMode(rawValue: raw) {
            themeMode = mode
        } else if userDefaults.object(forKey: isDarkModeKey) != nil || iCloudStore.object(forKey: isDarkModeKey) != nil {
            themeMode = iCloudStore.bool(forKey: isDarkModeKey) || userDefaults.bool(forKey: isDarkModeKey) ? .dark : .light
        }
        resolveDarkMode()
    }

    /// Writes one key to UserDefaults and/or iCloud. Each mutation writes only
    /// the keys it changed: rewriting every key would push this device's stale
    /// copy of the others over newer iCloud values. No `synchronize()` per
    /// write; the system schedules uploads (`init` calls it once at launch).
    private func persist(_ value: Any, forKey key: String, local: Bool = true, cloud: Bool = true) {
        guard isPersistent else { return }
        if local { userDefaults.set(value, forKey: key) }
        if cloud { iCloudStore.set(value, forKey: key) }
    }

    private func saveVisited() { persist(Array(visitedCountries), forKey: visitedCountriesKey) }
    private func saveWishlist() { persist(Array(wishlistCountries), forKey: wishlistCountriesKey) }
    private func saveCheckedCities() { persist(checkedCities.mapValues { Array($0) }, forKey: checkedCitiesKey) }
    private func saveCheckedAttractions() {
        persist(checkedAttractions.mapValues { Array($0) }, forKey: checkedAttractionsKey)
    }

    func setGlobeStyle(_ style: GlobeStyle) {
        globeStyle = style
        persist(style.rawValue, forKey: globeStyleKey)
    }

    func setMapStyle(_ style: GlobeStyle) {
        mapStyle = style
        persist(style.rawValue, forKey: mapStyleKey)
    }

    func setShowsTerrain(_ shows: Bool) {
        showsTerrain = shows
        persist(shows, forKey: showsTerrainKey)
    }

    func setThemeMode(_ mode: ThemeMode) {
        themeMode = mode
        if mode == .system {
            // While a mode was forced, the views' color scheme reported the
            // forced one, so re-read the device's own appearance.
            systemIsDark = Self.currentSystemIsDark()
        }
        resolveDarkMode()
        persist(mode.rawValue, forKey: themeModeKey)
    }

    /// For `.preferredColorScheme`: nil leaves the window on the system's appearance.
    var colorScheme: ColorScheme? {
        themeMode == .system ? nil : (isDarkMode ? .dark : .light)
    }

    /// Asks the sun again. `ContentView` calls this once a minute, which is what
    /// turns the Daylight theme over at sunrise and sunset.
    func refreshDaylight() {
        if themeMode == .daylight { resolveDarkMode() }
    }

    /// Reports the system appearance, as seen by a view whose color scheme is not
    /// forced. Ignored unless following the system: otherwise that scheme is the
    /// forced one, not the system's.
    func systemAppearanceChanged(isDark: Bool) {
        guard themeMode == .system else { return }
        systemIsDark = isDark
        resolveDarkMode()
    }

    private func resolveDarkMode() {
        let dark = themeMode.isDark(systemIsDark: systemIsDark, isNight: themeMode == .daylight && Daylight.isNight())
        if isDarkMode != dark { isDarkMode = dark }
    }

    /// The device's appearance, unaffected by any window's override.
    private static func currentSystemIsDark() -> Bool {
        let screen = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.screen }
            .first
        return (screen?.traitCollection ?? UITraitCollection.current).userInterfaceStyle == .dark
    }

    @objc private func iCloudDidChange(_ notification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.loadData()
        }
    }

    func selectCountry(_ name: String, center: (lat: Double, lon: Double)? = nil) {
        selectedCountry = name
        isAutoRotating = false
        targetCountryCenter = center
    }

    func addVisit(_ name: String) {
        visitedCountries.insert(name)
        saveVisited()
    }

    func removeVisit(_ name: String) {
        visitedCountries.remove(name)
        saveVisited()
    }

    func isVisited(_ name: String) -> Bool {
        visitedCountries.contains(name)
    }

    func addToWishlist(_ name: String) {
        wishlistCountries.insert(name)
        saveWishlist()
    }

    func removeFromWishlist(_ name: String) {
        wishlistCountries.remove(name)
        saveWishlist()
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

    // Checking a city or attraction marks its country visited — you've been
    // there. Unchecking leaves the visit alone: only the Visited button removes one.
    func toggleCheckedCity(_ city: String, for country: String) {
        var set = checkedCities[country] ?? []
        if set.contains(city) {
            set.remove(city)
        } else {
            set.insert(city)
            visitedCountries.insert(country)
        }
        checkedCities[country] = set.isEmpty ? nil : set
        saveCheckedCities()
        saveVisited()
    }

    func toggleCheckedAttraction(_ attraction: String, for country: String) {
        var set = checkedAttractions[country] ?? []
        if set.contains(attraction) {
            set.remove(attraction)
        } else {
            set.insert(attraction)
            visitedCountries.insert(country)
        }
        checkedAttractions[country] = set.isEmpty ? nil : set
        saveCheckedAttractions()
        saveVisited()
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

    /// Bumped each time Home's tab is re-selected while the globe is showing;
    /// the globe answers each new value by turning to where the user is.
    /// Not persisted. Android `VoyageState.homeRequest`.
    private(set) var homeRequest = 0

    /// Sends the globe home: deselects, so the country's card closes, and asks
    /// the globe to fly to the user's time zone's city (`Daylight.location`), or
    /// its launch pose if the zone has none. The spin stays off for the
    /// flight — an `SCNAction` and an animated transaction would fight over the
    /// same rotation — and the globe turns it back on when it lands, unless a
    /// drag or a selection has taken over by then. Android `VoyageState.returnHome`.
    func returnHome() {
        deselectCountry(resumeAutoRotation: false)
        isAutoRotating = false
        homeRequest += 1
    }

    func resetSelection() {
        selectedCountry = nil
        targetCountryCenter = nil
        isAutoRotating = true
    }

    func resetAllData() {
        selectedCountry = nil
        visitedCountries.removeAll()
        wishlistCountries.removeAll()
        checkedCities.removeAll()
        checkedAttractions.removeAll()
        countryHighlightColors.removeAll()
        targetCountryCenter = nil
        isAutoRotating = true
        saveVisited()
        saveWishlist()
        saveCheckedCities()
        saveCheckedAttractions()
    }

    // Get flag emoji for a country
    func flagForCountry(_ name: String) -> String {
        if let code = CountryDataCache.shared.countryNamed(name)?.flagCode {
            return flagEmojiFromCode(code)
        }
        return "🌍" // Generic globe emoji as fallback
    }

    /// "La Paz, Sucre" — every capital of the country, the main one first.
    func capitalsForCountry(_ name: String) -> String? {
        CountryDataCache.shared.countryNamed(name)?.capitalsLine
    }

    /// Whether the country counts toward the UN-country progress total.
    func countsTowardProgress(_ name: String) -> Bool {
        !Self.nonUNTerritories.contains(name)
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

import SwiftUI
import StoreKit

struct SettingsView: View {
    @ObservedObject var globeState: GlobeState
    @State private var showingResetConfirmation = false
    @StateObject private var tipJarManager = TipJarManager()
    /// The icon on the Home Screen. iOS remembers it, so it is read back from
    /// the system rather than kept in `GlobeState`.
    @State private var appIcon = ThemeMode.currentAppIcon

    private func setAppIcon(_ mode: ThemeMode) {
        appIcon = mode
        Task {
            do {
                try await UIApplication.shared.setAlternateIconName(mode.alternateIconName)
            } catch {
                appIcon = .currentAppIcon
            }
        }
    }

    /// "1.10.2 (123)": the marketing version and the build number, which CI sets
    /// to the TestFlight run number, so testers can say exactly which build they have.
    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        guard let build = info?["CFBundleVersion"] as? String else { return version }
        return "\(version) (\(build))"
    }

    /// The policy the marketing site renders from `PRIVACY.md` (see web/README.md).
    private static let privacyPolicyURL = URL(string: "https://voyage.anmol-singh.workers.dev/privacy")!

    private var thankYouMessage: String {
        switch tipJarManager.lastPurchasedProductId {
        case "com.anmol.voyage.tip.small":
            return "🍌 Thanks, bananas are useful for code monkeys like me!"
        case "com.anmol.voyage.tip.medium":
            return "🍫 Mmm... you just made my day sweeter. Thank you!"
        case "com.anmol.voyage.tip.large":
            return "☕ Much needed caffeine! Thanks to you, I'll be coding all night. You're amazing!"
        default:
            return "Your support means a lot! Thank you for helping make voyage better."
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Image(systemName: "circle.lefthalf.filled")
                                .foregroundColor(AppColors.buttonColor)

                            Text("Theme")
                        }

                        HStack(spacing: 8) {
                            ForEach(ThemeMode.allCases, id: \.self) { mode in
                                ThemeOption(mode: mode, isSelected: globeState.themeMode == mode) {
                                    globeState.setThemeMode(mode)
                                }
                            }
                        }

                        Text(globeState.themeMode.summary)
                            .font(.voyageText(13))
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))

                    if UIApplication.shared.supportsAlternateIcons {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: "app")
                                    .foregroundColor(AppColors.buttonColor)

                                Text("App Icon")
                            }

                            HStack(spacing: 0) {
                                ForEach(ThemeMode.appIcons, id: \.self) { mode in
                                    AppIconOption(mode: mode, isSelected: appIcon == mode) {
                                        setAppIcon(mode)
                                    }
                                    .frame(maxWidth: .infinity)
                                }
                            }

                            Text("“System” follows your Home Screen icon setting.")
                                .font(.voyageText(13))
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 4)
                        .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                    }

                    HStack {
                        Image(systemName: "globe.americas")
                            .foregroundColor(AppColors.buttonColor)

                        Text("Globe Style")

                        Spacer()

                        Picker("", selection: $globeState.globeStyle) {
                            ForEach(GlobeStyle.allCases, id: \.self) { style in
                                Text(style.displayName).tag(style)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))

                    HStack {
                        Image(systemName: "map")
                            .foregroundColor(AppColors.buttonColor)

                        Text("Map Style")

                        Spacer()

                        Picker("", selection: $globeState.mapStyle) {
                            ForEach(GlobeStyle.allCases, id: \.self) { style in
                                Text(style.displayName).tag(style)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))

                    // One switch for the globe and the map: shading one and not the
                    // other would break their consistency
                    Toggle(isOn: Binding(
                        get: { globeState.showsTerrain },
                        set: { globeState.setShowsTerrain($0) }
                    )) {
                        HStack {
                            Image(systemName: "mountain.2")
                                .foregroundColor(AppColors.buttonColor)
                            Text("Terrain")
                        }
                    }
                    .tint(AppColors.buttonColor)
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                } header: {
                    Text("Appearance")
                        .font(.voyageText(17, weight: .semibold))
                }

                Section {
                    if tipJarManager.isLoading {
                        HStack {
                            ProgressView()
                                .padding(.trailing, 8)
                            Text("Loading tips...")
                                .foregroundColor(.secondary)
                        }
                        .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                    } else if tipJarManager.useFallback {
                        ForEach(TipJarManager.fallbackTips) { tip in
                            FallbackTipRowView(tip: tip)
                                .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                        }
                    } else {
                        ForEach(tipJarManager.tips, id: \.id) { tip in
                            TipRowView(
                                tip: tip,
                                purchaseState: tipJarManager.purchaseState,
                                onPurchase: {
                                    Task {
                                        await tipJarManager.purchase(tip)
                                    }
                                }
                            )
                            .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                        }
                    }
                } header: {
                    Text("Support")
                        .font(.voyageText(17, weight: .semibold))
                } footer: {
                    if tipJarManager.useFallback {
                        Text("Tips unavailable in this environment. In the App Store version, you can leave a tip to support development.")
                            .font(.voyageText(13))
                    } else {
                        Text("Thanks for using voyage! If you enjoy the app, consider leaving a tip to support development.")
                            .font(.voyageText(13))
                    }
                }

                Section {
                    Button(role: .destructive) {
                        showingResetConfirmation = true
                    } label: {
                        HStack {
                            Image(systemName: "trash")
                                .foregroundColor(.red)
                            Text("Reset All Data")
                                .foregroundColor(.red)
                        }
                    }
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                } header: {
                    Text("Data")
                        .font(.voyageText(17, weight: .semibold))
                } footer: {
                    Text("This will clear all visited countries, wishlist and challenge statistics.")
                        .font(.voyageText(13))
                }

                Section {
                    Link(destination: Self.privacyPolicyURL) {
                        HStack {
                            Text("Privacy Policy")
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .foregroundColor(.secondary)
                        }
                    }
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))

                    HStack {
                        Text("Version")
                        Spacer()
                        Text(appVersion)
                            .foregroundColor(.secondary)
                    }
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                } footer: {
                    Text("© 2026 Anmol Singh. All rights reserved.")
                        .font(.voyageText(13))
                        .frame(maxWidth: .infinity)
                        .padding(.top, 16)
                }
            }
            .onChange(of: globeState.globeStyle) { _, newStyle in
                globeState.setGlobeStyle(newStyle)
            }
            .onChange(of: globeState.mapStyle) { _, newStyle in
                globeState.setMapStyle(newStyle)
            }
            .scrollContentBackground(.hidden)
            .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            .font(.voyageText(17))
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .confirmationDialog(
                "Reset All Data",
                isPresented: $showingResetConfirmation,
                titleVisibility: .visible
            ) {
                Button("Reset", role: .destructive) {
                    globeState.resetAllData()
                    ChallengeStatsStore.shared.resetAll()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to reset all data? This will clear all visited countries, wishlist and challenge statistics. It cannot be undone.")
            }
            .alert("Thank You!", isPresented: .init(
                get: { tipJarManager.purchaseState == .purchased },
                set: { if !$0 { tipJarManager.resetState() } }
            )) {
                Button("You're welcome!") {
                    tipJarManager.resetState()
                }
            } message: {
                Text(thankYouMessage)
            }
        }
    }
}

/// One choice in the Theme picker: Home in miniature in that theme with its name
/// below, ringed in the app's orange when chosen — Android's `ThemeOption`.
private struct ThemeOption: View {
    let mode: ThemeMode
    let isSelected: Bool
    let action: () -> Void

    private static let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ThemePreview(mode: mode)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .clipShape(Self.shape)
                    .overlay(Self.shape.stroke(Color.primary.opacity(0.12), lineWidth: 1))
                    .padding(4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(AppColors.buttonColor, lineWidth: 2)
                            .opacity(isSelected ? 1 : 0)
                    )

                Text(mode.displayName)
                    .font(.voyageText(13, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? .primary : .secondary)
                    .lineLimit(1)
            }
        }
        // Borderless, so a tap on one option doesn't fire the whole list row
        .buttonStyle(.borderless)
        .accessibilityLabel(mode.displayName)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Home in miniature: the globe rising into the day's warm sky or the night's
/// starry one — Android's `ThemePreview`, same proportions. System and Daylight
/// are day on the left and night on the right, so the globe carries a
/// terminator; Daylight adds the sun and the moon it follows.
private struct ThemePreview: View {
    let mode: ThemeMode

    /// Stars, as fractions of the preview.
    private static let stars: [CGPoint] = [
        CGPoint(x: 0.08, y: 0.22), CGPoint(x: 0.20, y: 0.50), CGPoint(x: 0.32, y: 0.14),
        CGPoint(x: 0.45, y: 0.36), CGPoint(x: 0.58, y: 0.16), CGPoint(x: 0.66, y: 0.44),
        CGPoint(x: 0.90, y: 0.14), CGPoint(x: 0.93, y: 0.52),
    ]
    /// Land as the app icon shows it — Africa under Europe and Asia — as
    /// outlines in fractions of the globe's bounding square.
    private static let land: [[CGPoint]] = [
        // Europe and Asia, from Iberia round Scandinavia to India and back by Arabia
        [
            CGPoint(x: 0.34, y: 0.17), CGPoint(x: 0.35, y: 0.14), CGPoint(x: 0.39, y: 0.135), CGPoint(x: 0.39, y: 0.115),
            CGPoint(x: 0.43, y: 0.10), CGPoint(x: 0.455, y: 0.065), CGPoint(x: 0.45, y: 0.045), CGPoint(x: 0.48, y: -0.01),
            CGPoint(x: 0.52, y: -0.02), CGPoint(x: 0.56, y: 0.01), CGPoint(x: 0.60, y: -0.01), CGPoint(x: 0.68, y: -0.04),
            CGPoint(x: 0.98, y: 0.12), CGPoint(x: 1.07, y: 0.31), CGPoint(x: 0.98, y: 0.30), CGPoint(x: 0.95, y: 0.39),
            CGPoint(x: 0.87, y: 0.31), CGPoint(x: 0.82, y: 0.28), CGPoint(x: 0.79, y: 0.29), CGPoint(x: 0.76, y: 0.315),
            CGPoint(x: 0.725, y: 0.33), CGPoint(x: 0.70, y: 0.325), CGPoint(x: 0.64, y: 0.26), CGPoint(x: 0.60, y: 0.24),
            CGPoint(x: 0.61, y: 0.19), CGPoint(x: 0.56, y: 0.175), CGPoint(x: 0.545, y: 0.155), CGPoint(x: 0.53, y: 0.175),
            CGPoint(x: 0.51, y: 0.155), CGPoint(x: 0.47, y: 0.13), CGPoint(x: 0.50, y: 0.16), CGPoint(x: 0.485, y: 0.17),
            CGPoint(x: 0.45, y: 0.13), CGPoint(x: 0.41, y: 0.14), CGPoint(x: 0.39, y: 0.165), CGPoint(x: 0.36, y: 0.18),
        ],
        [CGPoint(x: 0.375, y: 0.095), CGPoint(x: 0.405, y: 0.09), CGPoint(x: 0.40, y: 0.05), CGPoint(x: 0.375, y: 0.06)], // Britain
        [CGPoint(x: 0.30, y: 0.22), CGPoint(x: 0.46, y: 0.20), CGPoint(x: 0.58, y: 0.22), CGPoint(x: 0.66, y: 0.34), CGPoint(x: 0.74, y: 0.36), CGPoint(x: 0.64, y: 0.44), CGPoint(x: 0.62, y: 0.56), CGPoint(x: 0.56, y: 0.66), CGPoint(x: 0.50, y: 0.66), CGPoint(x: 0.46, y: 0.52), CGPoint(x: 0.44, y: 0.42), CGPoint(x: 0.34, y: 0.42), CGPoint(x: 0.26, y: 0.36), CGPoint(x: 0.24, y: 0.28)], // Africa
        [CGPoint(x: 0.04, y: 0.40), CGPoint(x: 0.14, y: 0.44), CGPoint(x: 0.16, y: 0.56), CGPoint(x: 0.06, y: 0.60)], // South America's shoulder
    ]

    var body: some View {
        Canvas { context, size in
            let full = CGRect(origin: .zero, size: size)
            let rightHalf = full.divided(atDistance: size.width / 2, from: .minXEdge).remainder
            if mode != .dark { draw(isDark: false, in: full, of: size, context: context) }
            if mode != .light { draw(isDark: true, in: mode == .dark ? full : rightHalf, of: size, context: context) }
        }
    }

    /// The whole scene in one theme, clipped to `region` of it.
    private func draw(isDark: Bool, in region: CGRect, of size: CGSize, context: GraphicsContext) {
        var context = context
        context.clip(to: Path(region))

        if isDark {
            context.fill(Path(region), with: .color(.black))
            for star in Self.stars {
                let center = CGPoint(x: star.x * size.width, y: star.y * size.height)
                context.fill(Path(ellipseIn: CGRect(center: center, radius: 0.7)), with: .color(.white.opacity(0.8)))
            }
        } else {
            context.fill(Path(region), with: .linearGradient(
                Gradient(colors: [AppColors.backgroundLightTop, AppColors.backgroundLightBottom]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)
            ))
        }

        if mode == .daylight {
            let radius = size.height * 0.11
            let center = CGPoint(x: size.width * (isDark ? 0.78 : 0.22), y: size.height * 0.3)
            context.fill(Path(ellipseIn: CGRect(center: center, radius: radius)),
                         with: .color(isDark ? Color(AppColors.capitalMarkerInsetUI) : AppColors.buttonColor))
            if isDark {
                // The sky again, offset, leaves a crescent
                let bite = CGPoint(x: center.x + radius * 0.5, y: center.y - radius * 0.3)
                context.fill(Path(ellipseIn: CGRect(center: bite, radius: radius)), with: .color(.black))
            }
        }

        let globe = CGRect(center: CGPoint(x: size.width / 2, y: size.height * 0.42 + size.width * 0.38), radius: size.width * 0.38)
        context.fill(Path(ellipseIn: globe), with: .color(isDark ? AppColors.oceanDark : AppColors.ocean))
        if isDark {
            // A rim of daylight, so the dark ocean stands off the black sky
            context.stroke(Path(ellipseIn: globe), with: .color(AppColors.ocean), lineWidth: 1.5)
        }
        context.clip(to: Path(ellipseIn: globe))
        var land = Path()
        for outline in Self.land {
            land.addLines(outline.map { CGPoint(x: globe.minX + $0.x * globe.width, y: globe.minY + $0.y * globe.height) })
            land.closeSubpath()
        }
        context.fill(land, with: .color(AppColors.land))
        // Stroked too, with round joins, to soften the corners
        context.stroke(land, with: .color(AppColors.land), style: StrokeStyle(lineWidth: 0.75, lineJoin: .round))
    }
}

private extension CGRect {
    /// The square around a circle.
    init(center: CGPoint, radius: CGFloat) {
        self.init(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
    }
}

extension ThemeMode {
    /// What the theme does, under the picker. Daylight says when it turns over
    /// today and whose sun that is — the device's time zone and its city are all
    /// the app knows of where the user is.
    var summary: String {
        switch self {
        case .system: return "Matches your device’s appearance."
        case .light: return "Always light."
        case .dark: return "Always dark, with stars behind the globe."
        case .daylight:
            let times = Daylight.sunTimes()
            let place = Daylight.cityName().map { "the sun in \($0)" } ?? "the clock"
            guard let sunrise = times.sunrise, let sunset = times.sunset else { return "Follows \(place)." }
            func clock(_ time: Date) -> String { time.formatted(date: .omitted, time: .shortened) }
            return "Light from \(clock(sunrise)) and dark from \(clock(sunset)), following \(place)."
        }
    }
}

/// The app icon picker reuses three of the theme's choices — an icon has no
/// daylight option — as `appIcons`: `.system` is the
/// primary `AppIcon`, whose dark appearance iOS shows when Home Screen icons
/// are set to Dark, and light and dark are alternate icons that hold one
/// artwork in every appearance (`ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`).
extension ThemeMode {
    static let appIcons: [ThemeMode] = [.system, .light, .dark]

    /// `nil` is the primary icon.
    var alternateIconName: String? {
        switch self {
        case .system, .daylight: return nil
        case .light: return "AppIcon-Light"
        case .dark: return "AppIcon-Dark"
        }
    }

    @MainActor static var currentAppIcon: ThemeMode {
        appIcons.first { $0.alternateIconName == UIApplication.shared.alternateIconName } ?? .system
    }
}

/// One choice in the App Icon picker: a preview of the icon with its name
/// below, ringed in the app's orange when chosen. System shows the light icon
/// and the dark one split down the middle, like the theme's half-filled circle.
private struct AppIconOption: View {
    let mode: ThemeMode
    let isSelected: Bool
    let action: () -> Void

    private static let size: CGFloat = 60
    /// The Home Screen's icon corner radius at this size.
    private static let shape = RoundedRectangle(cornerRadius: size * 0.225, style: .continuous)

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                preview
                    .frame(width: Self.size, height: Self.size)
                    .clipShape(Self.shape)
                    .padding(4)
                    .overlay(
                        RoundedRectangle(cornerRadius: Self.size * 0.225 + 4, style: .continuous)
                            .stroke(AppColors.buttonColor, lineWidth: 2)
                            .opacity(isSelected ? 1 : 0)
                    )

                Text(mode.displayName)
                    .font(.voyageText(13, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? .primary : .secondary)
            }
        }
        // Borderless, so a tap on one option doesn't fire the whole list row
        .buttonStyle(.borderless)
        .accessibilityLabel(mode.displayName)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    @ViewBuilder private var preview: some View {
        switch mode {
        case .light:
            Image("AppIconPreviewLight").resizable()
        case .dark:
            Image("AppIconPreviewDark").resizable()
        case .system, .daylight:
            Image("AppIconPreviewLight").resizable()
                .overlay(
                    Image("AppIconPreviewDark").resizable()
                        .mask(HStack(spacing: 0) { Color.clear; Color.black })
                )
        }
    }
}

struct TipRowView: View {
    let tip: Product
    let purchaseState: TipJarManager.PurchaseState
    let onPurchase: () -> Void

    private var emoji: String {
        switch tip.id {
        case "com.anmol.voyage.tip.small":
            return "🍌"
        case "com.anmol.voyage.tip.medium":
            return "🍫"
        case "com.anmol.voyage.tip.large":
            return "☕"
        default:
            return "🍌"
        }
    }

    var body: some View {
        Button(action: onPurchase) {
            HStack {
                Text(emoji)
                    .font(.title2)
                    .frame(width: 28, height: 28)

                VStack(alignment: .leading, spacing: 2) {
                    Text(tip.displayName)
                        .foregroundColor(.primary)
                    Text(tip.description)
                        .font(.voyageText(12))
                        .foregroundColor(.secondary)
                }

                Spacer()

                if case .purchasing = purchaseState {
                    ProgressView()
                        .frame(width: 60)
                } else {
                    Text(tip.displayPrice)
                        .font(.voyageText(15, weight: .semibold))
                        .foregroundColor(.accentColor)
                        .frame(minWidth: 60)
                }
            }
        }
        .disabled(purchaseState == .purchasing)
    }
}

struct FallbackTipRowView: View {
    let tip: FallbackTip

    private var emoji: String {
        switch tip.id {
        case "com.anmol.voyage.tip.small":
            return "🍌"
        case "com.anmol.voyage.tip.medium":
            return "🍫"
        case "com.anmol.voyage.tip.large":
            return "☕"
        default:
            return "🍌"
        }
    }

    var body: some View {
        HStack {
            Text(emoji)
                .font(.title2)
                .frame(width: 28, height: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(tip.displayName)
                    .foregroundColor(.primary)
                Text(tip.description)
                    .font(.voyageText(12))
                    .foregroundColor(.secondary)
            }

            Spacer()

            Text(tip.displayPrice)
                .font(.voyageText(15, weight: .semibold))
                .foregroundColor(.secondary)
                .frame(minWidth: 60)
        }
    }
}

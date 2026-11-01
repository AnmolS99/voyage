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

                        Picker("Theme", selection: Binding(
                            get: { globeState.themeMode },
                            set: { globeState.setThemeMode($0) }
                        )) {
                            ForEach(ThemeMode.allCases, id: \.self) { mode in
                                Text(mode.displayName).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .labelsHidden()
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
                                ForEach(ThemeMode.allCases, id: \.self) { mode in
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

/// The app icon picker reuses the theme's three choices: `.system` is the
/// primary `AppIcon`, whose dark appearance iOS shows when Home Screen icons
/// are set to Dark, and light and dark are alternate icons that hold one
/// artwork in every appearance (`ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`).
extension ThemeMode {
    /// `nil` is the primary icon.
    var alternateIconName: String? {
        switch self {
        case .system: return nil
        case .light: return "AppIcon-Light"
        case .dark: return "AppIcon-Dark"
        }
    }

    @MainActor static var currentAppIcon: ThemeMode {
        allCases.first { $0.alternateIconName == UIApplication.shared.alternateIconName } ?? .system
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
        case .system:
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

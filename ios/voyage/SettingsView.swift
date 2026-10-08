import SwiftUI
import StoreKit

struct SettingsView: View {
    @ObservedObject var globeState: GlobeState
    @State private var showingResetConfirmation = false
    @StateObject private var tipJarManager = TipJarManager()

    /// "1.10.2 (123)": the marketing version and the build number, which CI sets
    /// to the TestFlight run number, so testers can say exactly which build they have.
    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        guard let build = info?["CFBundleVersion"] as? String else { return version }
        return "\(version) (\(build))"
    }

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
                } header: {
                    Text("Appearance")
                } footer: {
                    Text("Choose the app's theme and the texture styles for the globe and map views.")
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
                } footer: {
                    if tipJarManager.useFallback {
                        Text("Tips unavailable in this environment. In the App Store version, you can leave a tip to support development.")
                    } else {
                        Text("Thanks for using voyage! If you enjoy the app, consider leaving a tip to support development.")
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
                } footer: {
                    Text("This will clear all visited countries, wishlist and challenge statistics.")
                }

                Section {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(appVersion)
                            .foregroundColor(.secondary)
                    }
                    .listRowBackground(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                } footer: {
                    Text("© 2026 Anmol Singh. All rights reserved.")
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
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                if case .purchasing = purchaseState {
                    ProgressView()
                        .frame(width: 60)
                } else {
                    Text(tip.displayPrice)
                        .font(.subheadline)
                        .fontWeight(.semibold)
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
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Text(tip.displayPrice)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.secondary)
                .frame(minWidth: 60)
        }
    }
}

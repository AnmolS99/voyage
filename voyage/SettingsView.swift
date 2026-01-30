import SwiftUI
import StoreKit

struct SettingsView: View {
    @ObservedObject var globeState: GlobeState
    @Environment(\.dismiss) private var dismiss
    @State private var showingResetConfirmation = false
    @StateObject private var tipJarManager = TipJarManager()

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
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
                } header: {
                    Text("Data")
                } footer: {
                    Text("This will clear all visited countries and wishlist.")
                }

                Section {
                    if tipJarManager.isLoading {
                        HStack {
                            ProgressView()
                                .padding(.trailing, 8)
                            Text("Loading tips...")
                                .foregroundColor(.secondary)
                        }
                    } else if tipJarManager.useFallback {
                        ForEach(TipJarManager.fallbackTips) { tip in
                            FallbackTipRowView(tip: tip)
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
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(appVersion)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .confirmationDialog(
                "Reset All Data",
                isPresented: $showingResetConfirmation,
                titleVisibility: .visible
            ) {
                Button("Reset", role: .destructive) {
                    globeState.resetAllData()
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to reset all data? This will clear all visited countries and cannot be undone.")
            }
            .alert("Thank You!", isPresented: .init(
                get: { tipJarManager.purchaseState == .purchased },
                set: { if !$0 { tipJarManager.resetState() } }
            )) {
                Button("OK") {
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

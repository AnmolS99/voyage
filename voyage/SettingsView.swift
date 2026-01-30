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

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if tipJarManager.tips.isEmpty {
                        HStack {
                            ProgressView()
                                .padding(.trailing, 8)
                            Text("Loading tips...")
                                .foregroundColor(.secondary)
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
                    Text("Thanks for using voyage! If you enjoy the app, consider leaving a tip to support development.")
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
                } header: {
                    Text("Data")
                } footer: {
                    Text("This will clear all visited countries and selections.")
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
                Text("Your support means a lot! Thank you for helping make voyage better.")
            }
        }
    }
}

struct TipRowView: View {
    let tip: Product
    let purchaseState: TipJarManager.PurchaseState
    let onPurchase: () -> Void

    private var iconName: String {
        switch tip.id {
        case "com.anmol.voyage.tip.small":
            return "cup.and.saucer"
        case "com.anmol.voyage.tip.medium":
            return "cup.and.saucer.fill"
        case "com.anmol.voyage.tip.large":
            return "heart.fill"
        default:
            return "cup.and.saucer"
        }
    }

    private var iconColor: Color {
        switch tip.id {
        case "com.anmol.voyage.tip.small":
            return Color(red: 0.37, green: 0.5, blue: 1.0)
        case "com.anmol.voyage.tip.medium":
            return Color(red: 0.85, green: 0.55, blue: 0.35)
        case "com.anmol.voyage.tip.large":
            return Color(red: 0.85, green: 0.35, blue: 0.35)
        default:
            return Color(red: 0.37, green: 0.5, blue: 1.0)
        }
    }

    var body: some View {
        Button(action: onPurchase) {
            HStack {
                Image(systemName: iconName)
                    .foregroundColor(.white)
                    .frame(width: 28, height: 28)
                    .background(iconColor)
                    .clipShape(RoundedRectangle(cornerRadius: 6))

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

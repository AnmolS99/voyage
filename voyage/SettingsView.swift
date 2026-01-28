import SwiftUI

struct SettingsView: View {
    @ObservedObject var globeState: GlobeState
    @Environment(\.dismiss) private var dismiss
    @State private var showingResetConfirmation = false

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
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
                    Text("This will clear all visited countries and selections.")
                }

                Section {
                    Link(destination: URL(string: "https://buymeacoffee.com/anmols99")!) {
                        HStack {
                            Image(systemName: "cup.and.saucer.fill")
                                .foregroundColor(.white)
                                .frame(width: 28, height: 28)
                                .background(Color(red: 0.37, green: 0.5, blue: 1.0))
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                            Text("Buy Me a Coffee")
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.footnote)
                                .foregroundColor(.secondary)
                        }
                    }
                } header: {
                    Text("Support")
                } footer: {
                    Text("Thanks for using voyage! If you enjoy the app, consider buying me a coffee.")
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
        }
    }
}

import StoreKit

@MainActor
class TipJarManager: ObservableObject {
    @Published private(set) var tips: [Product] = []
    @Published private(set) var purchaseState: PurchaseState = .ready

    enum PurchaseState: Equatable {
        case ready
        case purchasing
        case purchased
        case failed(String)
    }

    static let tipProductIdentifiers: [String] = [
        "com.anmol.voyage.tip.small",
        "com.anmol.voyage.tip.medium",
        "com.anmol.voyage.tip.large"
    ]

    init() {
        Task {
            await loadProducts()
        }
    }

    func loadProducts() async {
        do {
            let products = try await Product.products(for: Self.tipProductIdentifiers)
            tips = products.sorted { $0.price < $1.price }
        } catch {
            print("Failed to load products: \(error)")
        }
    }

    func purchase(_ product: Product) async {
        purchaseState = .purchasing

        do {
            let result = try await product.purchase()

            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    await transaction.finish()
                    purchaseState = .purchased

                    // Reset to ready after a delay so user can tip again
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    purchaseState = .ready

                case .unverified(_, let error):
                    purchaseState = .failed("Verification failed: \(error.localizedDescription)")
                }

            case .userCancelled:
                purchaseState = .ready

            case .pending:
                purchaseState = .ready

            @unknown default:
                purchaseState = .ready
            }
        } catch {
            purchaseState = .failed(error.localizedDescription)
        }
    }

    func resetState() {
        purchaseState = .ready
    }
}

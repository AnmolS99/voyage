import StoreKit

struct FallbackTip: Identifiable {
    let id: String
    let displayName: String
    let description: String
    let displayPrice: String
}

@MainActor
class TipJarManager: ObservableObject {
    @Published private(set) var tips: [Product] = []
    @Published private(set) var purchaseState: PurchaseState = .ready
    @Published private(set) var isLoading = true
    @Published private(set) var useFallback = false
    @Published private(set) var lastPurchasedProductId: String?

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

    static let fallbackTips: [FallbackTip] = [
        FallbackTip(id: "com.anmol.voyage.tip.small", displayName: "Small Tip", description: "Buy me a banana", displayPrice: "$0.99"),
        FallbackTip(id: "com.anmol.voyage.tip.medium", displayName: "Medium Tip", description: "Buy me a chocolate bar", displayPrice: "$2.99"),
        FallbackTip(id: "com.anmol.voyage.tip.large", displayName: "Large Tip", description: "Buy me a coffee", displayPrice: "$4.99")
    ]

    init() {
        Task {
            await loadProducts()
        }
    }

    func loadProducts() async {
        isLoading = true
        do {
            let products = try await Product.products(for: Self.tipProductIdentifiers)
            tips = products.sorted { $0.price < $1.price }
            useFallback = products.isEmpty
        } catch {
            print("Failed to load products: \(error)")
            useFallback = true
        }
        isLoading = false
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
                    lastPurchasedProductId = product.id
                    purchaseState = .purchased

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

import Foundation

struct Achievement: Identifiable {
    let name: String
    var id: String { name }
    let medal: String
    let visitedCountries: [String]
    let remainingCountries: [String]
    var itemLabel: String = "countries"

    var current: Int { visitedCountries.count }
    var total: Int { visitedCountries.count + remainingCountries.count }
    var isCompleted: Bool { current >= total }
    var progress: Double { total > 0 ? Double(current) / Double(total) : 0 }
    var percentage: Int { Int(progress * 100) }
}

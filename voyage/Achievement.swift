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

/// The New 7 Wonders of the World plus the Pyramids of Giza — the only surviving
/// ancient wonder, which the New7Wonders campaign named an honorary eighth rather
/// than putting it to the vote. Each is paired with the country whose attraction
/// checklist (country_highlights.json) contains it.
enum WondersOfTheWorld {
    static let wonders: [(country: String, attraction: String)] = [
        ("Brazil", "Christ the Redeemer"),
        ("China", "Great Wall of China"),
        ("Egypt", "Pyramids of Giza"),
        ("India", "Taj Mahal"),
        ("Italy", "Colosseum"),
        ("Jordan", "Petra"),
        ("Mexico", "Chichen Itza"),
        ("Peru", "Machu Picchu"),
    ]

    static func visited(from checkedAttractions: [String: Set<String>]) -> [String] {
        wonders
            .filter { checkedAttractions[$0.country]?.contains($0.attraction) == true }
            .map(\.attraction)
            .sorted()
    }

    static func remaining(from checkedAttractions: [String: Set<String>]) -> [String] {
        wonders
            .filter { checkedAttractions[$0.country]?.contains($0.attraction) != true }
            .map(\.attraction)
            .sorted()
    }
}

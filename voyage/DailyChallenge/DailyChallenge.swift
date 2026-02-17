import Foundation

struct DailyChallenge: Codable {
    let id: String
    let date: String
    let isGuessCountry: Bool
    let isGuessCapital: Bool
    let isGuessFlag: Bool
    let answer: String

    enum CodingKeys: String, CodingKey {
        case id, date, answer
        case isGuessCountry = "is_guess_country"
        case isGuessCapital = "is_guess_capital"
        case isGuessFlag = "is_guess_flag"
    }

    var questionType: QuestionType {
        if isGuessCountry { return .country }
        if isGuessCapital { return .capital }
        return .flag
    }
}

enum QuestionType {
    case country
    case capital
    case flag

    var title: String {
        switch self {
        case .country: return "Name That Country"
        case .capital: return "Name That Capital"
        case .flag: return "Name That Flag"
        }
    }

    var subtitle: String {
        switch self {
        case .country: return "Identify the country from its outline"
        case .capital: return "Name the capital city"
        case .flag: return "Identify the country from its flag"
        }
    }

    var icon: String {
        switch self {
        case .country: return "map.fill"
        case .capital: return "building.columns.fill"
        case .flag: return "flag.fill"
        }
    }
}

struct ChallengeResult: Codable {
    let date: String
    let attempts: Int
    let solved: Bool
    let completed: Bool
    let completedOnChallengeDay: Bool
    let guesses: [String]
}

enum DailyChallengeError: Error, LocalizedError {
    case networkError(String)
    case noChallengeForDate
    case decodingError

    var errorDescription: String? {
        switch self {
        case .networkError(let message): return message
        case .noChallengeForDate: return "No challenge available for this date"
        case .decodingError: return "Failed to load challenge data"
        }
    }
}

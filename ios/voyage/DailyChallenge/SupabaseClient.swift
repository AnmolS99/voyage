import Foundation

enum SupabaseClient {
    private static let baseURL: String = {
        guard let url = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String, !url.isEmpty else {
            fatalError("SUPABASE_URL not set — check Secrets.xcconfig")
        }
        return url
    }()

    private static let publishableKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_PUBLISHABLE_KEY") as? String, !key.isEmpty else {
            fatalError("SUPABASE_PUBLISHABLE_KEY not set — check Secrets.xcconfig")
        }
        return key
    }()

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func fetchChallenge(for date: Date) async throws -> DailyChallenge {
        let dateString = dateFormatter.string(from: date)
        let urlString = "\(baseURL)/rest/v1/daily_challenges?date=eq.\(dateString)&select=*"

        guard let url = URL(string: urlString) else {
            throw DailyChallengeError.networkError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(publishableKey)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw DailyChallengeError.networkError("Server error")
        }

        let challenges = try JSONDecoder().decode([DailyChallenge].self, from: data)

        guard let challenge = challenges.first else {
            throw DailyChallengeError.noChallengeForDate
        }

        return challenge
    }

    static func fetchAvailableDates() async throws -> [String] {
        let urlString = "\(baseURL)/rest/v1/daily_challenges?select=date"

        guard let url = URL(string: urlString) else {
            throw DailyChallengeError.networkError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(publishableKey)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw DailyChallengeError.networkError("Server error")
        }

        struct DateRow: Codable { let date: String }
        let rows = try JSONDecoder().decode([DateRow].self, from: data)
        return rows.map { $0.date }
    }
}

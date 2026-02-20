import SwiftUI

struct ChallengeCalendarView: View {
    @ObservedObject var globeState: GlobeState
    @State private var displayedMonth = Date()
    @State private var availableDates: Set<String> = []
    @State private var selectedDate: Date?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private let calendar = Calendar.current
    private let store = ChallengeStore.shared
    private let weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static let monthYearFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMMM yyyy"
        return f
    }()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Month navigation
                    monthNavigationHeader

                    // Calendar grid
                    calendarGrid

                    // Error message
                    if let errorMessage = errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13, design: .rounded))
                            .foregroundColor(.red)
                            .padding(.horizontal, 20)
                    }

                    // Streak
                    streakCard

                    // Stats summary
                    statsSummary
                }
                .padding(.vertical, 16)
            }
            .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            .navigationTitle("Daily Challenge")
            .navigationBarTitleDisplayMode(.inline)
        }
        .sheet(item: Binding(
            get: { selectedDate.map { IdentifiableDate(date: $0) } },
            set: { selectedDate = $0?.date }
        )) { item in
            ChallengePlayView(date: item.date, isDarkMode: globeState.isDarkMode)
        }
        .task {
            await loadAvailableDates()
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }

    // MARK: - Month Navigation

    private var monthNavigationHeader: some View {
        HStack {
            Button {
                withAnimation { changeMonth(by: -1) }
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
            }

            Spacer()

            Text(Self.monthYearFormatter.string(from: displayedMonth))
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))

            Spacer()

            Button {
                withAnimation { changeMonth(by: 1) }
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Calendar Grid

    private var calendarGrid: some View {
        VStack(spacing: 8) {
            // Weekday headers
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(weekdays, id: \.self) { day in
                    Text(day)
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(AppColors.textMuted(isDarkMode: globeState.isDarkMode))
                        .frame(height: 28)
                }
            }

            // Day cells
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(daysInMonth(), id: \.self) { day in
                    if let day = day {
                        dayCell(for: day)
                    } else {
                        Color.clear
                            .frame(height: 44)
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }

    private func dayCell(for date: Date) -> some View {
        let dateString = Self.dateFormatter.string(from: date)
        let hasChallenge = availableDates.contains(dateString)
        let result = store.getResult(for: dateString)
        let isToday = calendar.isDateInToday(date)
        let isFuture = calendar.compare(date, to: Date(), toGranularity: .day) == .orderedDescending
        let isPlayable = hasChallenge && !isFuture

        return Button {
            if isPlayable {
                selectedDate = date
            }
        } label: {
            ZStack {
                // Background
                RoundedRectangle(cornerRadius: 10)
                    .fill(dayCellBackground(hasChallenge: hasChallenge, isFuture: isFuture, result: result, isToday: isToday))

                // Border for today
                if isToday {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(AppColors.buttonColor(isDarkMode: globeState.isDarkMode), lineWidth: 2)
                }

                VStack(spacing: 2) {
                    Text("\(calendar.component(.day, from: date))")
                        .font(.system(size: 15, weight: isToday ? .bold : .medium, design: .rounded))
                        .foregroundColor(dayTextColor(hasChallenge: hasChallenge, isFuture: isFuture, result: result))

                    // Status indicator
                    if let result = result, result.completed {
                        Image(systemName: result.solved ? "checkmark" : "xmark")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(result.solved ? .green : .red)
                    } else if hasChallenge && isFuture {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 8))
                            .foregroundColor(AppColors.textMuted(isDarkMode: globeState.isDarkMode))
                    }
                }
            }
            .frame(height: 44)
            .opacity(isFuture && hasChallenge ? 0.5 : 1.0)
        }
        .disabled(!isPlayable)
    }

    private func dayCellBackground(hasChallenge: Bool, isFuture: Bool, result: ChallengeResult?, isToday: Bool) -> Color {
        if let result = result {
            return result.solved
                ? Color.green.opacity(globeState.isDarkMode ? 0.15 : 0.1)
                : Color.red.opacity(globeState.isDarkMode ? 0.15 : 0.1)
        }
        if hasChallenge && !isFuture {
            return AppColors.buttonColor(isDarkMode: globeState.isDarkMode).opacity(0.1)
        }
        return .clear
    }

    private func dayTextColor(hasChallenge: Bool, isFuture: Bool, result: ChallengeResult?) -> Color {
        if isFuture {
            return AppColors.textMuted(isDarkMode: globeState.isDarkMode)
        }
        if hasChallenge || result != nil {
            return AppColors.textPrimary(isDarkMode: globeState.isDarkMode)
        }
        return AppColors.textMuted(isDarkMode: globeState.isDarkMode)
    }

    // MARK: - Streak Card

    private var streakCard: some View {
        let streak = store.currentStreak
        return HStack(spacing: 12) {
            Text("🔥")
                .font(.system(size: 36))
            VStack(alignment: .leading, spacing: 2) {
                Text("\(streak) day\(streak == 1 ? "" : "s")")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))
                Text("Current Streak")
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: globeState.isDarkMode))
            }
            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 20)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }

    // MARK: - Stats Summary

    private var statsSummary: some View {
        let results = store.allResults().values.filter { $0.completed }
        let solved = results.filter { $0.solved }.count
        let total = results.count

        return HStack(spacing: 24) {
            statItem(value: "\(solved)", label: "Solved")
            statItem(value: "\(total)", label: "Played")
            statItem(
                value: total > 0 ? "\(Int(Double(solved) / Double(total) * 100))%" : "—",
                label: "Accuracy"
            )
        }
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }

    private func statItem(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))
            Text(label)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: globeState.isDarkMode))
        }
    }

    // MARK: - Helpers

    private func changeMonth(by offset: Int) {
        if let newMonth = calendar.date(byAdding: .month, value: offset, to: displayedMonth) {
            displayedMonth = newMonth
        }
    }

    private func daysInMonth() -> [Date?] {
        guard let range = calendar.range(of: .day, in: .month, for: displayedMonth),
              let firstOfMonth = calendar.date(from: calendar.dateComponents([.year, .month], from: displayedMonth)) else {
            return []
        }

        let firstWeekday = calendar.component(.weekday, from: firstOfMonth) - 1
        var days: [Date?] = Array(repeating: nil, count: firstWeekday)

        for day in range {
            if let date = calendar.date(byAdding: .day, value: day - 1, to: firstOfMonth) {
                days.append(date)
            }
        }

        return days
    }

    private func loadAvailableDates() async {
        isLoading = true
        errorMessage = nil
        do {
            let dates = try await SupabaseClient.fetchAvailableDates()
            availableDates = Set(dates)
        } catch {
            errorMessage = "Failed to load challenges: \(error.localizedDescription)"
        }
        isLoading = false
    }
}

private struct IdentifiableDate: Identifiable {
    let date: Date
    var id: TimeInterval { date.timeIntervalSince1970 }
}

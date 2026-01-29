import Foundation

enum Continent: String, CaseIterable {
    case africa = "Africa"
    case asia = "Asia"
    case europe = "Europe"
    case northAmerica = "North America"
    case southAmerica = "South America"
    case oceania = "Oceania"
    case antarctica = "Antarctica"

    var medal: String {
        switch self {
        case .africa: return "🦁"
        case .asia: return "🐉"
        case .europe: return "🏰"
        case .northAmerica: return "🦅"
        case .southAmerica: return "🦜"
        case .oceania: return "🐨"
        case .antarctica: return "🐧"
        }
    }

    var countries: Set<String> {
        Set(ContinentData.countriesByContinent[self] ?? [])
    }
}

struct ContinentData {
    static let countriesByContinent: [Continent: [String]] = [
        .africa: [
            "Algeria", "Angola", "Benin", "Botswana", "Burkina Faso", "Burundi",
            "Cameroon", "Cape Verde", "Central African Republic", "Chad", "Comoros",
            "Democratic Republic of the Congo", "Djibouti", "Egypt", "Equatorial Guinea",
            "Eritrea", "Eswatini", "Ethiopia", "Gabon", "Gambia", "Ghana", "Guinea",
            "Guinea Bissau", "Côte d'Ivoire", "Kenya", "Lesotho", "Liberia", "Libya",
            "Madagascar", "Malawi", "Mali", "Mauritania", "Mauritius", "Morocco",
            "Mozambique", "Namibia", "Niger", "Nigeria", "Republic of the Congo", "Rwanda",
            "Sao Tome and Principe", "Senegal", "Seychelles", "Sierra Leone", "Somalia",
            "South Africa", "South Sudan", "Sudan", "Tanzania", "Togo", "Tunisia",
            "Uganda", "Zambia", "Zimbabwe"
        ],
        .asia: [
            "Afghanistan", "Armenia", "Azerbaijan", "Bahrain", "Bangladesh", "Bhutan",
            "Brunei", "Cambodia", "China", "Cyprus", "Georgia", "India", "Indonesia",
            "Iran", "Iraq", "Israel", "Japan", "Jordan", "Kazakhstan", "Kuwait",
            "Kyrgyzstan", "Laos", "Lebanon", "Malaysia", "Maldives", "Mongolia",
            "Myanmar", "Nepal", "North Korea", "Oman", "Pakistan", "Palestine",
            "Philippines", "Qatar", "Russia", "Saudi Arabia", "Singapore", "South Korea",
            "Sri Lanka", "Syria", "Taiwan", "Tajikistan", "Thailand", "Timor-Leste",
            "Turkey", "Turkmenistan", "United Arab Emirates", "Uzbekistan", "Vietnam", "Yemen"
        ],
        .europe: [
            "Albania", "Andorra", "Austria", "Belarus", "Belgium", "Bosnia and Herzegovina",
            "Bulgaria", "Croatia", "Czechia", "Denmark", "Estonia", "Finland",
            "France", "Germany", "Greece", "Hungary", "Iceland", "Ireland", "Italy",
            "Kosovo", "Latvia", "Liechtenstein", "Lithuania", "Luxembourg", "Malta",
            "Moldova", "Monaco", "Montenegro", "Netherlands", "North Macedonia", "Norway",
            "Poland", "Portugal", "Romania", "San Marino", "Serbia", "Slovakia", "Slovenia",
            "Spain", "Sweden", "Switzerland", "Ukraine", "United Kingdom", "Vatican City"
        ],
        .northAmerica: [
            "Antigua and Barbuda", "The Bahamas", "Barbados", "Belize", "Canada", "Costa Rica",
            "Cuba", "Dominica", "Dominican Republic", "El Salvador", "Grenada", "Guatemala",
            "Haiti", "Honduras", "Jamaica", "Mexico", "Nicaragua", "Panama",
            "Saint Kitts and Nevis", "Saint Lucia", "Saint Vincent and the Grenadines",
            "Trinidad and Tobago", "United States of America"
        ],
        .southAmerica: [
            "Argentina", "Bolivia", "Brazil", "Chile", "Colombia", "Ecuador", "Guyana",
            "Paraguay", "Peru", "Suriname", "Uruguay", "Venezuela"
        ],
        .oceania: [
            "Australia", "Fiji", "Kiribati", "Marshall Islands", "Micronesia", "Nauru",
            "New Zealand", "Palau", "Papua New Guinea", "Samoa", "Solomon Islands",
            "Tonga", "Tuvalu", "Vanuatu"
        ],
        .antarctica: []
    ]

    static func continent(for country: String) -> Continent? {
        for (continent, countries) in countriesByContinent {
            if countries.contains(country) {
                return continent
            }
        }
        return nil
    }

    static func visitedCountries(in continent: Continent, from visited: Set<String>) -> Set<String> {
        visited.intersection(continent.countries)
    }
}

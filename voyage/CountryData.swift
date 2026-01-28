import UIKit

struct Coordinate {
    let x: Double
    let y: Double
}

struct CountryInfo {
    let name: String
    let centerLat: Double
    let centerLon: Double
    let size: Double
    let color: UIColor
    let regions: [[Coordinate]]

    init(name: String, centerLat: Double, centerLon: Double, size: Double = 5.0, color: UIColor? = nil, regions: [[Coordinate]] = []) {
        self.name = name
        self.centerLat = centerLat
        self.centerLon = centerLon
        self.size = size
        self.color = color ?? CountryInfo.randomLandColor()
        self.regions = regions
    }

    static func randomLandColor() -> UIColor {
        let colors: [UIColor] = [
            UIColor(red: 0.30, green: 0.60, blue: 0.35, alpha: 1.0), // Forest green
            UIColor(red: 0.35, green: 0.55, blue: 0.30, alpha: 1.0), // Darker green
            UIColor(red: 0.40, green: 0.65, blue: 0.40, alpha: 1.0), // Light green
            UIColor(red: 0.45, green: 0.58, blue: 0.38, alpha: 1.0), // Olive green
            UIColor(red: 0.38, green: 0.52, blue: 0.32, alpha: 1.0), // Sage green
        ]
        return colors.randomElement()!
    }
}

class CountryData {

    static func getCountries() -> [CountryInfo] {
        return [
            // North America
            CountryInfo(name: "United States", centerLat: 39.8, centerLon: -98.5, size: 20),
            CountryInfo(name: "Canada", centerLat: 56.1, centerLon: -106.3, size: 25),
            CountryInfo(name: "Mexico", centerLat: 23.6, centerLon: -102.5, size: 12),
            CountryInfo(name: "Guatemala", centerLat: 15.8, centerLon: -90.2, size: 3),
            CountryInfo(name: "Cuba", centerLat: 21.5, centerLon: -79.9, size: 4),
            CountryInfo(name: "Haiti", centerLat: 19.0, centerLon: -72.3, size: 2),
            CountryInfo(name: "Dominican Republic", centerLat: 18.7, centerLon: -70.2, size: 2),
            CountryInfo(name: "Honduras", centerLat: 15.0, centerLon: -86.5, size: 3),
            CountryInfo(name: "Nicaragua", centerLat: 12.9, centerLon: -85.2, size: 3),
            CountryInfo(name: "Costa Rica", centerLat: 9.7, centerLon: -83.8, size: 2),
            CountryInfo(name: "Panama", centerLat: 8.5, centerLon: -80.8, size: 2),
            CountryInfo(name: "Jamaica", centerLat: 18.1, centerLon: -77.3, size: 1.5),
            CountryInfo(name: "El Salvador", centerLat: 13.8, centerLon: -88.9, size: 1.5),
            CountryInfo(name: "Belize", centerLat: 17.2, centerLon: -88.5, size: 1.5),
            CountryInfo(name: "Antigua and Barbuda", centerLat: 17.06, centerLon: -61.80, size: 0.5),
            CountryInfo(name: "Barbados", centerLat: 13.19, centerLon: -59.54, size: 0.5),
            CountryInfo(name: "Dominica", centerLat: 15.41, centerLon: -61.37, size: 0.5),
            CountryInfo(name: "Grenada", centerLat: 12.12, centerLon: -61.68, size: 0.5),
            CountryInfo(name: "Saint Kitts and Nevis", centerLat: 17.36, centerLon: -62.78, size: 0.4),
            CountryInfo(name: "Saint Lucia", centerLat: 13.91, centerLon: -60.98, size: 0.5),
            CountryInfo(name: "Saint Vincent and the Grenadines", centerLat: 13.25, centerLon: -61.20, size: 0.4),
            CountryInfo(name: "Bahamas", centerLat: 25.03, centerLon: -77.40, size: 1.5),
            CountryInfo(name: "Trinidad and Tobago", centerLat: 10.69, centerLon: -61.22, size: 1),

            // South America
            CountryInfo(name: "Brazil", centerLat: -14.2, centerLon: -51.9, size: 22),
            CountryInfo(name: "Argentina", centerLat: -38.4, centerLon: -63.6, size: 15),
            CountryInfo(name: "Peru", centerLat: -9.2, centerLon: -75.0, size: 10),
            CountryInfo(name: "Colombia", centerLat: 4.6, centerLon: -74.3, size: 8),
            CountryInfo(name: "Venezuela", centerLat: 6.4, centerLon: -66.6, size: 7),
            CountryInfo(name: "Chile", centerLat: -35.7, centerLon: -71.5, size: 8),
            CountryInfo(name: "Ecuador", centerLat: -1.8, centerLon: -78.2, size: 4),
            CountryInfo(name: "Bolivia", centerLat: -16.3, centerLon: -63.6, size: 7),
            CountryInfo(name: "Paraguay", centerLat: -23.4, centerLon: -58.4, size: 5),
            CountryInfo(name: "Uruguay", centerLat: -32.5, centerLon: -55.8, size: 3),
            CountryInfo(name: "Guyana", centerLat: 4.9, centerLon: -58.9, size: 3),
            CountryInfo(name: "Suriname", centerLat: 3.9, centerLon: -56.0, size: 2.5),

            // Europe
            CountryInfo(name: "Russia", centerLat: 61.5, centerLon: 105.3, size: 30),
            CountryInfo(name: "Germany", centerLat: 51.2, centerLon: 10.5, size: 5),
            CountryInfo(name: "France", centerLat: 46.2, centerLon: 2.2, size: 6),
            CountryInfo(name: "United Kingdom", centerLat: 55.4, centerLon: -3.4, size: 4),
            CountryInfo(name: "Italy", centerLat: 41.9, centerLon: 12.6, size: 5),
            CountryInfo(name: "Spain", centerLat: 40.5, centerLon: -3.7, size: 6),
            CountryInfo(name: "Ukraine", centerLat: 48.4, centerLon: 31.2, size: 7),
            CountryInfo(name: "Poland", centerLat: 51.9, centerLon: 19.1, size: 5),
            CountryInfo(name: "Romania", centerLat: 45.9, centerLon: 25.0, size: 4),
            CountryInfo(name: "Netherlands", centerLat: 52.1, centerLon: 5.3, size: 2),
            CountryInfo(name: "Belgium", centerLat: 50.5, centerLon: 4.5, size: 1.5),
            CountryInfo(name: "Greece", centerLat: 39.1, centerLon: 21.8, size: 3),
            CountryInfo(name: "Portugal", centerLat: 39.4, centerLon: -8.2, size: 3),
            CountryInfo(name: "Sweden", centerLat: 60.1, centerLon: 18.6, size: 6),
            CountryInfo(name: "Norway", centerLat: 60.5, centerLon: 8.5, size: 6),
            CountryInfo(name: "Finland", centerLat: 61.9, centerLon: 25.7, size: 5),
            CountryInfo(name: "Denmark", centerLat: 56.3, centerLon: 9.5, size: 2),
            CountryInfo(name: "Switzerland", centerLat: 46.8, centerLon: 8.2, size: 2),
            CountryInfo(name: "Austria", centerLat: 47.5, centerLon: 14.6, size: 2.5),
            CountryInfo(name: "Czech Republic", centerLat: 49.8, centerLon: 15.5, size: 2.5),
            CountryInfo(name: "Hungary", centerLat: 47.2, centerLon: 19.5, size: 2.5),
            CountryInfo(name: "Ireland", centerLat: 53.4, centerLon: -8.2, size: 2.5),
            CountryInfo(name: "Slovakia", centerLat: 48.7, centerLon: 19.7, size: 2),
            CountryInfo(name: "Bulgaria", centerLat: 42.7, centerLon: 25.5, size: 3),
            CountryInfo(name: "Serbia", centerLat: 44.0, centerLon: 21.0, size: 2.5),
            CountryInfo(name: "Croatia", centerLat: 45.1, centerLon: 15.2, size: 2),
            CountryInfo(name: "Bosnia and Herzegovina", centerLat: 43.9, centerLon: 17.7, size: 2),
            CountryInfo(name: "Albania", centerLat: 41.2, centerLon: 20.2, size: 1.5),
            CountryInfo(name: "Lithuania", centerLat: 55.2, centerLon: 23.9, size: 2),
            CountryInfo(name: "Latvia", centerLat: 56.9, centerLon: 24.6, size: 2),
            CountryInfo(name: "Estonia", centerLat: 58.6, centerLon: 25.0, size: 2),
            CountryInfo(name: "Slovenia", centerLat: 46.2, centerLon: 14.9, size: 1.5),
            CountryInfo(name: "North Macedonia", centerLat: 41.5, centerLon: 21.7, size: 1.5),
            CountryInfo(name: "Montenegro", centerLat: 42.7, centerLon: 19.4, size: 1),
            CountryInfo(name: "Luxembourg", centerLat: 49.8, centerLon: 6.1, size: 0.8),
            CountryInfo(name: "Malta", centerLat: 35.9, centerLon: 14.4, size: 0.5),
            CountryInfo(name: "Andorra", centerLat: 42.51, centerLon: 1.52, size: 0.3),
            CountryInfo(name: "Liechtenstein", centerLat: 47.17, centerLon: 9.56, size: 0.3),
            CountryInfo(name: "Monaco", centerLat: 43.74, centerLon: 7.42, size: 0.2),
            CountryInfo(name: "San Marino", centerLat: 43.94, centerLon: 12.46, size: 0.3),
            CountryInfo(name: "Vatican City", centerLat: 41.90, centerLon: 12.45, size: 0.5),
            CountryInfo(name: "Iceland", centerLat: 64.9, centerLon: -19.0, size: 3),
            CountryInfo(name: "Cyprus", centerLat: 35.1, centerLon: 33.4, size: 1.5),
            CountryInfo(name: "Moldova", centerLat: 47.4, centerLon: 28.4, size: 2),
            CountryInfo(name: "Belarus", centerLat: 53.7, centerLon: 27.9, size: 4),

            // Asia
            CountryInfo(name: "China", centerLat: 35.9, centerLon: 104.2, size: 20),
            CountryInfo(name: "India", centerLat: 20.6, centerLon: 79.0, size: 15),
            CountryInfo(name: "Japan", centerLat: 36.2, centerLon: 138.3, size: 6),
            CountryInfo(name: "South Korea", centerLat: 35.9, centerLon: 127.8, size: 3),
            CountryInfo(name: "North Korea", centerLat: 40.3, centerLon: 127.5, size: 3),
            CountryInfo(name: "Indonesia", centerLat: -0.8, centerLon: 113.9, size: 12),
            CountryInfo(name: "Pakistan", centerLat: 30.4, centerLon: 69.3, size: 8),
            CountryInfo(name: "Bangladesh", centerLat: 23.7, centerLon: 90.4, size: 4),
            CountryInfo(name: "Vietnam", centerLat: 14.1, centerLon: 108.3, size: 5),
            CountryInfo(name: "Thailand", centerLat: 15.9, centerLon: 100.9, size: 5),
            CountryInfo(name: "Myanmar", centerLat: 21.9, centerLon: 95.9, size: 6),
            CountryInfo(name: "Philippines", centerLat: 12.9, centerLon: 121.8, size: 5),
            CountryInfo(name: "Malaysia", centerLat: 4.2, centerLon: 101.9, size: 4),
            CountryInfo(name: "Nepal", centerLat: 28.4, centerLon: 84.1, size: 3),
            CountryInfo(name: "Sri Lanka", centerLat: 7.9, centerLon: 80.8, size: 2),
            CountryInfo(name: "Kazakhstan", centerLat: 48.0, centerLon: 68.0, size: 15),
            CountryInfo(name: "Uzbekistan", centerLat: 41.4, centerLon: 64.6, size: 6),
            CountryInfo(name: "Turkmenistan", centerLat: 38.9, centerLon: 59.6, size: 5),
            CountryInfo(name: "Afghanistan", centerLat: 33.9, centerLon: 67.7, size: 6),
            CountryInfo(name: "Iran", centerLat: 32.4, centerLon: 53.7, size: 10),
            CountryInfo(name: "Iraq", centerLat: 33.2, centerLon: 43.7, size: 6),
            CountryInfo(name: "Saudi Arabia", centerLat: 23.9, centerLon: 45.1, size: 12),
            CountryInfo(name: "Yemen", centerLat: 15.6, centerLon: 48.5, size: 5),
            CountryInfo(name: "Oman", centerLat: 21.5, centerLon: 55.9, size: 4),
            CountryInfo(name: "UAE", centerLat: 23.4, centerLon: 53.8, size: 2.5),
            CountryInfo(name: "Qatar", centerLat: 25.4, centerLon: 51.2, size: 1.5),
            CountryInfo(name: "Kuwait", centerLat: 29.3, centerLon: 47.5, size: 1.5),
            CountryInfo(name: "Bahrain", centerLat: 26.0, centerLon: 50.6, size: 0.8),
            CountryInfo(name: "Jordan", centerLat: 30.6, centerLon: 36.2, size: 2.5),
            CountryInfo(name: "Israel", centerLat: 31.0, centerLon: 34.9, size: 1.5),
            CountryInfo(name: "Palestine", centerLat: 31.95, centerLon: 35.23, size: 0.8),
            CountryInfo(name: "Lebanon", centerLat: 33.9, centerLon: 35.9, size: 1),
            CountryInfo(name: "Syria", centerLat: 34.8, centerLon: 39.0, size: 4),
            CountryInfo(name: "Turkey", centerLat: 38.9, centerLon: 35.2, size: 8),
            CountryInfo(name: "Georgia", centerLat: 42.3, centerLon: 43.4, size: 2),
            CountryInfo(name: "Armenia", centerLat: 40.1, centerLon: 45.0, size: 1.5),
            CountryInfo(name: "Azerbaijan", centerLat: 40.1, centerLon: 47.6, size: 2.5),
            CountryInfo(name: "Mongolia", centerLat: 46.9, centerLon: 103.8, size: 10),
            CountryInfo(name: "Cambodia", centerLat: 12.6, centerLon: 105.0, size: 3),
            CountryInfo(name: "Laos", centerLat: 19.9, centerLon: 102.5, size: 3),
            CountryInfo(name: "Singapore", centerLat: 1.4, centerLon: 103.8, size: 0.5),
            CountryInfo(name: "Brunei", centerLat: 4.5, centerLon: 114.7, size: 0.8),
            CountryInfo(name: "Taiwan", centerLat: 23.7, centerLon: 121.0, size: 2),
            CountryInfo(name: "Kyrgyzstan", centerLat: 41.2, centerLon: 74.8, size: 3),
            CountryInfo(name: "Tajikistan", centerLat: 38.9, centerLon: 71.3, size: 3),
            CountryInfo(name: "Bhutan", centerLat: 27.5, centerLon: 90.4, size: 1.5),
            CountryInfo(name: "Maldives", centerLat: 3.2, centerLon: 73.2, size: 0.5),
            CountryInfo(name: "Timor-Leste", centerLat: -8.87, centerLon: 125.73, size: 1),

            // Africa
            CountryInfo(name: "Nigeria", centerLat: 9.1, centerLon: 8.7, size: 8),
            CountryInfo(name: "Egypt", centerLat: 26.8, centerLon: 30.8, size: 8),
            CountryInfo(name: "South Africa", centerLat: -30.6, centerLon: 22.9, size: 9),
            CountryInfo(name: "Algeria", centerLat: 28.0, centerLon: 1.7, size: 12),
            CountryInfo(name: "Sudan", centerLat: 12.9, centerLon: 30.2, size: 10),
            CountryInfo(name: "Morocco", centerLat: 31.8, centerLon: -7.1, size: 5),
            CountryInfo(name: "Kenya", centerLat: -0.0, centerLon: 38.0, size: 6),
            CountryInfo(name: "Ethiopia", centerLat: 9.1, centerLon: 40.5, size: 8),
            CountryInfo(name: "Ghana", centerLat: 7.9, centerLon: -1.0, size: 4),
            CountryInfo(name: "Tanzania", centerLat: -6.4, centerLon: 34.9, size: 7),
            CountryInfo(name: "DRC", centerLat: -4.0, centerLon: 21.8, size: 12),
            CountryInfo(name: "Angola", centerLat: -11.2, centerLon: 17.9, size: 9),
            CountryInfo(name: "Mozambique", centerLat: -18.7, centerLon: 35.5, size: 7),
            CountryInfo(name: "Madagascar", centerLat: -18.8, centerLon: 46.9, size: 6),
            CountryInfo(name: "Ivory Coast", centerLat: 7.5, centerLon: -5.5, size: 5),
            CountryInfo(name: "Cameroon", centerLat: 7.4, centerLon: 12.4, size: 5),
            CountryInfo(name: "Niger", centerLat: 17.6, centerLon: 8.1, size: 9),
            CountryInfo(name: "Mali", centerLat: 17.6, centerLon: -4.0, size: 9),
            CountryInfo(name: "Senegal", centerLat: 14.5, centerLon: -14.5, size: 4),
            CountryInfo(name: "Zimbabwe", centerLat: -19.0, centerLon: 29.2, size: 5),
            CountryInfo(name: "Zambia", centerLat: -13.1, centerLon: 27.8, size: 6),
            CountryInfo(name: "Tunisia", centerLat: 33.9, centerLon: 9.5, size: 3),
            CountryInfo(name: "Libya", centerLat: 26.3, centerLon: 17.2, size: 10),
            CountryInfo(name: "Uganda", centerLat: 1.4, centerLon: 32.3, size: 4),
            CountryInfo(name: "Botswana", centerLat: -22.3, centerLon: 24.7, size: 6),
            CountryInfo(name: "Namibia", centerLat: -22.6, centerLon: 17.1, size: 7),
            CountryInfo(name: "Mauritania", centerLat: 21.0, centerLon: -10.9, size: 8),
            CountryInfo(name: "Somalia", centerLat: 5.2, centerLon: 46.2, size: 6),
            CountryInfo(name: "Chad", centerLat: 15.5, centerLon: 18.7, size: 9),
            CountryInfo(name: "Central African Republic", centerLat: 6.6, centerLon: 20.9, size: 6),
            CountryInfo(name: "South Sudan", centerLat: 7.9, centerLon: 30.0, size: 6),
            CountryInfo(name: "Republic of Congo", centerLat: -0.2, centerLon: 15.8, size: 5),
            CountryInfo(name: "Gabon", centerLat: -0.8, centerLon: 11.6, size: 4),
            CountryInfo(name: "Equatorial Guinea", centerLat: 1.7, centerLon: 10.3, size: 1.5),
            CountryInfo(name: "Eritrea", centerLat: 15.2, centerLon: 39.8, size: 3),
            CountryInfo(name: "Benin", centerLat: 9.3, centerLon: 2.3, size: 3),
            CountryInfo(name: "Togo", centerLat: 8.6, centerLon: 0.8, size: 2),
            CountryInfo(name: "Burkina Faso", centerLat: 12.2, centerLon: -1.6, size: 5),
            CountryInfo(name: "Sierra Leone", centerLat: 8.5, centerLon: -11.8, size: 2.5),
            CountryInfo(name: "Liberia", centerLat: 6.4, centerLon: -9.4, size: 3),
            CountryInfo(name: "Guinea", centerLat: 9.9, centerLon: -9.7, size: 4),
            CountryInfo(name: "Guinea-Bissau", centerLat: 11.8, centerLon: -15.2, size: 2),
            CountryInfo(name: "Gambia", centerLat: 13.4, centerLon: -16.6, size: 1),
            CountryInfo(name: "Rwanda", centerLat: -1.9, centerLon: 29.9, size: 1.5),
            CountryInfo(name: "Burundi", centerLat: -3.4, centerLon: 29.9, size: 1.5),
            CountryInfo(name: "Lesotho", centerLat: -29.6, centerLon: 28.2, size: 1.5),
            CountryInfo(name: "Eswatini", centerLat: -26.5, centerLon: 31.5, size: 1),
            CountryInfo(name: "Malawi", centerLat: -13.3, centerLon: 34.3, size: 3),
            CountryInfo(name: "Djibouti", centerLat: 11.8, centerLon: 42.6, size: 1.5),
            CountryInfo(name: "Mauritius", centerLat: -20.3, centerLon: 57.6, size: 0.8),
            CountryInfo(name: "Comoros", centerLat: -11.9, centerLon: 43.9, size: 0.5),
            CountryInfo(name: "Seychelles", centerLat: -4.7, centerLon: 55.5, size: 0.5),
            CountryInfo(name: "Cape Verde", centerLat: 16.0, centerLon: -24.0, size: 0.8),
            CountryInfo(name: "Sao Tome and Principe", centerLat: 0.2, centerLon: 6.6, size: 0.5),

            // Oceania
            CountryInfo(name: "Australia", centerLat: -25.3, centerLon: 133.8, size: 20),
            CountryInfo(name: "New Zealand", centerLat: -40.9, centerLon: 174.9, size: 5),
            CountryInfo(name: "Papua New Guinea", centerLat: -6.3, centerLon: 143.9, size: 6),
            CountryInfo(name: "Fiji", centerLat: -18.0, centerLon: 179.0, size: 2),
            CountryInfo(name: "Solomon Islands", centerLat: -9.4, centerLon: 160.0, size: 2),
            CountryInfo(name: "Vanuatu", centerLat: -15.4, centerLon: 166.9, size: 1.5),
            CountryInfo(name: "Samoa", centerLat: -13.8, centerLon: -172.0, size: 1),
            CountryInfo(name: "Tonga", centerLat: -21.2, centerLon: -175.2, size: 0.8),
            CountryInfo(name: "Micronesia", centerLat: 7.4, centerLon: 150.5, size: 1),
            CountryInfo(name: "Palau", centerLat: 7.5, centerLon: 134.6, size: 0.5),
            CountryInfo(name: "Marshall Islands", centerLat: 7.1, centerLon: 171.2, size: 0.5),
            CountryInfo(name: "Kiribati", centerLat: 1.9, centerLon: -157.4, size: 0.5),
            CountryInfo(name: "Nauru", centerLat: -0.5, centerLon: 166.9, size: 0.3),
            CountryInfo(name: "Tuvalu", centerLat: -7.1, centerLon: 179.2, size: 0.3),

            // Additional territories and regions
            CountryInfo(name: "Greenland", centerLat: 71.7, centerLon: -42.6, size: 15),
            CountryInfo(name: "Antarctica", centerLat: -82.9, centerLon: 0.0, size: 25),
        ]
    }
}

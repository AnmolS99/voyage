package com.anmol.voyage.data

/**
 * ISO 3166-1 alpha-2 code → flag emoji, the Kotlin port of the iOS
 * `flagEmojiFromCode`.
 *
 * A flag emoji is its country's two letters written as regional indicator
 * symbols, which sit a fixed distance above ASCII — so the whole conversion is
 * an offset, no lookup table. `world.geojson` uses the same code as a feature's
 * `id`, which is why the app never stores flags anywhere.
 */
object FlagEmoji {

    /** Shown when a country has no usable code, matching iOS. */
    const val FALLBACK = "🌍"

    /** 🇦 — the first regional indicator, the letter `A`'s counterpart. */
    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private const val LETTER_A = 'A'.code

    /**
     * The flag for [isoCode], or [FALLBACK] for anything that is not a pair of
     * letters. iOS offsets whatever scalars it is handed; the guard here is the
     * one deliberate difference, because an unexpected code should show the globe
     * rather than two arbitrary symbols.
     */
    fun of(isoCode: String?): String {
        if (isoCode == null || isoCode.length != 2) return FALLBACK
        val builder = StringBuilder(4)
        for (char in isoCode) {
            val upper = char.uppercaseChar()
            if (upper !in 'A'..'Z') return FALLBACK
            builder.appendCodePoint(REGIONAL_INDICATOR_A + (upper.code - LETTER_A))
        }
        return builder.toString()
    }

    /** The flag for a parsed country, whose `id` is its ISO code. */
    fun of(country: GeoJsonCountry): String = of(country.isoCode)
}

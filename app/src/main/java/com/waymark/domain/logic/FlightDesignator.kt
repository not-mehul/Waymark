package com.waymark.domain.logic

/**
 * A parsed flight number. Airlines are inconsistent about how they print
 * these — "BA 286", "ba286", "BAW286", "LH1234A" — so the app parses loosely
 * and normalises hard.
 */
data class FlightDesignator(
    val carrier: String,
    val number: Int,
    val suffix: String?,
) {
    /** Canonical form, always uppercase, never zero-padded. */
    val normalised: String get() = "$carrier$number${suffix ?: ""}"

    /** How airlines print it on a boarding pass: carrier, space, padded number. */
    val printed: String get() = "$carrier ${number.toString().padStart(3, '0')}${suffix ?: ""}"

    companion object {
        // Two-letter IATA codes ("BA"), mixed codes with a leading digit ("3U")
        // or a trailing digit ("U2"), and three-letter ICAO codes ("BAW").
        private val PATTERN =
            Regex("^([A-Z0-9][A-Z]|[A-Z][A-Z0-9]|[A-Z]{3})(\\d{1,4})([A-Z])?$")

        /** Null when the text cannot be a flight number at all. */
        fun parse(input: String): FlightDesignator? {
            val cleaned = input.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
            if (cleaned.length < 3) return null
            val match = PATTERN.matchEntire(cleaned) ?: return null
            val (carrier, digits, suffix) = match.destructured
            val number = digits.toIntOrNull() ?: return null
            if (number <= 0) return null
            return FlightDesignator(carrier, number, suffix.ifBlank { null })
        }

        /** Is this text far enough along to be worth a lookup? */
        fun looksComplete(input: String): Boolean = parse(input) != null
    }
}

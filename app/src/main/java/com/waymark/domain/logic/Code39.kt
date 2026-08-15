package com.waymark.domain.logic

/**
 * Code 39 symbol generator, used to print a scannable reference — ticket
 * number, sequence number, record locator — on an offline boarding pass.
 *
 * Code 39 is a 1D symbology: nine elements per character, three of them wide.
 * It is deliberately not used for the full 60-character BCBP payload, which
 * belongs in a 2D symbol; when the traveler imports the airline's own pass
 * image Waymark shows that instead.
 */
object Code39 {

    /** Bar, space, bar, … nine elements, 'w' wide and 'n' narrow. */
    private val PATTERNS: Map<Char, String> = mapOf(
        '0' to "nnnwwnwnn", '1' to "wnnwnnnnw", '2' to "nnwwnnnnw", '3' to "wnwwnnnnn",
        '4' to "nnnwwnnnw", '5' to "wnnwwnnnn", '6' to "nnwwwnnnn", '7' to "nnnwnnwnw",
        '8' to "wnnwnnwnn", '9' to "nnwwnnwnn",
        'A' to "wnnnnwnnw", 'B' to "nnwnnwnnw", 'C' to "wnwnnwnnn", 'D' to "nnnnwwnnw",
        'E' to "wnnnwwnnn", 'F' to "nnwnwwnnn", 'G' to "nnnnnwwnw", 'H' to "wnnnnwwnn",
        'I' to "nnwnnwwnn", 'J' to "nnnnwwwnn", 'K' to "wnnnnnnww", 'L' to "nnwnnnnww",
        'M' to "wnwnnnnwn", 'N' to "nnnnwnnww", 'O' to "wnnnwnnwn", 'P' to "nnwnwnnwn",
        'Q' to "nnnnnnwww", 'R' to "wnnnnnwwn", 'S' to "nnwnnnwwn", 'T' to "nnnnwnwwn",
        'U' to "wwnnnnnnw", 'V' to "nwwnnnnnw", 'W' to "wwwnnnnnn", 'X' to "nwnnwnnnw",
        'Y' to "wwnnwnnnn", 'Z' to "nwwnwnnnn",
        '-' to "nwnnnnwnw", '.' to "wwnnnnwnn", ' ' to "nwwnnnwnn",
        '$' to "nwnwnwnnn", '/' to "nwnwnnnwn", '+' to "nwnnnwnwn", '%' to "nnnwnwnwn",
        '*' to "nwnnwnwnn",
    )

    /** Mod-43 check character alphabet, in value order. */
    private const val CHECK_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"

    const val START_STOP = '*'

    /** Characters this symbology can carry; anything else is dropped. */
    fun sanitise(input: String): String =
        input.uppercase().filter { it != START_STOP && PATTERNS.containsKey(it) }

    fun supports(char: Char): Boolean = PATTERNS.containsKey(char.uppercaseChar())

    fun checkCharacter(input: String): Char {
        val sum = sanitise(input).sumOf { CHECK_ALPHABET.indexOf(it).coerceAtLeast(0) }
        return CHECK_ALPHABET[sum % CHECK_ALPHABET.length]
    }

    /**
     * The symbol as a run-length list: each entry is a bar (true) or a space
     * (false) with a width in narrow-module units. Rendering is then just a
     * loop over runs, which keeps the canvas code free of symbology knowledge.
     */
    data class Run(val isBar: Boolean, val modules: Int)

    data class Symbol(val runs: List<Run>, val text: String) {
        val totalModules: Int get() = runs.sumOf { it.modules }
    }

    /**
     * @param wideRatio width of a wide element in narrow modules; 2 or 3, per
     *   the spec. 2 keeps long strings on screen, 3 scans more reliably.
     */
    fun encode(input: String, withCheckCharacter: Boolean = false, wideRatio: Int = 2): Symbol {
        require(wideRatio in 2..3) { "Code 39 wide ratio must be 2 or 3" }
        val body = sanitise(input).ifEmpty { "0" }
        val payload = if (withCheckCharacter) body + checkCharacter(body) else body
        val chars = buildString {
            append(START_STOP)
            append(payload)
            append(START_STOP)
        }

        val runs = mutableListOf<Run>()
        chars.forEachIndexed { index, char ->
            val pattern = PATTERNS.getValue(char)
            pattern.forEachIndexed { position, element ->
                runs += Run(
                    isBar = position % 2 == 0,
                    modules = if (element == 'w') wideRatio else 1,
                )
            }
            // Inter-character gap: one narrow space, except after the stop code.
            if (index != chars.lastIndex) runs += Run(isBar = false, modules = 1)
        }
        return Symbol(runs, payload)
    }

    /** Exposed for the table's self-check in tests. */
    internal fun patterns(): Map<Char, String> = PATTERNS
}

package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Code39Test {

    /**
     * Every Code 39 character is nine elements with exactly three wide. The
     * alphanumerics carry two wide bars and one wide space; only the four
     * punctuation characters invert that. A typo in the table breaks one of
     * these invariants, which is the point of asserting them.
     */
    @Test
    fun `pattern table satisfies the symbology invariants`() {
        val punctuation = setOf('$', '/', '+', '%')
        Code39.patterns().forEach { (char, pattern) ->
            assertEquals("$char has nine elements", 9, pattern.length)
            assertEquals("$char has three wide elements", 3, pattern.count { it == 'w' })

            val wideBars = pattern.filterIndexed { index, _ -> index % 2 == 0 }.count { it == 'w' }
            val wideSpaces = pattern.filterIndexed { index, _ -> index % 2 == 1 }.count { it == 'w' }
            if (char in punctuation) {
                assertEquals("$char is a punctuation pattern", 0, wideBars)
                assertEquals("$char is a punctuation pattern", 3, wideSpaces)
            } else {
                assertEquals("$char has two wide bars", 2, wideBars)
                assertEquals("$char has one wide space", 1, wideSpaces)
            }
        }
    }

    @Test
    fun `patterns are unique`() {
        val patterns = Code39.patterns().values
        assertEquals(patterns.size, patterns.toSet().size)
    }

    @Test
    fun `symbol is framed by start and stop and alternates bar and space`() {
        val symbol = Code39.encode("042", withCheckCharacter = false)
        // Three characters plus start and stop: five nine-element groups with
        // four one-module gaps between them.
        assertEquals(5 * 9 + 4, symbol.runs.size)
        assertTrue(symbol.runs.first().isBar)
        assertTrue(symbol.runs.last().isBar)
    }

    @Test
    fun `unsupported characters are dropped rather than encoded wrongly`() {
        assertEquals("ABC123", Code39.sanitise("abc*123é"))
    }

    @Test
    fun `check character follows the mod 43 rule`() {
        // "A" is value 10; on its own the check character is also "A".
        assertEquals('A', Code39.checkCharacter("A"))
        // 0 + 1 + 2 = 3 -> "3"
        assertEquals('3', Code39.checkCharacter("012"))
    }

    @Test
    fun `wide ratio scales only the wide elements`() {
        val narrow = Code39.encode("1", withCheckCharacter = false, wideRatio = 2)
        val wide = Code39.encode("1", withCheckCharacter = false, wideRatio = 3)
        assertTrue(wide.totalModules > narrow.totalModules)
        assertEquals(narrow.runs.size, wide.runs.size)
    }

    @Test
    fun `empty input still produces a scannable symbol`() {
        val symbol = Code39.encode("", withCheckCharacter = true)
        assertTrue(symbol.runs.isNotEmpty())
    }
}

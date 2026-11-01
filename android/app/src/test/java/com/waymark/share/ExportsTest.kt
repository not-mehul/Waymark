package com.waymark.share

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportsTest {

    @Test
    fun aSharedFileStaysInsideTheExportsFolder() {
        assertEquals("2026-11-07-london-paris.waymark.json", Exports.sanitize("2026-11-07-london-paris.waymark.json"))
        assertEquals("London___Paris.md", Exports.sanitize("London & Paris.md"))
        assertEquals("_.._etc_passwd", Exports.sanitize("/../etc/passwd"))
        assertEquals("waymark.txt", Exports.sanitize(".."))
    }
}

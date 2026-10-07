package io.github.lilijoyskyseeker.qalc.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFormatTest {
    @Test
    fun lineIsExpressionEqualsResult() {
        assertEquals(
            "5 ft + 30 cm to in = 71.81102362 in",
            HistoryFormat.line(HistoryEntry(0, "5 ft + 30 cm to in", "71.81102362 in")),
        )
    }

    @Test
    fun linesAreJoinedByOneNewlineWithoutTrailing() {
        val entries = listOf(HistoryEntry(0, "1 + 1", "2"), HistoryEntry(1, "2 × 3", "6"))
        assertEquals("1 + 1 = 2\n2 × 3 = 6", HistoryFormat.lines(entries))
    }
}

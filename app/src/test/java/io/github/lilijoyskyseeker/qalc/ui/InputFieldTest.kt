package io.github.lilijoyskyseeker.qalc.ui

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class InputFieldTest {
    @Test
    fun restoredInputPutsCursorAtEnd() {
        // e.g. after rotation or coming back from Settings: typing continues the line
        val field = fieldFor("5 ft + 30 cm")
        assertEquals("5 ft + 30 cm", field.text)
        assertEquals(TextRange(12), field.selection)
    }
}

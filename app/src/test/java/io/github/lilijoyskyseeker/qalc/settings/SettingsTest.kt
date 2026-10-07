package io.github.lilijoyskyseeker.qalc.settings

import io.github.lilijoyskyseeker.qalc.engine.EngineSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun defaultsMapToDesktopDefaults() {
        assertEquals(EngineSettings(1, 1, 10, 0, 3, 0), Settings().toEngine())
    }

    @Test
    fun degreesAndMixedFractions() {
        val e = Settings(angle = AngleUnit.Degrees, fractions = FractionDisplay.Mixed).toEngine()
        assertEquals(2, e.angleUnit)
        assertEquals(3, e.fractionFormat)
    }

    @Test
    fun nearestSixteenthUsesFixedDenominator() {
        val e = Settings(fractions = FractionDisplay.Nearest16).toEngine()
        assertEquals(5, e.fractionFormat)
        assertEquals(16, e.fixedDenominator)
    }

    @Test
    fun precisionIsClampedToSupportedRange() {
        assertEquals(2, Settings(precision = 0).toEngine().precision)
        assertEquals(100, Settings(precision = 500).toEngine().precision)
    }
}

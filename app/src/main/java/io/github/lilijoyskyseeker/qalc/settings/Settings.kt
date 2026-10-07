package io.github.lilijoyskyseeker.qalc.settings

import io.github.lilijoyskyseeker.qalc.engine.EngineSettings

// q values are libqalculate 5.12.0 enum ints (includes.h).

enum class AngleUnit(val q: Int, val label: String) {
    Radians(1, "Radians"), Degrees(2, "Degrees"), Gradians(3, "Gradians"),
}

enum class Approximation(val q: Int, val label: String) {
    Exact(0, "Exact"), TryExact(1, "Try exact"), Approximate(2, "Approximate"),
}

enum class FractionDisplay(val q: Int, val label: String, val denominator: Int = 0) {
    Decimal(0, "Decimal"),
    Fraction(2, "Fraction"),
    Mixed(3, "Mixed"),
    Nearest8(5, "Mixed, nearest 1/8", 8),
    Nearest16(5, "Mixed, nearest 1/16", 16),
    Nearest32(5, "Mixed, nearest 1/32", 32),
}

enum class AutoConversion(val q: Int, val label: String) {
    None(0, "None"), OptimalSi(1, "Optimal SI"), Base(2, "Base units"), Optimal(3, "Optimal"),
}

data class Settings(
    val angle: AngleUnit = AngleUnit.Radians,
    val approximation: Approximation = Approximation.TryExact,
    val precision: Int = 10,
    val fractions: FractionDisplay = FractionDisplay.Decimal,
    val autoConversion: AutoConversion = AutoConversion.Optimal,
) {
    fun toEngine() = EngineSettings(
        angleUnit = angle.q,
        approximation = approximation.q,
        precision = precision.coerceIn(MIN_PRECISION, MAX_PRECISION),
        fractionFormat = fractions.q,
        autoConversion = autoConversion.q,
        fixedDenominator = fractions.denominator,
    )

    companion object {
        const val MIN_PRECISION = 2
        const val MAX_PRECISION = 100
    }
}

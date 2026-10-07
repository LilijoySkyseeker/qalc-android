package io.github.lilijoyskyseeker.qalc.engine

data class CalcResult(
    val text: String,
    val parsed: String,
    val messages: List<String>,
    val ok: Boolean,
    val aborted: Boolean,
) {
    // Called from JNI.
    constructor(text: String, parsed: String, messages: Array<String>, ok: Boolean, aborted: Boolean) :
        this(text, parsed, messages.toList(), ok, aborted)
}

data class RateSource(val url: String, val path: String)

/** Values are libqalculate enum ints; see Settings.toEngine(). */
data class EngineSettings(
    val angleUnit: Int,
    val approximation: Int,
    val precision: Int,
    val fractionFormat: Int,
    val autoConversion: Int,
    val fixedDenominator: Int,
)

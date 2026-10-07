package io.github.lilijoyskyseeker.qalc.engine

/** What the UI needs from the engine; faked in tests. */
interface Calculator {
    suspend fun calculate(expr: String): CalcResult

    /** Aborts the running calculation, if any. Returns immediately. */
    fun abort()

    /** Persists user variables and functions (e.g. from `x := 5`). */
    suspend fun saveDefinitions(): Boolean
}

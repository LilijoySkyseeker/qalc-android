package io.github.lilijoyskyseeker.qalc.engine

import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * libqalculate is one global, not thread-safe object, so every call except
 * abort() runs on a single dedicated thread.
 */
class Engine(private val userDir: File) : Calculator {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "qalc-engine") }.asCoroutineDispatcher()
    private var initialized = false

    private suspend fun <T> onEngine(block: () -> T): T = withContext(thread) {
        if (!initialized) {
            userDir.mkdirs()
            Native.init(userDir.path)
            initialized = true
        }
        block()
    }

    override suspend fun calculate(expr: String): CalcResult = onEngine { Native.calculate(expr, TIMEOUT_MS) }

    override fun abort() = Native.abort()

    suspend fun apply(s: EngineSettings) = onEngine {
        Native.apply(
            intArrayOf(s.angleUnit, s.approximation, s.precision, s.fractionFormat, s.autoConversion, s.fixedDenominator),
        )
    }

    suspend fun saveDefinitions(): Boolean = onEngine { Native.saveDefinitions() }

    suspend fun rateSources(): List<RateSource> = onEngine {
        Native.rateSources().toList().chunked(2) { (url, path) -> RateSource(url, path) }
    }

    suspend fun reloadRates(): Boolean = onEngine { Native.reloadRates() }

    companion object {
        const val TIMEOUT_MS = 2000
    }
}

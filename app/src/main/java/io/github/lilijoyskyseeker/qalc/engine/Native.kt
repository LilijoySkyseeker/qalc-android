package io.github.lilijoyskyseeker.qalc.engine

/** JNI surface of native/bridge/bridge_core.h. Only Engine calls this. */
internal object Native {
    init {
        System.loadLibrary("qalcbridge")
    }

    external fun init(userDir: String)
    external fun calculate(expr: String, timeoutMs: Int): CalcResult
    external fun abort()
    external fun apply(values: IntArray)
    external fun saveDefinitions(): Boolean
    external fun rateSources(): Array<String>
    external fun reloadRates(): Boolean
}

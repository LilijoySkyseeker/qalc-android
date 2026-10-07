package io.github.lilijoyskyseeker.qalc

import android.app.Application
import io.github.lilijoyskyseeker.qalc.engine.Engine
import io.github.lilijoyskyseeker.qalc.history.HistoryStore
import java.io.File

/** Owns the single Engine: libqalculate is one global per process. */
class QalcApp : Application() {
    val engine by lazy { Engine(File(filesDir, "qalculate")) }
    val history by lazy { HistoryStore(File(filesDir, "history.jsonl")) }
}

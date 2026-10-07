package io.github.lilijoyskyseeker.qalc.history

/** Plain-text forms used when copying history. */
object HistoryFormat {
    fun line(e: HistoryEntry): String = "${e.expr} = ${e.result}"

    fun lines(es: List<HistoryEntry>): String = es.joinToString("\n") { line(it) }
}

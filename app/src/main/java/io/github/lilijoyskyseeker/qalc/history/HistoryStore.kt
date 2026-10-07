package io.github.lilijoyskyseeker.qalc.history

import java.io.File
import java.io.FileOutputStream
import org.json.JSONException
import org.json.JSONObject

data class HistoryEntry(val t: Long, val expr: String, val result: String)

/** Append-only history file, one JSON object per line. */
class HistoryStore(private val file: File) {
    fun load(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            try {
                val o = JSONObject(line)
                HistoryEntry(o.getLong("t"), o.getString("expr"), o.getString("result"))
            } catch (e: JSONException) {
                null // a line cut short by the app being killed mid-write
            }
        }
    }

    fun append(e: HistoryEntry) {
        val json = JSONObject().put("t", e.t).put("expr", e.expr).put("result", e.result).toString()
        // Start on a fresh line if a previous write was cut short.
        val prefix = if (file.length() > 0 && !endsWithNewline()) "\n" else ""
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { it.write("$prefix$json\n".toByteArray()) }
    }

    fun clear() {
        file.delete()
    }

    private fun endsWithNewline(): Boolean = file.inputStream().use {
        it.skip(file.length() - 1)
        it.read() == '\n'.code
    }
}

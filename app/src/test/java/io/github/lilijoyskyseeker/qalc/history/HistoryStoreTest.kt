package io.github.lilijoyskyseeker.qalc.history

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStoreTest {
    private val file = File(Files.createTempDirectory("history").toFile(), "history.jsonl")
    private val store = HistoryStore(file)

    @Test
    fun appendedEntriesLoadInOrder() {
        val a = HistoryEntry(1, "5 ft + 30 cm to in", "71.81102362 in")
        val b = HistoryEntry(2, "1 + 1", "2")
        store.append(a)
        store.append(b)
        assertEquals(listOf(a, b), HistoryStore(file).load())
    }

    @Test
    fun specialCharactersRoundTrip() {
        val e = HistoryEntry(3, "\"quoted\"\nline √2 − 1", "0.414 ∕ x")
        store.append(e)
        assertEquals(listOf(e), store.load())
    }

    @Test
    fun truncatedLastLineIsSkipped() {
        val a = HistoryEntry(1, "1 + 1", "2")
        store.append(a)
        file.appendText("{\"t\":1,\"ex")
        assertEquals(listOf(a), store.load())
    }

    @Test
    fun appendAfterTruncatedLineIsKept() {
        file.writeText("{\"t\":1,\"ex")
        val b = HistoryEntry(2, "1 + 1", "2")
        store.append(b)
        assertEquals(listOf(b), store.load())
    }

    @Test
    fun missingFileLoadsEmpty() {
        assertEquals(emptyList<HistoryEntry>(), store.load())
    }

    @Test
    fun clearEmptiesHistory() {
        store.append(HistoryEntry(1, "1 + 1", "2"))
        store.clear()
        assertEquals(emptyList<HistoryEntry>(), store.load())
    }
}

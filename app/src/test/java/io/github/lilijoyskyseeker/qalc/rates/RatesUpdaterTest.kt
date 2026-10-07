package io.github.lilijoyskyseeker.qalc.rates

import io.github.lilijoyskyseeker.qalc.engine.RateSource
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatesUpdaterTest {
    private val dir = Files.createTempDirectory("rates").toFile()
    private val sources = (1..4).map { RateSource("https://example.test/$it", File(dir, "rates$it").path) }
    private val now = 10L * DAY
    private val fetched = mutableListOf<String>()
    private var reloads = 0
    private val failing = mutableSetOf<String>()

    private fun updater() = RatesUpdater(
        sources = { sources },
        reload = { reloads++; true },
        fetch = { url ->
            fetched += url
            if (url in failing) throw IOException("offline")
            "data from $url".toByteArray()
        },
        now = { now },
    )

    private fun writeSource1(age: Long) = File(sources[0].path).apply {
        writeText("old")
        setLastModified(now - age)
    }

    @Test
    fun freshRatesAreNotFetched() = runTest {
        writeSource1(age = DAY / 2)
        assertFalse(updater().updateIfStale())
        assertEquals(emptyList<String>(), fetched)
    }

    @Test
    fun missingRatesFetchAllSourcesAndReloadOnce() = runTest {
        assertTrue(updater().updateIfStale())
        assertEquals(sources.map { it.url }, fetched)
        sources.forEach { assertEquals("data from ${it.url}", File(it.path).readText()) }
        assertEquals(1, reloads)
    }

    @Test
    fun failedSourceKeepsOldFileAndOthersStillUpdate() = runTest {
        val old = writeSource1(age = 2 * DAY).readBytes()
        failing += sources[0].url
        assertTrue(updater().updateIfStale())
        assertArrayEquals(old, File(sources[0].path).readBytes())
        assertFalse(File(sources[0].path + ".tmp").exists())
        assertEquals("data from ${sources[1].url}", File(sources[1].path).readText())
    }

    @Test
    fun allFailingReturnsFalseWithoutReload() = runTest {
        failing += sources.map { it.url }
        assertFalse(updater().updateIfStale())
        assertEquals(0, reloads)
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}

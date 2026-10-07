package io.github.lilijoyskyseeker.qalc.calc

import io.github.lilijoyskyseeker.qalc.engine.CalcResult
import io.github.lilijoyskyseeker.qalc.engine.Calculator
import io.github.lilijoyskyseeker.qalc.history.HistoryEntry
import io.github.lilijoyskyseeker.qalc.history.HistoryStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeCalculator : Calculator {
    val calculated = mutableListOf<String>()
    var aborts = 0
    var saves = 0
    val delays = mutableMapOf<String, Long>()
    val results = mutableMapOf<String, CalcResult>()

    override suspend fun calculate(expr: String): CalcResult {
        calculated += expr
        delays[expr]?.let { delay(it) }
        return results[expr] ?: CalcResult("=$expr", expr, emptyList(), ok = true, aborted = false)
    }

    override fun abort() {
        aborts++
    }

    override suspend fun saveDefinitions(): Boolean {
        saves++
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalcViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val calc = FakeCalculator()
    private val file = File(Files.createTempDirectory("vm").toFile(), "history.jsonl")
    private val store = HistoryStore(file)
    private lateinit var vm: CalcViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = CalcViewModel(calc, store) { 42L }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.type(text: String) {
        vm.onInput(text)
        advanceUntilIdle()
    }

    private fun TestScope.commit(reason: CommitReason) {
        vm.commit(reason)
        advanceUntilIdle()
    }

    @Test
    fun enterOnValidResultAppendsAndClearsInput() = runTest(dispatcher) {
        type("1 + 1")
        commit(CommitReason.Enter)
        assertEquals(listOf(HistoryEntry(42, "1 + 1", "=1 + 1")), vm.state.value.history)
        assertEquals(listOf(HistoryEntry(42, "1 + 1", "=1 + 1")), store.load())
        assertEquals("", vm.state.value.input)
    }

    @Test
    fun errorResultIsNotAppended() = runTest(dispatcher) {
        calc.results["sqrt("] = CalcResult("√()", "", listOf("error"), ok = false, aborted = false)
        type("sqrt(")
        commit(CommitReason.Clear)
        assertEquals(emptyList<HistoryEntry>(), vm.state.value.history)
        assertEquals("", vm.state.value.input)
    }

    @Test
    fun repeatedSameLineIsSavedOnce() = runTest(dispatcher) {
        type("1 + 1")
        commit(CommitReason.Enter)
        type("1 + 1")
        commit(CommitReason.Enter)
        assertEquals(1, vm.state.value.history.size)
    }

    @Test
    fun blankInputCommitsNothing() = runTest(dispatcher) {
        type("  ")
        commit(CommitReason.Enter)
        assertEquals(emptyList<HistoryEntry>(), vm.state.value.history)
        assertTrue(calc.calculated.isEmpty())
    }

    @Test
    fun backgroundAppendsAndKeepsInput() = runTest(dispatcher) {
        type("2 × 3")
        commit(CommitReason.Background)
        assertEquals(1, vm.state.value.history.size)
        assertEquals("2 × 3", vm.state.value.input)
    }

    @Test
    fun assignmentIsNotEvaluatedLiveOnlyOnCommit() = runTest(dispatcher) {
        type("x :=")
        type("x := 5")
        assertTrue(calc.calculated.isEmpty())
        assertNull(vm.state.value.live)
        commit(CommitReason.Enter)
        assertEquals(listOf("x := 5"), calc.calculated)
        assertEquals(1, calc.saves)
    }

    @Test
    fun lateResultForOlderInputIsDiscarded() = runTest(dispatcher) {
        calc.delays["1+"] = 500
        vm.onInput("1+")
        vm.onInput("1+2")
        advanceUntilIdle()
        assertEquals("=1+2", vm.state.value.live?.text)
        assertTrue(calc.aborts >= 1)
    }

    @Test
    fun abortedResultIsNotAppended() = runTest(dispatcher) {
        calc.results["factorial(100000000)"] =
            CalcResult("factorial(100000000)", "", emptyList(), ok = false, aborted = true)
        type("factorial(100000000)")
        commit(CommitReason.Enter)
        assertEquals(emptyList<HistoryEntry>(), vm.state.value.history)
    }

    @Test
    fun clearHistoryEmptiesListAndFile() = runTest(dispatcher) {
        type("1 + 1")
        commit(CommitReason.Enter)
        vm.clearHistory()
        assertEquals(emptyList<HistoryEntry>(), vm.state.value.history)
        assertEquals(emptyList<HistoryEntry>(), store.load())
    }
}

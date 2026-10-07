package io.github.lilijoyskyseeker.qalc.calc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lilijoyskyseeker.qalc.engine.CalcResult
import io.github.lilijoyskyseeker.qalc.engine.Calculator
import io.github.lilijoyskyseeker.qalc.history.HistoryEntry
import io.github.lilijoyskyseeker.qalc.history.HistoryStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommitReason { Enter, Clear, Background }

data class UiState(val input: String, val live: CalcResult?, val history: List<HistoryEntry>)

/**
 * Live evaluation while typing, and the "save when you move on" rule:
 * Enter, clearing the line, or leaving the app commits the line to history
 * if it evaluates successfully and differs from the last entry.
 */
class CalcViewModel(
    private val calc: Calculator,
    private val store: HistoryStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState("", null, store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var liveJob: Job? = null

    fun onInput(text: String) {
        _state.update { it.copy(input = text) }
        liveJob?.cancel()
        calc.abort()
        if (text.isBlank() || hasSideEffects(text)) {
            _state.update { it.copy(live = null) }
            return
        }
        liveJob = viewModelScope.launch {
            val result = calc.calculate(text)
            _state.update { if (it.input == text) it.copy(live = result) else it }
        }
    }

    fun commit(reason: CommitReason) {
        val text = _state.value.input
        if (reason != CommitReason.Background) _state.update { it.copy(input = "", live = null) }
        if (text.isBlank()) return
        liveJob?.cancel()
        viewModelScope.launch {
            val result = calc.calculate(text)
            if (hasSideEffects(text)) calc.saveDefinitions()
            if (!result.ok || result.aborted) return@launch
            val last = _state.value.history.lastOrNull()
            if (last != null && last.expr == text && last.result == result.text) return@launch
            val entry = HistoryEntry(clock(), text, result.text)
            store.append(entry)
            _state.update { it.copy(history = it.history + entry) }
        }
    }

    fun clearHistory() {
        store.clear()
        _state.update { it.copy(history = emptyList()) }
    }

    companion object {
        /** Assignments define variables at every partial step, so they only run on commit. */
        fun hasSideEffects(text: String) = ":=" in text || "save(" in text
    }
}

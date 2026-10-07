package io.github.lilijoyskyseeker.qalc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lilijoyskyseeker.qalc.calc.CalcViewModel
import io.github.lilijoyskyseeker.qalc.calc.CommitReason
import io.github.lilijoyskyseeker.qalc.engine.CalcResult
import io.github.lilijoyskyseeker.qalc.history.HistoryEntry
import io.github.lilijoyskyseeker.qalc.history.HistoryFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: CalcViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var field by remember { mutableStateOf(fieldFor(state.input)) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val selecting = selected.isNotEmpty()

    // The view model clears the input on commit; mirror that into the field.
    LaunchedEffect(state.input) {
        if (field.text != state.input) field = fieldFor(state.input)
    }

    fun edit(value: TextFieldValue) {
        field = value
        if (value.text != state.input) vm.onInput(value.text)
    }

    fun insertAtCursor(text: String) {
        val start = field.selection.min
        val newText = field.text.replaceRange(start, field.selection.max, text)
        edit(TextFieldValue(newText, TextRange(start + text.length)))
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    actions = {
                        TextButton(onClick = {
                            copy(context, HistoryFormat.lines(selected.sorted().map { state.history[it] }))
                            selected = emptySet()
                        }) { Text("Copy") }
                        TextButton(onClick = { selected = emptySet() }) { Text("Cancel") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Qalc") },
                    actions = {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            HistoryList(
                history = state.history,
                selected = selected,
                modifier = Modifier.weight(1f),
                onTap = { i ->
                    if (selecting) selected = selected.toggle(i) else insertAtCursor(state.history[i].result)
                },
                onCopy = { copy(context, it) },
                onSelect = { i -> selected = selected + i },
            )
            HorizontalDivider()
            InputArea(
                field = field,
                live = state.live,
                onChange = ::edit,
                onEnter = { vm.commit(CommitReason.Enter) },
                onClear = { vm.commit(CommitReason.Clear) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(
    history: List<HistoryEntry>,
    selected: Set<Int>,
    modifier: Modifier,
    onTap: (Int) -> Unit,
    onCopy: (String) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.scrollToItem(history.lastIndex)
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        itemsIndexed(history) { i, entry ->
            var menu by remember { mutableStateOf(false) }
            val background =
                if (i in selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(background)
                        .combinedClickable(onClick = { onTap(i) }, onLongClick = { menu = true })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(entry.expr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("= ${entry.result}", style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy result") }, onClick = { onCopy(entry.result); menu = false })
                    DropdownMenuItem(text = { Text("Copy expression") }, onClick = { onCopy(entry.expr); menu = false })
                    DropdownMenuItem(
                        text = { Text("Copy line") },
                        onClick = { onCopy(HistoryFormat.line(entry)); menu = false },
                    )
                    DropdownMenuItem(text = { Text("Select") }, onClick = { onSelect(i); menu = false })
                }
            }
        }
    }
}

@Composable
private fun InputArea(
    field: TextFieldValue,
    live: CalcResult?,
    onChange: (TextFieldValue) -> Unit,
    onEnter: () -> Unit,
    onClear: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        TextField(
            value = field,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            placeholder = { Text("5 ft + 30 cm to in") },
            trailingIcon = {
                if (field.text.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, "Clear") }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onEnter() }),
        )
        LiveResult(live)
    }
}

@Composable
private fun LiveResult(live: CalcResult?) {
    val modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    when {
        live == null -> Text(" ", modifier)
        live.aborted -> Text("taking too long", modifier, color = MaterialTheme.colorScheme.error)
        !live.ok -> Text(live.messages.firstOrNull() ?: "error", modifier, color = MaterialTheme.colorScheme.error)
        else -> Column(modifier) {
            Text("= ${live.text}", style = MaterialTheme.typography.titleLarge)
            live.messages.firstOrNull()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The input as a field with the cursor at the end, ready to keep typing. */
internal fun fieldFor(input: String) = TextFieldValue(input, TextRange(input.length))

private fun Set<Int>.toggle(i: Int) = if (i in this) this - i else this + i

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Qalc", text))
}

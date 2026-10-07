package io.github.lilijoyskyseeker.qalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lilijoyskyseeker.qalc.settings.Approximation
import io.github.lilijoyskyseeker.qalc.settings.AngleUnit
import io.github.lilijoyskyseeker.qalc.settings.AutoConversion
import io.github.lilijoyskyseeker.qalc.settings.FractionDisplay
import io.github.lilijoyskyseeker.qalc.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Choice("Angle unit", settings.angle, AngleUnit.entries, { it.label }) { v -> onChange { it.copy(angle = v) } }
            Choice("Exact or approximate", settings.approximation, Approximation.entries, { it.label }) { v ->
                onChange { it.copy(approximation = v) }
            }
            Choice("Fractions", settings.fractions, FractionDisplay.entries, { it.label }) { v ->
                onChange { it.copy(fractions = v) }
            }
            Choice("Automatic unit conversion", settings.autoConversion, AutoConversion.entries, { it.label }) { v ->
                onChange { it.copy(autoConversion = v) }
            }
            Precision(settings.precision) { v -> onChange { it.copy(precision = v) } }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { confirmClear = true }.padding(16.dp),
            ) { Text("Clear history", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All saved calculations will be deleted.") },
            confirmButton = {
                TextButton(onClick = { onClearHistory(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun <T> Choice(title: String, value: T, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(label(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { onPick(option); open = false })
            }
        }
    }
}

@Composable
private fun Precision(value: Int, onSet: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Precision", style = MaterialTheme.typography.bodyLarge)
            Text("$value significant digits", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = { onSet((value - 1).coerceAtLeast(Settings.MIN_PRECISION)) }) { Text("−") }
        TextButton(onClick = { onSet((value + 1).coerceAtMost(Settings.MAX_PRECISION)) }) { Text("+") }
    }
}

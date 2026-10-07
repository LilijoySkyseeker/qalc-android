package io.github.lilijoyskyseeker.qalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lilijoyskyseeker.qalc.calc.CalcViewModel
import io.github.lilijoyskyseeker.qalc.calc.CommitReason
import io.github.lilijoyskyseeker.qalc.rates.RatesUpdater
import io.github.lilijoyskyseeker.qalc.settings.Settings
import io.github.lilijoyskyseeker.qalc.settings.SettingsStore
import io.github.lilijoyskyseeker.qalc.ui.MainScreen
import io.github.lilijoyskyseeker.qalc.ui.QalcTheme
import io.github.lilijoyskyseeker.qalc.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm by viewModels<CalcViewModel> {
        viewModelFactory {
            initializer {
                val app = application as QalcApp
                CalcViewModel(app.engine, app.history)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = (application as QalcApp).engine
        val settingsStore = SettingsStore(applicationContext)
        lifecycleScope.launch {
            settingsStore.settings.collect {
                engine.apply(it.toEngine())
                vm.onInput(vm.state.value.input) // re-evaluate the live line with the new settings
            }
        }
        lifecycleScope.launch {
            if (RatesUpdater(engine::rateSources, engine::reloadRates).updateIfStale()) {
                vm.onInput(vm.state.value.input) // re-evaluate with fresh rates
            }
        }
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) vm.commit(CommitReason.Background)
            },
        )
        setContent {
            QalcTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                if (showSettings) {
                    BackHandler { showSettings = false }
                    val settings by settingsStore.settings.collectAsStateWithLifecycle(Settings())
                    SettingsScreen(
                        settings = settings,
                        onChange = { f -> scope.launch { settingsStore.update(f) } },
                        onClearHistory = vm::clearHistory,
                        onBack = { showSettings = false },
                    )
                } else {
                    MainScreen(vm, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}

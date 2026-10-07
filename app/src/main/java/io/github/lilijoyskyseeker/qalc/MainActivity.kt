package io.github.lilijoyskyseeker.qalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lilijoyskyseeker.qalc.calc.CalcViewModel
import io.github.lilijoyskyseeker.qalc.calc.CommitReason
import io.github.lilijoyskyseeker.qalc.ui.MainScreen
import io.github.lilijoyskyseeker.qalc.ui.QalcTheme

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
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) vm.commit(CommitReason.Background)
            },
        )
        setContent {
            QalcTheme {
                MainScreen(vm, onOpenSettings = {})
            }
        }
    }
}

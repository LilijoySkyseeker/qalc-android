package io.github.lilijoyskyseeker.qalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.lilijoyskyseeker.qalc.engine.Engine
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = Engine(File(filesDir, "qalculate"))
        setContent {
            MaterialTheme {
                var text by remember { mutableStateOf("…") }
                LaunchedEffect(Unit) { text = engine.calculate("5 ft + 30 cm to in").text }
                Text("5 ft + 30 cm to in = $text")
            }
        }
    }
}

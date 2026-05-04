package dev.bee.kanjianki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.bee.kanjianki.ui.KanjiAnkiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KanjiAnkiApplication).container
        setContent {
            KanjiAnkiApp(container)
        }
    }
}

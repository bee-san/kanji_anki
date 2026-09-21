package dev.bee.kanjianki.compose.jvm.fixture

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun KaniComposeJvmFixture(label: String = "Kani desktop foundation") {
    MaterialTheme {
        Text(label)
    }
}

package dev.bee.kanjianki.desktop.conventions.fixture

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

fun foundationLabel(): String = "Kani desktop foundation"

@Composable
fun DesktopFixture() {
    Text(foundationLabel())
}

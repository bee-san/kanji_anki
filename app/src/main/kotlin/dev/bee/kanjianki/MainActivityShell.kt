package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MainActivityShell(legacyRoot: View) {
    MaterialTheme {
        key(legacyRoot) {
            AndroidView(
                factory = { legacyRoot },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

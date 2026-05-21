package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MainActivityShell(
    legacyRoot: View,
    model: MainActivityShellModel = MainActivityShellModel(),
) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("main-activity-shell")
                .semantics {
                    contentDescription = "Kani shell ${model.selectedRoute}"
                }
        ) {
            key(legacyRoot) {
                AndroidView(
                    factory = { legacyRoot },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

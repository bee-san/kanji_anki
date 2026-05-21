package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ComposeShellModel(
    val title: String,
    val body: String,
    val versionLabel: String,
    val closeLabel: String
)

object ComposeShellPresenter {
    @JvmStatic
    fun create(versionName: String): ComposeShellModel {
        return ComposeShellModel(
            title = "Compose shell",
            body = "Kani now boots through a Kotlin/Compose shell while the remaining parity screens migrate in place.",
            versionLabel = "App version $versionName",
            closeLabel = "Close"
        )
    }
}

class ComposeShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ComposeShellPresenter.create(BuildConfig.VERSION_NAME)
        setTitle(model.title)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ComposeShellScreen(model = model, onClose = ::finish)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun intent(context: Context): Intent {
            return Intent(context, ComposeShellActivity::class.java)
        }
    }
}

@Composable
fun ComposeShellScreen(model: ComposeShellModel, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = model.title, fontWeight = FontWeight.Bold)
        Text(text = model.body)
        Text(text = model.versionLabel)
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Text(text = model.closeLabel)
        }
    }
}

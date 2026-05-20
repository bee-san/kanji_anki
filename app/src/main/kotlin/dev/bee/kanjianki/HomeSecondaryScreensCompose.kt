package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView

data class HomeFocusQueueScreenModel(
    val title: String,
    val homeLabel: String,
    val onHome: () -> Unit,
    val queue: HomeFocusQueuePanelModel,
    val onSync: () -> Unit,
)

data class HomeRecentMistakesScreenModel(
    val title: String,
    val homeLabel: String,
    val onHome: () -> Unit,
    val mistakes: HomeRecentMistakesPanelModel,
)

internal fun homeFocusQueueScreenView(home: MainActivityHome, model: HomeFocusQueueScreenModel): View {
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                HomeFocusQueueScreen(model)
            }
        }
    }
}

internal fun homeRecentMistakesScreenView(home: MainActivityHome, model: HomeRecentMistakesScreenModel): View {
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                HomeRecentMistakesScreen(model)
            }
        }
    }
}

@Composable
fun HomeFocusQueueScreen(model: HomeFocusQueueScreenModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = model.title,
            actionLabel = model.homeLabel,
            onAction = model.onHome
        )
        HomeFocusQueuePanel(model = model.queue, onSync = model.onSync)
    }
}

@Composable
fun HomeRecentMistakesScreen(model: HomeRecentMistakesScreenModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = model.title,
            actionLabel = model.homeLabel,
            onAction = model.onHome
        )
        HomeRecentMistakesPanel(model = model.mistakes)
    }
}

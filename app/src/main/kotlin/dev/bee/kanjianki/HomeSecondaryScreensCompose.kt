package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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

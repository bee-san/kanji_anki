package dev.bee.kanjianki

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

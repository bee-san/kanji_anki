package dev.bee.kanjianki

data class GamesScreenModel(
    val title: String,
    val subtitle: String,
    val emptyTitle: String?,
    val emptyBody: String?,
    val showSyncButton: Boolean,
    val onSync: Runnable,
    val modeCards: List<GamesModeCardModel>,
)

data class GamesModeCardModel(
    val title: String,
    val label: String,
    val body: String,
    val accentColor: Int,
    val available: Boolean,
    val chipLabel: String,
    val onClick: Runnable,
)

data class GamesScoreStripModel(
    val roundLabel: String,
    val roundValue: String,
    val scoreLabel: String,
    val scoreValue: String,
    val streakLabel: String,
    val streakValue: String,
    val scoreDescription: String? = null,
)

data class GamesResultModel(
    val title: String,
    val titleColor: Int,
    val finalScore: String?,
    val accuracy: String?,
    val answer: String?,
    val selectedAnswer: String?,
    val explanation: String?,
    val primaryLabel: String,
    val primaryColor: Int,
    val onPrimary: Runnable,
    val onGames: Runnable,
)

data class GamesUnavailableModel(
    val title: String,
    val body: String,
)

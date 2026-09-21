package dev.bee.kanjianki.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.GamesAccent
import dev.bee.kanjianki.presentation.GamesChoice
import dev.bee.kanjianki.presentation.GamesMenu
import dev.bee.kanjianki.presentation.GamesModeCard
import dev.bee.kanjianki.presentation.GamesResult
import dev.bee.kanjianki.presentation.GamesRound
import dev.bee.kanjianki.presentation.GamesScreen
import dev.bee.kanjianki.presentation.GamesState
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.ui.KaniTheme

internal fun gamesCopy(): GamesCopy = GamesCopy(
    sync = "Sync a collection",
    unavailableTitle = "Games need a collection",
    unavailableBody = "Sync some kanji to unlock games.",
)

internal fun menuScreen(needsSync: Boolean = false, empty: Boolean = false): GamesScreen = GamesScreen(
    state = GamesState.MENU,
    menu = GamesMenu(
        title = "Kanji games",
        subtitle = "A quick warm-up",
        modes = listOf(
            GamesModeCard("meaning_pop", "Meaning Pop", "Kanji → meaning", "Choose the meaning.", GamesAccent.MEANING, available = true, chipLabel = "Play"),
            GamesModeCard("miss_sweep", "Miss Sweep", "Recent misses", "Choose the meaning.", GamesAccent.MISS_SWEEP, available = false, chipLabel = "Need 2+ misses"),
        ),
        needsSync = needsSync,
        emptyTitle = if (empty) "Nothing to play" else null,
        emptyBody = if (empty) "Sync some kanji first." else null,
    ),
)

internal fun roundScreen(): GamesScreen = GamesScreen(
    state = GamesState.ROUND,
    round = GamesRound(
        roundLabel = "Round",
        roundValue = "3/10",
        scoreLabel = "Score",
        scoreValue = "40",
        streakLabel = "Streak",
        streakValue = "3",
        scoreDescription = "Round 3 of 10, score 40, streak 3.",
        modeLabel = "Meaning Pop",
        prompt = "脱",
        promptDetail = "Choose the meaning.",
        choices = listOf(
            GamesChoice("take off", "take off"),
            GamesChoice("explain", "explain"),
            GamesChoice("tax", "tax"),
        ),
        accent = GamesAccent.MEANING,
    ),
)

internal fun resultScreen(correct: Boolean = false): GamesScreen = GamesScreen(
    state = GamesState.RESULT,
    result = GamesResult(
        title = if (correct) "Correct!" else "Round over",
        correct = correct,
        finalScore = "Final score 40",
        accuracy = "80% accuracy",
        answer = if (correct) null else "Answer: take off",
        selected = if (correct) null else "You chose: explain",
        explanation = if (correct) null else "脱 means take off.",
        primaryLabel = "Play again",
        primaryAction = KaniAction.Game.Continue,
    ),
)

private val WINDOW_WIDTH: Dp = 411.dp
private val WINDOW_HEIGHT: Dp = 891.dp

@Composable
private fun FixedWindow(width: Dp, height: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = maxOf(width / maxWidth, height / maxHeight, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density / scale, fontScale = density.fontScale),
        ) {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) { content() }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun renderGames(content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
        block()
    }
}

internal fun SemanticsNodeInteraction.subtreeTextOrEmpty(): String {
    fun collect(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::collect)
    return collect(fetchSemanticsNode()).joinToString(" ")
}

internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()

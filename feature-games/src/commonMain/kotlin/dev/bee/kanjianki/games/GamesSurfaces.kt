package dev.bee.kanjianki.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.GamesAccent
import dev.bee.kanjianki.presentation.GamesChoice
import dev.bee.kanjianki.presentation.GamesMenu
import dev.bee.kanjianki.presentation.GamesModeCard
import dev.bee.kanjianki.presentation.GamesResult
import dev.bee.kanjianki.presentation.GamesRound
import dev.bee.kanjianki.presentation.GamesScreen
import dev.bee.kanjianki.presentation.GamesState
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.ui.KaniColors
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val GAMES_SCREEN_TEST_TAG: String = "kani-games"
const val GAMES_MENU_TEST_TAG: String = "kani-games-menu"
const val GAMES_SYNC_TEST_TAG: String = "kani-games-sync"
const val GAMES_ROUND_TEST_TAG: String = "kani-games-round"
const val GAMES_SCORE_TEST_TAG: String = "kani-games-score"
const val GAMES_RESULT_TEST_TAG: String = "kani-games-result"
const val GAMES_RESULT_PRIMARY_TEST_TAG: String = "kani-games-result-primary"
const val GAMES_UNAVAILABLE_TEST_TAG: String = "kani-games-unavailable"

/** One mode card, tagged by its id. */
fun gamesModeTestTag(id: String): String = "kani-games-mode-$id"

/** One round choice, tagged by its value. */
fun gamesChoiceTestTag(value: String): String = "kani-games-choice-$value"

/**
 * The kanji games surface, from one [GamesScreen].
 *
 * One entry point per host, branching on [GamesScreen.state]: the mode menu, a live
 * round, a result, or an unavailable notice. The engine decides which state this is;
 * the surface only lays it out — the checkable form of "both hosts play the same
 * games from the same engine".
 */
@Composable
fun GamesScreenView(
    screen: GamesScreen,
    copy: GamesCopy,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(GAMES_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (screen.state) {
            GamesState.MENU -> screen.menu?.let { MenuSection(it, copy, dispatch) }
            GamesState.ROUND -> screen.round?.let { RoundSection(it, dispatch) }
            GamesState.RESULT -> screen.result?.let { ResultSection(it, dispatch) }
            GamesState.UNAVAILABLE -> UnavailableSection(copy)
        }
    }
}

@Composable
private fun MenuSection(menu: GamesMenu, copy: GamesCopy, dispatch: (KaniAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GAMES_MENU_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = menu.title,
            modifier = Modifier.semantics { heading() },
            color = KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = menu.subtitle, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        menu.emptyTitle?.let { emptyTitle ->
            Text(text = emptyTitle, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
            menu.emptyBody?.let {
                Text(text = it, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            }
        }
        if (menu.needsSync) {
            Button(
                onClick = { dispatch(KaniAction.Provider.RequestSync) },
                modifier = Modifier.testTag(GAMES_SYNC_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.sync)
            }
        }
        for (mode in menu.modes) {
            ModeCard(mode, dispatch)
        }
    }
}

@Composable
private fun ModeCard(mode: GamesModeCard, dispatch: (KaniAction) -> Unit) {
    val accent = mode.accent.color(KaniTheme.colors)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(gamesModeTestTag(mode.id))
            .semantics { contentDescription = "${mode.title}. ${mode.body}" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, accent.copy(alpha = if (mode.available) CARD_STROKE_ALPHA else DISABLED_ALPHA)),
        enabled = mode.available,
        onClick = { mode.action?.let(dispatch) },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = mode.title, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Bold)
                Text(text = mode.chipLabel, color = accent, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            }
            Text(text = mode.label, color = accent, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp, fontWeight = FontWeight.Medium)
            Text(text = mode.body, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
    }
}

@Composable
private fun RoundSection(round: GamesRound, dispatch: (KaniAction) -> Unit) {
    val accent = round.accent.color(KaniTheme.colors)
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GAMES_ROUND_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(GAMES_SCORE_TEST_TAG)
                .semantics(mergeDescendants = true) { contentDescription = round.scoreDescription },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScoreCard(round.roundLabel, round.roundValue, KaniTheme.colors.blue, Modifier.weight(1f))
            ScoreCard(round.scoreLabel, round.scoreValue, KaniTheme.colors.coral, Modifier.weight(1f))
            ScoreCard(round.streakLabel, round.streakValue, KaniTheme.colors.teal, Modifier.weight(1f))
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = PROMPT_MIN_HEIGHT),
            shape = KaniUiTokens.StudyShapeLarge,
            color = KaniTheme.colors.panelSoft,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = round.modeLabel, color = accent, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = round.prompt,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyQuestionTextSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (round.promptDetail.isNotBlank()) {
                    Text(text = round.promptDetail, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
                }
            }
        }
        for (choice in round.choices) {
            ChoiceButton(choice, accent, round.choicesAreKanji, dispatch)
        }
    }
}

@Composable
private fun ScoreCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = KaniUiTokens.StudyShapeSmall,
        color = accent.copy(alpha = SCORE_FILL_ALPHA),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = value, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
    }
}

@Composable
private fun ChoiceButton(choice: GamesChoice, accent: Color, kanji: Boolean, dispatch: (KaniAction) -> Unit) {
    OutlinedButton(
        onClick = { dispatch(choice.action) },
        modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT).testTag(gamesChoiceTestTag(choice.value)),
        shape = KaniUiTokens.ButtonShape,
        border = BorderStroke(1.dp, accent.copy(alpha = CARD_STROKE_ALPHA)),
    ) {
        Text(
            text = choice.label,
            color = KaniTheme.colors.ink,
            fontSize = if (kanji) KaniUiTokens.StudyHeadingTextSizeSp.sp else KaniUiTokens.StudyActionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ResultSection(result: GamesResult, dispatch: (KaniAction) -> Unit) {
    val accent = if (result.correct) KaniTheme.colors.teal else KaniTheme.colors.coral
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(GAMES_RESULT_TEST_TAG),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = result.title, color = accent, fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp, fontWeight = FontWeight.Bold)
            for (line in listOfNotNull(result.finalScore, result.accuracy, result.answer, result.selected, result.explanation)) {
                Text(text = line, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            }
            Button(
                onClick = { dispatch(result.primaryAction) },
                modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT).testTag(GAMES_RESULT_PRIMARY_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = result.primaryLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UnavailableSection(copy: GamesCopy) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GAMES_UNAVAILABLE_TEST_TAG)
            .semantics { contentDescription = "${copy.unavailableTitle}. ${copy.unavailableBody}" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = copy.unavailableTitle, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
            Text(text = copy.unavailableBody, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
    }
}

/** The live theme colour for a games accent, mapped the way the model must not know. */
internal fun GamesAccent.color(colors: KaniColors): Color = when (this) {
    GamesAccent.MEANING -> colors.blue
    GamesAccent.READING -> colors.teal
    GamesAccent.CONFUSABLE -> colors.coral
    GamesAccent.MISS_SWEEP -> colors.primary
}

private const val CARD_STROKE_ALPHA = 0.5f
private const val DISABLED_ALPHA = 0.2f
private const val SCORE_FILL_ALPHA = 0.14f
private val ACTION_MIN_HEIGHT = 54.dp
private val PROMPT_MIN_HEIGHT = 140.dp

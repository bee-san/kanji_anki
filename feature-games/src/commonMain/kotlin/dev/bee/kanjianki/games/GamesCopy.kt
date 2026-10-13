package dev.bee.kanjianki.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.games.generated.resources.Res
import dev.bee.kanjianki.feature.games.generated.resources.games_sync
import dev.bee.kanjianki.feature.games.generated.resources.games_unavailable_body
import dev.bee.kanjianki.feature.games.generated.resources.games_unavailable_title
import org.jetbrains.compose.resources.stringResource

/**
 * The games shell's structural labels.
 *
 * Small, like the other feature copies: mode titles, prompts, and result wording are
 * host-computed by `KanjiGameEngine`/`KanjiGameCopy` and arrive on the portable model.
 * Only the sync button and the unavailable notice are the shell's own.
 */
data class GamesCopy(
    val sync: String,
    val unavailableTitle: String,
    val unavailableBody: String,
)

/** Resolves [GamesCopy] from this module's resources. */
@Composable
fun rememberGamesCopy(): GamesCopy {
    val sync = stringResource(Res.string.games_sync)
    val unavailableTitle = stringResource(Res.string.games_unavailable_title)
    val unavailableBody = stringResource(Res.string.games_unavailable_body)
    return remember(sync, unavailableTitle, unavailableBody) {
        GamesCopy(sync = sync, unavailableTitle = unavailableTitle, unavailableBody = unavailableBody)
    }
}

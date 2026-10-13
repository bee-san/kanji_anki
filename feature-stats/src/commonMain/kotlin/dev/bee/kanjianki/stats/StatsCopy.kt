package dev.bee.kanjianki.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.stats.generated.resources.Res
import dev.bee.kanjianki.feature.stats.generated.resources.stats_empty_body
import dev.bee.kanjianki.feature.stats.generated.resources.stats_empty_title
import dev.bee.kanjianki.feature.stats.generated.resources.stats_range_30
import dev.bee.kanjianki.feature.stats.generated.resources.stats_range_7
import dev.bee.kanjianki.feature.stats.generated.resources.stats_range_90
import dev.bee.kanjianki.presentation.StatsRange
import org.jetbrains.compose.resources.stringResource

/**
 * The stats dashboard's structural labels.
 *
 * Small on purpose, like the study session's: the analytics prose is host-computed
 * and arrives on the [dev.bee.kanjianki.presentation.StatsDashboard] model. Only the
 * range chips and the empty state — chrome the dashboard shell adds itself — are here.
 */
data class StatsCopy(
    val emptyTitle: String,
    val emptyBody: String,
    private val rangeLabels: Map<StatsRange, String>,
) {
    fun rangeLabel(range: StatsRange): String = rangeLabels.getValue(range)
}

/** Resolves [StatsCopy] from this module's resources. */
@Composable
fun rememberStatsCopy(): StatsCopy {
    val rangeLabels = mapOf(
        StatsRange.SEVEN_DAYS to stringResource(Res.string.stats_range_7),
        StatsRange.THIRTY_DAYS to stringResource(Res.string.stats_range_30),
        StatsRange.NINETY_DAYS to stringResource(Res.string.stats_range_90),
    )
    val emptyTitle = stringResource(Res.string.stats_empty_title)
    val emptyBody = stringResource(Res.string.stats_empty_body)
    return remember(rangeLabels, emptyTitle, emptyBody) {
        StatsCopy(emptyTitle = emptyTitle, emptyBody = emptyBody, rangeLabels = rangeLabels)
    }
}

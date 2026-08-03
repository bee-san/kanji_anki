package dev.bee.kanjianki.missing

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
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.presentation.MissingKanjiDestinations
import dev.bee.kanjianki.presentation.MissingKanjiProvider
import dev.bee.kanjianki.presentation.MissingKanjiRow
import dev.bee.kanjianki.presentation.MissingKanjiScreen
import dev.bee.kanjianki.ui.KaniTheme

internal fun missingCopy(): MissingKanjiCopy = MissingKanjiCopy(
    firstRunTitle = "Find missing kanji",
    firstRunBody = "Scan your collection.",
    providerMissingTitle = "No collection",
    providerMissingBody = "Connect one.",
    permissionTitle = "Permission needed",
    permissionBody = "Grant access.",
    errorTitle = "Scan failed",
    scanning = "Scanning…",
    cancel = "Cancel",
    selectAll = "Select all",
    clear = "Clear",
    addToKani = "Add to Kani",
    createAnki = "Create in Anki",
    exportCsv = "Export CSV",
    dismiss = "Done",
    selectionTemplate = "%1\$d selected",
)

internal fun reportScreen(
    destinations: MissingKanjiDestinations = MissingKanjiDestinations(
        addToKaniEnabled = true,
        createAnkiEnabled = true,
        csvExportEnabled = true,
        defaultDeckName = "Kani::Missing Kanji",
    ),
): MissingKanjiScreen = MissingKanjiScreen(
    content = MissingKanjiContent.Report(
        summaryLine = "Scanned 400 notes",
        missingCountLine = "12 missing kanji",
        rows = listOf(
            MissingKanjiRow("脱", "take off", "だつ", rankLine = "#900"),
            MissingKanjiRow("説", "explain", "せつ", rankLine = "#1200"),
            MissingKanjiRow("税", "tax", "ぜい", inKani = true, canRemove = true),
        ),
    ),
    providerAvailability = MissingKanjiProvider.READY,
    primaryActionLabel = "Scan again",
    primaryAction = KaniAction.MissingKanji.ScanIntent,
    destinations = destinations,
)

internal fun stateScreen(content: MissingKanjiContent, label: String = "Scan"): MissingKanjiScreen =
    MissingKanjiScreen(
        content = content,
        providerAvailability = MissingKanjiProvider.READY,
        primaryActionLabel = label,
        primaryAction = KaniAction.MissingKanji.ScanIntent,
    )

private val WIDTH: Dp = 411.dp
private val HEIGHT: Dp = 891.dp

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
internal fun renderMissing(content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = WIDTH, height = HEIGHT) {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
        block()
    }
}

internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()

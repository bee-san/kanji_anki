package dev.bee.kanjianki.missing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.ui.KaniUiTokens
import kotlin.test.assertTrue

/**
 * That every control on the report is large enough to hit.
 *
 * The report is a bulk-selection screen: the whole point is picking rows and then acting
 * on the picks, so a checkbox or a select-all that is hard to hit is not a cosmetic
 * problem — it is the screen not working. Material's `Checkbox` and `TextButton` both
 * default under [KaniUiTokens.MinTouchTarget], which is what these measure against.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryMissingKanjiControlIsBigEnoughToHit() {
    renderMissing(content = { MissingKanjiScreenView(reportScreen(), missingCopy(), dispatch = {}) }) {
        val tags = listOf(
            MISSING_SELECT_ALL_TEST_TAG,
            MISSING_CLEAR_TEST_TAG,
            MISSING_ADD_TEST_TAG,
            MISSING_CREATE_ANKI_TEST_TAG,
            MISSING_EXPORT_CSV_TEST_TAG,
            missingRowSelectTestTag("脱"),
            missingRowSelectTestTag("説"),
        )
        for (tag in tags) {
            assertTargetIsBigEnough(tag)
        }
    }
}

/**
 * That the controls the report does not show are targets too.
 *
 * Each screen state renders a disjoint set of controls, so a report-only sweep leaves
 * the rest untested — which is how the primary button kept Material's 40dp default while
 * every control beside it on the report had been floored. Cancel is the other half: it
 * exists only while a scan runs, and it is what a user reaches for when the scan is
 * taking longer than they expected, so a missed click there is the worst-timed one.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertTheOffReportControlsAreBigEnoughToHit() {
    renderMissing(
        content = {
            val scanning = MissingKanjiContent.Scanning(
                notesScanned = 120,
                uniqueKanji = 44,
                skippedNotes = 2,
                cancelling = false,
            )
            MissingKanjiScreenView(stateScreen(scanning), missingCopy(), dispatch = {})
        },
    ) {
        assertTargetIsBigEnough(MISSING_CANCEL_TEST_TAG)
    }
    // The primary lives on the four state panels — first run, provider missing,
    // permission, error — and not on the report, so it needs its own render.
    renderMissing(
        content = {
            MissingKanjiScreenView(stateScreen(MissingKanjiContent.FirstRun), missingCopy(), dispatch = {})
        },
    ) {
        assertTargetIsBigEnough(MISSING_PRIMARY_TEST_TAG)
    }
}

/**
 * Asserts the node at [tag] is at least [KaniUiTokens.MinTouchTarget] tall.
 *
 * Measured from the laid-out node rather than read off the modifier chain: a `heightIn`
 * that is present but sits inside a `Row` whose own height is fixed produces no taller
 * node, and source-reading would not notice. The 1dp slack absorbs the density division
 * the fixed-window harness performs, so a logical 44dp measuring 43.999 does not fail.
 *
 * No scroll-into-view first: the bounds of a laid-out node are the same whether or not it
 * is on screen, and requiring a scrollable ancestor would make the check depend on how
 * each module's harness wraps its content rather than on the control's own size.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTargetIsBigEnough(tag: String) {
    val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$tag did not render, so its size is untested")
    val bounds = onAllNodesWithTag(tag)[0].getBoundsInRoot()
    val height = bounds.bottom - bounds.top
    assertTrue(
        height.value + TARGET_SLACK >= KaniUiTokens.MinTouchTarget.value,
        "$tag is $height tall, under the ${KaniUiTokens.MinTouchTarget} target",
    )
}

private const val TARGET_SLACK: Float = 1f

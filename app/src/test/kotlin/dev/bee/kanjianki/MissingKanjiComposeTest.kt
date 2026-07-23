package dev.bee.kanjianki

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissingKanjiComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFirstRunPermissionErrorAndScanningStates() {
        var cancelClicked = false
        val progress = MissingKanjiScanProgressState().apply {
            update(notesScanned = 125, uniqueKanjiCount = 84, skippedNotes = 2)
        }
        val model = mutableStateOf(screenModel(MissingKanjiContentModel.FirstRun))
        composeRule.setContent {
            MissingKanjiScreen(
                model.value.copy(onCancelScan = { cancelClicked = true }),
            )
        }

        composeRule.onNodeWithText(MissingKanjiTextCopy.firstRunTitle()).assertIsDisplayed()

        composeRule.runOnIdle {
            model.value = screenModel(MissingKanjiContentModel.PermissionRequired)
        }
        composeRule.onNodeWithText(MissingKanjiTextCopy.permissionTitle()).assertIsDisplayed()

        composeRule.runOnIdle {
            model.value = screenModel(
                MissingKanjiContentModel.Error("provider_unavailable"),
                primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
            )
        }
        composeRule.onNodeWithText(MissingKanjiTextCopy.scanErrorTitle("provider_unavailable"))
            .assertIsDisplayed()

        composeRule.runOnIdle {
            model.value = screenModel(MissingKanjiContentModel.Scanning(progress))
        }
        composeRule.onNodeWithText(
            MissingKanjiTextCopy.scanningProgress(125, 84),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSING_KANJI_CANCEL_TAG)
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(cancelClicked)
        }
    }

    @Test
    fun rendersMissingProviderAndBothEmptyReportStates() {
        val model = mutableStateOf(
            screenModel(
                MissingKanjiContentModel.AnkiDroidMissing,
                availability = MissingKanjiProviderAvailability.NOT_INSTALLED,
                primaryAction = MissingKanjiPrimaryAction.INSTALL_ANKIDROID,
            ),
        )
        composeRule.setContent {
            MissingKanjiScreen(model.value)
        }

        composeRule.onNodeWithText(MissingKanjiTextCopy.ankiDroidMissingTitle())
            .assertIsDisplayed()

        composeRule.runOnIdle {
            model.value = screenModel(
                MissingKanjiContentModel.Report(
                    report(rows = emptyList(), eligible = 0, key = "empty-range"),
                ),
                primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
            )
        }
        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(5)
        composeRule.onNodeWithText(MissingKanjiTextCopy.noEligibleTitle()).assertIsDisplayed()

        composeRule.runOnIdle {
            model.value = screenModel(
                MissingKanjiContentModel.Report(
                    report(rows = emptyList(), eligible = 10, key = "full-coverage"),
                ),
                primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
            )
        }
        composeRule.onNodeWithText(MissingKanjiTextCopy.noneMissingTitle()).assertIsDisplayed()
    }

    @Test
    fun fiveThousandRowsStayLazyAndSearchSelectionDetailsRemainStable() {
        val rows = missingKanjiRows(
            (1..5_000).map { rank ->
                MissingKanjiCandidate(
                    literal = literal(rank),
                    meanings = listOf(if (rank == 4_999) "needle target" else "meaning $rank"),
                    onReadings = listOf("reading-$rank"),
                    jitenRank = rank,
                )
            },
        )
        val target = rows[4_998]
        composeRule.setContent {
            MainActivityComposeRoute(
                scrollMode = MainActivityRouteScrollMode.CONTENT,
            ) {
                MissingKanjiScreen(
                    screenModel(
                        MissingKanjiContentModel.Report(
                            report(rows = rows, eligible = rows.size, key = "large"),
                        ),
                        primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
                    ),
                )
            }
        }

        composeRule.onAllNodesWithTag(missingKanjiRowTag(rows.last().literal))
            .assertCountEquals(0)
        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(5)
        composeRule.onNodeWithTag(MISSING_KANJI_SEARCH_TAG)
            .performTextReplacement("needle 4999")
        composeRule.onNodeWithText(
            MissingKanjiTextCopy.visibleResultCount(1, 5_000),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(7)
        composeRule.onNodeWithTag(missingKanjiRowTag(target.literal)).assertIsDisplayed()

        composeRule.onNodeWithTag(missingKanjiCheckboxTag(target.literal)).performClick()
        composeRule.onAllNodesWithText(MissingKanjiTextCopy.selectedCount(1))
            .fetchSemanticsNodes()
            .also { nodes -> assertTrue(nodes.isNotEmpty()) }
        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(6)
        composeRule.onNodeWithTag(MISSING_KANJI_ADD_TO_KANI_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(MISSING_KANJI_CREATE_ANKI_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(7)
        composeRule.onNodeWithContentDescription(
            MissingKanjiTextCopy.rowDescription(
                literal = target.literal,
                meaning = target.primaryMeaning,
                reading = target.primaryReading,
                rank = target.jitenRank,
                selected = true,
            ),
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(missingKanjiRowTag(target.literal)).performClick()
        composeRule.onNodeWithText(MissingKanjiTextCopy.detailsTitle(target.literal))
            .assertIsDisplayed()
        composeRule.onNodeWithText(MissingKanjiTextCopy.closeLabel()).performClick()

        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(5)
        composeRule.onNodeWithTag(MISSING_KANJI_SEARCH_TAG).performTextReplacement("")
        composeRule.onAllNodesWithText(MissingKanjiTextCopy.selectedCount(1))
            .fetchSemanticsNodes()
            .also { nodes -> assertTrue(nodes.isNotEmpty()) }
    }

    @Test
    fun selectAllAppliesOnlyToVisibleFilteredRows() {
        val rows = missingKanjiRows(
            listOf(
                candidate("語", "language", 301),
                candidate("話", "talk language", 302),
                candidate("海", "sea", 303),
            ),
        )
        composeRule.setContent {
            MissingKanjiScreen(
                screenModel(
                    MissingKanjiContentModel.Report(
                        report(rows = rows, eligible = rows.size, key = "filtered-selection"),
                    ),
                    primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
                ),
            )
        }

        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(5)
        composeRule.onNodeWithTag(MISSING_KANJI_SEARCH_TAG)
            .performTextReplacement("language")
        composeRule.onNodeWithTag(MISSING_KANJI_SELECT_VISIBLE_TAG).performClick()

        composeRule.onAllNodesWithText(MissingKanjiTextCopy.selectedCount(2))
            .fetchSemanticsNodes()
            .also { nodes -> assertTrue(nodes.isNotEmpty()) }
        composeRule.onNodeWithText(MissingKanjiTextCopy.clearVisibleLabel(2))
            .assertIsDisplayed()
    }

    @Test
    fun customRangeBlocksInvertedBoundsPreviewsValidCountAndApplies() {
        var applied: Pair<MissingKanjiPreset, MissingKanjiFrequencyRange>? = null
        val model = screenModel(MissingKanjiContentModel.FirstRun).copy(
            onRangePreview = { _, callback -> callback(1_234) },
            onRangeApplied = { preset, range -> applied = preset to range },
        )
        composeRule.setContent {
            MissingKanjiScreen(model)
        }

        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(3)
        composeRule.onNodeWithTag(missingKanjiPresetTag(MissingKanjiPreset.CUSTOM))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(MISSING_KANJI_MINIMUM_RANK_TAG)
            .performTextReplacement("2000")
        composeRule.onNodeWithTag(MISSING_KANJI_MAXIMUM_RANK_TAG)
            .performTextReplacement("1000")
        composeRule.onNodeWithText(MissingKanjiTextCopy.invalidRangeMessage("inverted"))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MISSING_KANJI_APPLY_RANGE_TAG)
            .performScrollTo()
            .assertIsNotEnabled()

        composeRule.onNodeWithTag(MISSING_KANJI_MAXIMUM_RANK_TAG)
            .performTextReplacement("3000")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(
                MissingKanjiTextCopy.expectedEligibleCount(1_234),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(MISSING_KANJI_APPLY_RANGE_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                MissingKanjiPreset.CUSTOM to MissingKanjiFrequencyRange(2_000, 3_000),
                applied,
            )
        }
    }

    @Test
    fun staleReportShowsWarningAndRowsExposeTalkBackSelectionLabels() {
        val row = missingKanjiRows(listOf(candidate("語", "language", 301))).single()
        composeRule.setContent {
            MissingKanjiScreen(
                screenModel(
                    MissingKanjiContentModel.Report(
                        report(
                            rows = listOf(row),
                            eligible = 10,
                            key = "stale",
                            staleReason = MissingKanjiStaleReason.FAILED,
                        ),
                    ),
                    primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
                ),
            )
        }

        composeRule.onNodeWithText(
            MissingKanjiTextCopy.staleResultsLabel(MissingKanjiStaleReason.FAILED.copyKey),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(7)
        composeRule.onNodeWithContentDescription(
            MissingKanjiTextCopy.selectionDescription("語"),
        ).assertIsDisplayed()
    }

    @Test
    fun addToKaniRequiresConfirmationAndReportsDailyAdmission() {
        val row = missingKanjiRows(listOf(candidate("語", "language", 301))).single()
        var added: Set<String> = emptySet()
        composeRule.setContent {
            MissingKanjiScreen(
                screenModel(
                    MissingKanjiContentModel.Report(
                        report(rows = listOf(row), eligible = 1, key = "add-to-kani"),
                    ),
                    primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
                ).copy(
                    destinations = MissingKanjiDestinationModel(
                        addToKaniEnabled = true,
                        newPerDay = 3,
                        onAddToKani = { literals -> added = literals },
                    ),
                ),
            )
        }

        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(6)
        composeRule.onNodeWithTag(missingKanjiCheckboxTag("語")).performClick()
        composeRule.onNodeWithTag(MISSING_KANJI_ADD_TO_KANI_TAG)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText(MissingKanjiTextCopy.addToKaniConfirmationTitle())
            .assertIsDisplayed()
        composeRule.onNodeWithText(MissingKanjiTextCopy.addToKaniConfirmationBody(1, 3))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MISSING_KANJI_CONFIRM_ADD_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(setOf("語"), added)
        }
    }

    @Test
    fun removableManualSourceIsMarkedAndConfirmedFromDetails() {
        val row = missingKanjiRows(
            candidates = listOf(candidate("語", "language", 301)),
            activeManualLiterals = setOf("語"),
            removableManualLiterals = setOf("語"),
        ).single()
        var removed = ""
        composeRule.setContent {
            MissingKanjiScreen(
                screenModel(
                    MissingKanjiContentModel.Report(
                        report(rows = listOf(row), eligible = 1, key = "remove-from-kani"),
                    ),
                    primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
                ).copy(
                    destinations = MissingKanjiDestinationModel(
                        addToKaniEnabled = true,
                        onRemoveFromKani = { literal -> removed = literal },
                    ),
                ),
            )
        }

        composeRule.onNodeWithTag(MISSING_KANJI_LIST_TAG).performScrollToIndex(6)
        composeRule.onNodeWithText(MissingKanjiTextCopy.inKaniLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(missingKanjiRowTag("語")).performClick()
        composeRule.onNodeWithTag(MISSING_KANJI_REMOVE_TAG).performClick()
        composeRule.onNodeWithText(MissingKanjiTextCopy.removeFromKaniConfirmationTitle("語"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(MissingKanjiTextCopy.removeFromKaniLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals("語", removed)
        }
    }

    @Test
    fun admissionResultOffersStudyOnlyWhenAnItemWasAdmitted() {
        var studyNow = false
        composeRule.setContent {
            MissingKanjiScreen(
                screenModel(MissingKanjiContentModel.FirstRun).copy(
                    operationResult = MissingKanjiOperationResultModel.KaniAdmission(
                        requestedCount = 4,
                        addedCount = 3,
                        alreadyInKaniCount = 1,
                        skippedMissingMeaningCount = 0,
                        skippedMissingReadingCount = 0,
                        invalidCount = 0,
                        admittedNowCount = 2,
                        deferredCount = 1,
                    ),
                    onStudyNow = { studyNow = true },
                ),
            )
        }

        composeRule.onNodeWithText(MissingKanjiTextCopy.kaniAdmissionResultTitle())
            .assertIsDisplayed()
        composeRule.onNodeWithText(MissingKanjiTextCopy.studyNowLabel()).performClick()
        composeRule.runOnIdle {
            assertTrue(studyNow)
        }
    }

    private fun screenModel(
        content: MissingKanjiContentModel,
        availability: MissingKanjiProviderAvailability = MissingKanjiProviderAvailability.READY,
        primaryAction: MissingKanjiPrimaryAction = MissingKanjiPrimaryAction.SCAN,
    ): MissingKanjiScreenModel = MissingKanjiScreenModel(
        content = content,
        providerAvailability = availability,
        frequency = MissingKanjiFrequencyModel(
            preset = MissingKanjiPreset.TOP_2000,
            range = MissingKanjiFrequencyRange.TOP_2000,
            searchQuery = "",
        ),
        primaryAction = primaryAction,
        onHome = {},
        onPrimaryAction = {},
        onCancelScan = {},
        onRangeApplied = { _, _ -> },
        onRangePreview = { _, callback -> callback(2_000) },
        onSearchQueryChanged = {},
    )

    private fun report(
        rows: List<MissingKanjiRowModel>,
        eligible: Int,
        key: String,
        staleReason: MissingKanjiStaleReason? = null,
    ): MissingKanjiReportUiModel = MissingKanjiReportUiModel(
        reportKey = key,
        scan = MissingKanjiScanSummaryModel(
            scanId = key.hashCode().toLong(),
            completedAtMillis = 1_784_795_436_000L,
            notesScanned = 100,
            uniqueAnkiKanjiCount = 80,
            skippedNotes = 0,
        ),
        eligibleDictionaryKanjiCount = eligible,
        missingKanjiCount = rows.size,
        rows = rows,
        staleReason = staleReason,
    )

    private fun candidate(
        literal: String,
        meaning: String,
        rank: Int,
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = listOf(meaning),
        onReadings = listOf("オン"),
        kunReadings = listOf("くん"),
        jitenRank = rank,
    )

    private fun literal(index: Int): String = String(Character.toChars(0x4E00 + index))
}

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class MainActivityStudyFlashcardComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setStudyAnswerPanelContent(
        model: StudyAnswerPanelModel,
        onAnkiTapAction: ((StudyAnswerAnkiTapActionModel) -> Unit)? = null,
    ) {
        composeRule.setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                StudyAnswerPanel(
                    model = model,
                    onAnkiTapAction = onAnkiTapAction,
                )
            }
        }
    }

    @Test
    fun rendersRevealButtonAndInvokesAction() {
        var revealed = false

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = false,
                onReveal = { revealed = true },
                onFail = {},
                onPass = {}
            )
        }

        composeRule.onNodeWithText(StudyReviewButtonCopy.revealLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.revealLabel()))
            .assertIsDisplayed()
            .performClick()

        assertTrue(revealed)
    }

    @Test
    fun rendersAgainAndGoodButtonsAndInvokesActions() {
        var failed = false
        var passed = false

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = { failed = true },
                onPass = { passed = true }
            )
        }

        composeRule.onNodeWithText(StudyReviewButtonCopy.againLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyReviewButtonCopy.goodLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.againLabel()))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.goodLabel()))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription(StudyReviewButtonCopy.againContentDescription()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(StudyReviewButtonCopy.goodContentDescription()).assertIsDisplayed()

        assertTrue(failed)
        assertTrue(passed)
    }

    @Test
    fun rendersRecognitionPill() {
        composeRule.setContent {
            RecognitionPill("Recognise")
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
    }

    @Test
    fun hidesFlashcardPromptHeaderReason() {
        composeRule.setContent {
            FlashcardPromptHeader(
                model = FlashcardPromptHeaderModel(
                    modeLabel = "Recognise",
                    title = "What does this kanji mean?",
                    question = "Recall the meaning",
                    hiddenHint = "Answer hidden until reveal",
                    reasonLine = "Weak Anki evidence"
                )
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onNodeWithText("What does this kanji mean?").assertIsDisplayed()
        composeRule.onNodeWithText("Recall the meaning").assertIsDisplayed()
        composeRule.onNodeWithText("Answer hidden until reveal").assertIsDisplayed()
        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
    }

    @Test
    fun keepsFlashcardPromptHeaderCleanWhenReasonEmpty() {
        composeRule.setContent {
            FlashcardPromptHeader(
                model = FlashcardPromptHeaderModel(
                    modeLabel = "Recognise",
                    title = "What does this kanji mean?",
                    question = "Recall the meaning",
                    hiddenHint = "Answer hidden until reveal",
                    reasonLine = ""
                )
            )
        }

        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
    }

    @Test
    fun rendersFlashcardHeroPanel() {
        composeRule.setContent {
            FlashcardHeroPanel(
                model = FlashcardHeroPanelModel(
                    glyph = "裂",
                    glyphSizeSp = 116,
                    typeface = null
                )
            )
        }

        composeRule.onNodeWithText("裂").assertIsDisplayed()
    }

    @Test
    fun rendersFlashcardCardShell() {
        val revealState = FlashcardRevealState(false)
        composeRule.setContent {
            FlashcardCard(
                model = FlashcardCardModel(
                    promptHeader = FlashcardPromptHeaderModel(
                        modeLabel = "Recognise",
                        title = "Name this kanji",
                        question = "What does this kanji mean?",
                        hiddenHint = "Answer hidden until reveal",
                        reasonLine = ""
                    ),
                    heroPanel = FlashcardHeroPanelModel(
                        glyph = "裂",
                        glyphSizeSp = 116,
                        typeface = null
                    ),
                    typingAnswer = null,
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "裂",
                        glyphSizeSp = 76,
                        lines = listOf(StudyAnswerLineModel("split", MainActivityUiSupport.STUDY_PLUM, 17, true)),
                        helperText = null
                    ),
                    revealState = revealState
                )
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onNodeWithText("Name this kanji").assertIsDisplayed()
        composeRule.onNodeWithText("What does this kanji mean?").assertIsDisplayed()
        composeRule.onAllNodesWithText("split").assertCountEquals(0)

        composeRule.runOnIdle {
            revealState.reveal()
        }

        composeRule.onNodeWithText("split").assertIsDisplayed()
    }

    @Test
    fun rendersTypingMeaningAnswerWithComposeInput() {
        var stateRef: TypingAnswerState? = null

        composeRule.setContent {
            val state = remember { TypingAnswerState("split") }
            stateRef = state
            TypingMeaningAnswer(label = MainActivityBase.LABEL_MEANING, state = state)
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_MEANING).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("split")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("split open")
        composeRule.runOnIdle {
            assertNotNull(stateRef)
            assertEquals("split open", stateRef?.getText().toString())
        }
    }

    @Test
    fun rendersStudyAnswerPanel() {
        composeRule.setContent {
            StudyAnswerPanel(
                model = StudyAnswerPanelModel(
                    title = "Answer",
                    glyph = "裂",
                    glyphSizeSp = 76,
                    lines = listOf(
                        StudyAnswerLineModel("split", MainActivityUiSupport.STUDY_PLUM, 17, true),
                        StudyAnswerLineModel("Reading: レツ", MainActivityUiSupport.STUDY_PINK_DARK, 15, true)
                    ),
                    helperText = "Trace it below, then check."
                )
            )
        }

        composeRule.onNodeWithText("Answer").assertIsDisplayed()
        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("Reading: レツ").assertIsDisplayed()
        composeRule.onNodeWithText("Trace it below, then check.").assertIsDisplayed()
    }

    @Test
    fun expandsOnlyOneStudyAnswerAccordionAtATime() {
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    breakdownComponentRows = listOf("left component", "right component"),
                ),
            ),
        )

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Details")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(1)

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Breakdown")).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Components").assertCountEquals(1)
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(0)

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Stroke order")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Stroke count").assertCountEquals(1)
        composeRule.onAllNodesWithText("Components").assertCountEquals(0)
    }

    @Test
    fun usedInAnkiShowAllToggleExpandsRows() {
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    examples = sampleUsedInAnkiExamples(),
                    currentExample = sampleUsedInAnkiExamples().first(),
                    openAnkiDroidSupported = false,
                ),
            ),
        )

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("甲").assertCountEquals(1)
        composeRule.onAllNodesWithText("丁").assertCountEquals(1)
        composeRule.onAllNodesWithText("丙").assertCountEquals(1)
        composeRule.onAllNodesWithText("乙").assertCountEquals(0)

        composeRule.onNodeWithTag(studyAnswerUsedInAnkiToggleTestTag()).performScrollTo().assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("乙").assertCountEquals(1)
        composeRule.onAllNodesWithText("Show fewer").assertCountEquals(1)
    }

    @Test
    fun emptyStudyAnswerAccordionsShowFriendlyCopy() {
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = studyAnswerKanjiDetailsModel(
                    kanji = "裂",
                    dictionaryEntry = null,
                    examples = emptyList(),
                    currentExample = null,
                    openAnkiDroidSupported = false,
                    deckNamesByCardId = emptyMap(),
                    modelNamesByNoteId = emptyMap(),
                    strokeOrderAssetAvailable = false,
                    strokeOrderAssetReference = null,
                    breakdownComponentRows = emptyList(),
                ),
            ),
        )

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Details")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kani couldn't find local details for this kanji yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Review still works; this drawer can fill in after dictionary data syncs.").assertIsDisplayed()

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No other synced Anki words yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Sync more cards and Kani will connect them here.").assertIsDisplayed()
    }

    @Test
    fun usedInAnkiRowTapReturnsCopyFallbackAction() {
        val examples = sampleUsedInAnkiExamples()
        var tappedAction: StudyAnswerAnkiTapActionModel? = null

        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    examples = examples,
                    currentExample = examples.first(),
                    openAnkiDroidSupported = false,
                ),
            ),
            onAnkiTapAction = { tappedAction = it },
        )

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki")).performClick()
        composeRule.onNodeWithTag(studyAnswerUsedInAnkiRowTestTag(0)).performClick()

        assertNotNull(tappedAction)
        when (val action = tappedAction) {
            is StudyAnswerAnkiTapActionModel.CopyId -> assertEquals(101L, action.value)
            else -> throw AssertionError("Expected CopyId, got $action")
        }
    }

    @Test
    fun capturesStudyAnswerDropdownScreenshots() {
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    breakdownComponentRows = listOf("left component", "right component"),
                ),
            ),
        )
        composeRule.waitForIdle()
        captureStudyScreenshot("01-rich-collapsed.png")

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Details")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        captureStudyScreenshot("02-rich-details-expanded.png")

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        captureStudyScreenshot("03-rich-used-in-anki-collapsed.png")

        composeRule.onNodeWithText("Show all 4").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        captureStudyScreenshot("04-rich-used-in-anki-expanded.png")
    }

    @Test
    fun capturesStudyAnswerDropdownFallbackBanner() {
        val examples = sampleUsedInAnkiExamples()

        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    examples = examples,
                    currentExample = examples.first(),
                    openAnkiDroidSupported = false,
                ),
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerUsedInAnkiRowTestTag(0)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Anki link unavailable — copied note ID.").assertIsDisplayed()
        captureStudyScreenshot("05-fallback-banner.png")
    }

    @Test
    fun capturesStudyAnswerDropdownEmptyState() {
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    examples = emptyList(),
                    currentExample = null,
                    openAnkiDroidSupported = false,
                ),
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No other synced Anki words yet.").assertIsDisplayed()
        captureStudyScreenshot("06-empty-used-in-anki.png")
    }
    private fun captureStudyScreenshot(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotDir = File(context.getExternalFilesDir(null), "study-answer-compose-screenshots")
        screenshotDir.mkdirs()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(screenshotDir, fileName)).use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}

private fun sampleStudyAnswerPanelModel(details: StudyAnswerKanjiDetailsModel): StudyAnswerPanelModel {
    return StudyAnswerPanelModel(
        title = "Answer",
        glyph = "裂",
        glyphSizeSp = 76,
        lines = listOf(
            StudyAnswerLineModel("split", MainActivityUiSupport.STUDY_PLUM, 17, true),
            StudyAnswerLineModel("Reading: レツ", MainActivityUiSupport.STUDY_PINK_DARK, 15, true),
        ),
        helperText = "Trace it below, then check.",
        stateKey = "study-answer-test",
        kanjiDetails = details,
    )
}

private fun sampleStudyAnswerDetails(
    examples: List<RecordsImportModels.Example> = sampleUsedInAnkiExamples(),
    currentExample: RecordsImportModels.Example? = examples.firstOrNull(),
    openAnkiDroidSupported: Boolean = false,
    breakdownComponentRows: List<String> = emptyList(),
): StudyAnswerKanjiDetailsModel {
    return studyAnswerKanjiDetailsModel(
        kanji = "裂",
        dictionaryEntry = sampleDictionaryEntry(),
        examples = examples,
        currentExample = currentExample,
        showAllUsedInAnki = false,
        openAnkiDroidSupported = openAnkiDroidSupported,
        deckNamesByCardId = mapOf(
            201L to "Core Deck",
            202L to "Study Deck",
        ),
        modelNamesByNoteId = mapOf(
            101L to "Basic Model",
            102L to "Reading Model",
        ),
        strokeOrderAssetAvailable = false,
        strokeOrderAssetReference = null,
        breakdownComponentRows = breakdownComponentRows,
    )
}

private fun sampleDictionaryEntry(): DictionaryLookup.KanjiEntry {
    return DictionaryLookup.KanjiEntry(
        DictionaryLookup.KanjiEntryFields(
            literal = "裂",
            meanings = listOf("tear apart", "separate"),
            onReadings = listOf("レツ"),
            kunReadings = listOf("さ.ける"),
            nanoriReadings = listOf("さけ"),
            strokeCount = 12,
            grade = 6,
            radical = 129,
            kanjidicFrequency = 321,
            jitenRank = 14,
        ),
    )
}

private fun sampleUsedInAnkiExamples(): List<RecordsImportModels.Example> {
    return listOf(
        RecordsImportModels.Example("anki", 201L, 101L, "甲", "こう", "first", "", false, 0),
        RecordsImportModels.Example("anki", 202L, 102L, "乙", "おつ", "second", "", false, 0),
        RecordsImportModels.Example("anki", 203L, 103L, "丙", "へい", "third", "", false, 0),
        RecordsImportModels.Example("anki", 204L, 104L, "丁", "てい", "fourth", "", false, 0),
    )
}

package dev.bee.kanjianki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
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
import dev.bee.kanjianki.core.StudyTextCopy
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
        onBrowseAction: Runnable? = null,
        initialExpandedSectionLabel: String? = null,
    ) {
        composeRule.setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                StudyAnswerPanel(
                    model = model,
                    onAnkiTapAction = onAnkiTapAction,
                    onBrowseAction = onBrowseAction,
                    initialExpandedSectionLabel = initialExpandedSectionLabel,
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
    fun rendersSharedStudyModeChip() {
        composeRule.setContent {
            StudyModeChip("Recognise")
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
    }

    @Test
    fun rendersFlashcardPromptHeaderWithModeAndQuestionOnly() {
        composeRule.setContent {
            FlashcardPromptHeader(
                model = FlashcardPromptHeaderModel(
                    modeLabel = "Recognise",
                    question = "Recall the meaning",
                )
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onNodeWithText("Recall the meaning").assertIsDisplayed()
        composeRule.onAllNodesWithText("What does this kanji mean?").assertCountEquals(0)
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
        composeRule.onAllNodesWithText("Weak Anki evidence").assertCountEquals(0)
    }

    @Test
    fun answeredFlashcardPromptHeaderHidesQuestionButKeepsMode() {
        composeRule.setContent {
            FlashcardPromptHeader(
                model = FlashcardPromptHeaderModel(
                    modeLabel = "Recognise",
                    question = "Recall the meaning",
                ),
                showQuestion = false,
            )
        }

        composeRule.onNodeWithText("Recognise").assertIsDisplayed()
        composeRule.onAllNodesWithText("Recall the meaning").assertCountEquals(0)
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
                        question = "What does this kanji mean?",
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
        composeRule.onAllNodesWithText("Name this kanji").assertCountEquals(0)
        composeRule.onNodeWithText("What does this kanji mean?").assertIsDisplayed()
        composeRule.onAllNodesWithText("split").assertCountEquals(0)

        composeRule.runOnIdle {
            revealState.reveal()
        }

        composeRule.onAllNodesWithText("What does this kanji mean?").assertCountEquals(0)
        composeRule.onAllNodesWithText("裂").assertCountEquals(1)
        composeRule.onNodeWithText("split").assertExists()
    }

    @Test
    fun rendersTypingMeaningAnswerWithComposeInput() {
        var stateRef: TypingAnswerState? = null

        composeRule.setContent {
            val state = remember { TypingAnswerState("split") }
            stateRef = state
            TypingMeaningAnswer(label = MainActivityBase.LABEL_MEANING, state = state)
        }

        composeRule.onAllNodesWithText(MainActivityBase.LABEL_MEANING).assertCountEquals(0)
        composeRule.onNode(hasSetTextAction()).assertTextEquals("split")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("split open")
        composeRule.runOnIdle {
            assertNotNull(stateRef)
            assertEquals("split open", stateRef?.getText().toString())
        }
    }

    @Test
    fun rendersWritingReferenceAnswerPanel() {
        composeRule.setContent {
            StudyAnswerPanel(
                model = StudyAnswerPanelModel(
                    title = "Reference",
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

        composeRule.onNodeWithText("Reference").assertIsDisplayed()
        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("Reading: レツ").assertIsDisplayed()
        composeRule.onNodeWithText("Trace it below, then check.").assertIsDisplayed()
    }

    @Test
    fun flashcardAnswerContentOmitsAnswerTitleAndDuplicateGlyph() {
        composeRule.setContent {
            StudyFlashcardAnswerContent(
                model = StudyAnswerPanelModel(
                    title = "Answer",
                    glyph = "裂",
                    glyphSizeSp = 76,
                    lines = listOf(
                        StudyAnswerLineModel("split", MainActivityUiSupport.STUDY_PLUM, 17, true),
                        StudyAnswerLineModel("Reading: レツ", MainActivityUiSupport.STUDY_PINK_DARK, 15, true),
                    ),
                    helperText = null,
                ),
            )
        }

        composeRule.onAllNodesWithText("Answer").assertCountEquals(0)
        composeRule.onAllNodesWithText("裂").assertCountEquals(0)
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("Reading: レツ").assertIsDisplayed()
    }

    @Test
    fun collapsesReadyAnswerDetailsIntoOneDisclosureWithBrowseInside() {
        var browseClicks = 0
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    breakdownComponentRows = listOf("left component", "right component"),
                ),
            ),
            onBrowseAction = Runnable { browseClicks++ },
        )

        composeRule.onNodeWithText(StudyTextCopy.moreAboutKanjiLabel("裂")).assertIsDisplayed()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.openInBrowseLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithText("View kanji details").assertCountEquals(0)

        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Details"))
            .assertExists()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Breakdown"))
            .assertExists()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Stroke order"))
            .assertExists()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .assertExists()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Why this card?"))
            .assertExists()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerComponentsHeading()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerStrokeCountLabel()).assertCountEquals(0)

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Details")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(1)

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Breakdown")).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerComponentsHeading()).assertCountEquals(1)

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Stroke order")).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerComponentsHeading()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerStrokeCountLabel()).assertCountEquals(1)
        composeRule.onNodeWithText(StudyTextCopy.openInBrowseLabel())
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, browseClicks) }
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

        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
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
        composeRule.onAllNodesWithText(StudyTextCopy.showFewerLabel()).assertCountEquals(1)
    }

    @Test
    fun disclosureOmitsEmptyAndUnavailableSections() {
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

        composeRule.onAllNodesWithTag(studyAnswerDisclosureHeaderTestTag()).assertCountEquals(0)
        composeRule.onAllNodesWithTag(studyAnswerAccordionHeaderTestTag("Details"))
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag(studyAnswerAccordionHeaderTestTag("Breakdown"))
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag(studyAnswerAccordionHeaderTestTag("Stroke order"))
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag(studyAnswerAccordionHeaderTestTag("Why this card?"))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerDetailsEmptyTitle()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerUsedInAnkiEmptyTitle()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerWhyThisCardEmptyBody()).assertCountEquals(0)
    }

    @Test
    fun initialExpandedSectionSelectsOneInnerAccordion() {
        setStudyAnswerPanelContent(
            model = sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(
                    breakdownComponentRows = listOf("left component", "right component"),
                ),
            ),
            initialExpandedSectionLabel = StudyTextCopy.studyAnswerBreakdownLabel(),
        )
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(StudyTextCopy.studyAnswerComponentsHeading())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerStrokeCountLabel()).assertCountEquals(0)
    }

    @Test
    fun disclosureExpansionIsKeyedToTheAnswerPanel() {
        val modelState = mutableStateOf(
            sampleStudyAnswerPanelModel(
                details = sampleStudyAnswerDetails(),
            ),
        )
        composeRule.setContent {
            StudyAnswerPanel(model = modelState.value)
        }

        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Details")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("tear apart").assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = modelState.value.copy(
                stateKey = "study-answer-test-next",
                kanjiDetails = requireNotNull(modelState.value.kanjiDetails).copy(kanji = "別"),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(StudyTextCopy.moreAboutKanjiLabel("別")).assertIsDisplayed()
        composeRule.onAllNodesWithText("tear apart").assertCountEquals(0)
    }

    @Test
    fun usedInAnkiRowTapCopiesIdShowsFeedbackAndReturnsFallbackAction() {
        val examples = sampleUsedInAnkiExamples()
        var tappedAction: StudyAnswerAnkiTapActionModel? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "before"))

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

        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(studyAnswerUsedInAnkiRowTestTag(0)).performClick()
        composeRule.waitForIdle()

        assertNotNull(tappedAction)
        when (val action = tappedAction) {
            is StudyAnswerAnkiTapActionModel.CopyId -> assertEquals(101L, action.value)
            else -> throw AssertionError("Expected CopyId, got $action")
        }
        var copiedText: String? = null
        composeRule.runOnIdle {
            copiedText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }
        assertEquals("101", copiedText)
        composeRule.onNodeWithText(StudyTextCopy.studyAnswerAnkiNoteIdCopiedMessage()).assertIsDisplayed()
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

        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        captureStudyScreenshot("02-rich-more-expanded.png")

        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(StudyTextCopy.showAllLabel(4)).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        captureStudyScreenshot("03-rich-used-in-anki-expanded.png")
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
        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerAccordionHeaderTestTag("Used in Anki"))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyAnswerUsedInAnkiRowTestTag(0)).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(StudyTextCopy.studyAnswerAnkiNoteIdCopiedMessage()).assertIsDisplayed()
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
        composeRule.onNodeWithTag(studyAnswerDisclosureHeaderTestTag()).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(StudyTextCopy.studyAnswerUsedInAnkiEmptyTitle()).assertCountEquals(0)
        captureStudyScreenshot("06-empty-sections-omitted.png")
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

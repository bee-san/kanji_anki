package dev.bee.kanjianki.core.study

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingFeedbackCopyTest {
    @Test
    fun guideLabelPreservesHintStageCopy() {
        val emptyGuide = StrokeGuide("裂", emptyList())
        val guide = guide()

        assertEquals(
            "Help level: Blind\nWrite from memory; stroke-order feedback will be limited.",
            WritingFeedbackCopy.guideLabel(3, emptyGuide)
        )
        assertEquals(
            "Help level: Blind\nWrite from memory; stroke-order feedback will be limited.",
            WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), emptyGuide)
        )
        assertEquals(
            "Help level: Trace\nDraw it, then check. Stroke-order feedback will be limited.",
            WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), emptyGuide)
        )
        assertEquals("Help level: Trace\nTrace the strokes, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide))
        assertEquals("Help level: Outline\nCopy the faint outline; the current stroke is emphasized.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(1), guide))
        assertEquals("Help level: Minimal\nWrite with only the current stroke hinted, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(2), guide))
        assertEquals("Help level: Blind\nWrite from memory, then check. Use Hint if you are stuck.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), guide))
        assertEquals("Help level: Trace\nTrace the strokes, then check.", WritingFeedbackCopy.guideLabel(null, guide))
    }

    @Test
    fun stageLabelPreservesShortNames() {
        assertEquals("Trace", WritingFeedbackCopy.stageLabel(HintLevel.TRACE))
        assertEquals("Outline", WritingFeedbackCopy.stageLabel(HintLevel.OUTLINE))
        assertEquals("Minimal", WritingFeedbackCopy.stageLabel(HintLevel.MINIMAL))
        assertEquals("Blind", WritingFeedbackCopy.stageLabel(HintLevel.BLIND))
    }

    @Test
    fun attemptProgressTextPreservesHintProgressMessages() {
        assertEquals("", WritingFeedbackCopy.attemptProgressText(null, 3, true))
        assertEquals(
            "\nNext writing review will have less help: Minimal.",
            WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 0), null, false)
        )
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.OUTLINE, 1), null, false))
        assertEquals(
            "\nTry cleaner for a cleaner pass, or Save hard to keep this help level.",
            WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), 3, false)
        )
        assertEquals(
            "\nNext try will use more support: Minimal.",
            WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), 3, true)
        )
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), 3, false))
        assertEquals("", WritingFeedbackCopy.attemptProgressText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), null, true))
    }

    @Test
    fun recognitionErrorsDoNotAdvertiseHintRegression() {
        val analysis = WritingAnalysisEngine.recognitionError(HintLevel.BLIND, 0)
        val shouldIncreaseSupport = WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis)

        assertFalse(shouldIncreaseSupport)
        assertEquals(
            "The handwriting checker could not read this attempt. Try once more.\nTarget: 裂",
            WritingFeedbackCopy.resultMessage(analysis, "裂", 3, shouldIncreaseSupport, null)
        )
    }

    @Test
    fun targetRevealTextPreservesTerminalStatusCopy() {
        assertEquals("", WritingFeedbackCopy.targetRevealText(null, "裂"))
        assertEquals("", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), null))
        assertEquals("", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0), "裂"))
    }

    @Test
    fun candidateTextShowsTopThreeRecognizedValues() {
        assertEquals("", WritingFeedbackCopy.candidateText(null))
        assertEquals("", WritingFeedbackCopy.candidateText(emptyList()))
        assertEquals(
            "拉, 拡, 抽",
            WritingFeedbackCopy.candidateText(
                listOf(
                    RecognitionCandidate("拉", 0.9f),
                    RecognitionCandidate("拡", 0.7f),
                    RecognitionCandidate("抽", 0.5f),
                    RecognitionCandidate("扌", 0.3f),
                )
            )
        )
    }

    @Test
    fun resultMessageCombinesProgressTargetCandidatesAndDiagnosis() {
        val analysis = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "Try again.",
            listOf(RecognitionCandidate("拉", 0.9f), RecognitionCandidate("拡", 0.7f)),
            null,
            HintLevel.BLIND,
            0,
        )

        assertEquals(
            "Try again.\nNext try will use more support: Minimal.\nTarget: 裂\nIt saw: 拉, 拡\nStroke 1: too short",
            WritingFeedbackCopy.resultMessage(analysis, "裂", 3, true, "Stroke 1: too short")
        )
    }

    @Test
    fun resultMessageHandlesNullAndEmptyOptionalText() {
        assertEquals("", WritingFeedbackCopy.resultMessage(null, "裂", 3, true, "diagnosis"))
        assertEquals(
            "NO_INK",
            WritingFeedbackCopy.resultMessage(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), "裂", null, false, null)
        )
    }

    @Test
    fun writingActionCopyPreservesButtonAndRatingChoices() {
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, false))
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, true))
        assertEquals("Check", WritingFeedbackCopy.checkWritingButtonText(false, false))
        assertEquals("Try cleaner", WritingFeedbackCopy.checkWritingButtonText(false, true))

        assertEquals("Fail", WritingFeedbackCopy.submitLabel(null))
        assertEquals("Fail", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertEquals("Save hard", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertEquals("Pass", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))

        assertEquals("again", WritingFeedbackCopy.submitRating(null))
        assertEquals("again", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertEquals("hard", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertEquals("good", WritingFeedbackCopy.submitRating(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
    }

    @Test
    fun modelStatusCopyPreservesGuidePrefixedMessages() {
        assertEquals(
            "Guide\nAutomatic handwriting checks are unavailable on this device.",
            WritingFeedbackCopy.unavailableModelStatusMessage("Guide")
        )
        assertEquals(
            "Guide\nUnable to read handwriting checker status.",
            WritingFeedbackCopy.modelStatusMessage("Guide", false, false, false)
        )
        assertEquals(
            "Guide\nUnable to read handwriting checker status.",
            WritingFeedbackCopy.modelStatusMessage("Guide", true, true, true)
        )
        assertEquals(
            "Guide\nDownload the handwriting checker before automatic checks.",
            WritingFeedbackCopy.modelStatusMessage("Guide", true, false, false)
        )
        assertEquals(
            "Guide\nHandwriting checker ready.",
            WritingFeedbackCopy.modelStatusMessage("Guide", true, true, false)
        )
        assertEquals(
            "Handwriting checker ready.",
            WritingFeedbackCopy.modelStatusMessage("", true, true, false)
        )
    }

    @Test
    fun writingStatusCopyPreservesGuidePrefixedActionMessages() {
        assertEquals(
            "Guide\nHint used. One current stroke hinted; your ink stayed on the canvas.",
            WritingFeedbackCopy.hintUsedStatus("Guide")
        )
        assertEquals(
            "Guide\nFresh guided try. Draw it again, then check.",
            WritingFeedbackCopy.freshGuidedTryStatus("Guide")
        )
        assertEquals(
            "Guide\nTry cleaner. Keep the same help level and draw it carefully once more.",
            WritingFeedbackCopy.cleanerRetryStatus("Guide")
        )
        assertEquals(
            "Guide\nUndid the last stroke.",
            WritingFeedbackCopy.undoStrokeStatus("Guide")
        )
        assertEquals(
            "Guide\nUpdated ink. Check again when ready.",
            WritingFeedbackCopy.updatedInkStatus("Guide")
        )
        assertEquals(
            "Updated ink. Check again when ready.",
            WritingFeedbackCopy.updatedInkStatus("")
        )
    }

    @Test
    fun checkerDownloadStatusPreservesGuidePrefixedHintStage() {
        assertEquals(
            "Guide\nDownloading handwriting checker...",
            WritingFeedbackCopy.checkerDownloadStatus("Guide")
        )
        assertEquals(
            "Guide\nHandwriting checker download failed: boom",
            WritingFeedbackCopy.checkerDownloadFailedStatus("Guide", "boom")
        )
        assertEquals(
            "Handwriting checker download failed: an unknown error",
            WritingFeedbackCopy.checkerDownloadFailedStatus("", "")
        )
    }

    @Test
    fun blockedStrokeStatusUsesDecisionMessageOrFallback() {
        assertEquals(
            "Guide\nStay close to the guide.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", null)
        )
        assertEquals(
            "Guide\nStay close to the guide.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", StrokeGuideGuard.Decision.rejected(1, ""))
        )
        assertEquals(
            "Guide\nStay close to stroke 1.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", StrokeGuideGuard.Decision.rejected(1, "Stay close to stroke 1."))
        )
    }

    @Test
    fun japaneseLocaleLocalizesWritingFeedbackCopy() = withDefaultLocale(Locale.JAPANESE) {
        assertEquals("ヒント段階: なぞる\nなぞってから確認しましょう。", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide()))
        assertEquals("最小ヒント", WritingFeedbackCopy.stageLabel(HintLevel.MINIMAL))
        assertEquals("\nお題: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("確認中...", WritingFeedbackCopy.checkWritingButtonText(true, false))
        assertEquals("チェッカーをダウンロード", WritingFeedbackCopy.downloadCheckerLabel())
        assertEquals(
            "手本\n手書き判定器をダウンロードしています...",
            WritingFeedbackCopy.checkerDownloadStatus("手本")
        )
        assertEquals(
            "手本\n手書き判定器のダウンロードに失敗しました: 不明なエラー",
            WritingFeedbackCopy.checkerDownloadFailedStatus("手本", "")
        )
        assertEquals("ヒント", WritingFeedbackCopy.hintButtonText(3))
        assertEquals("もっとヒント", WritingFeedbackCopy.hintButtonText(1))
        assertEquals("不合格", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertEquals("合格", WritingFeedbackCopy.submitLabel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
        assertEquals(
            "手本\n手書き判定の準備ができています。",
            WritingFeedbackCopy.modelStatusMessage("手本", true, true, false)
        )
        assertEquals(
            "手本\n1画目に近づけて書いてください。",
            WritingFeedbackCopy.blockedStrokeStatus("手本", StrokeGuideGuard.Decision.rejected(1, "Stay close to stroke 1."))
        )
    }

    @Test
    fun japaneseLocaleLocalizesCombinedResultMessage() = withDefaultLocale(Locale.JAPANESE) {
        val analysis = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "I could not read that as the target kanji yet.",
            listOf(RecognitionCandidate("拉", 0.9f), RecognitionCandidate("拡", 0.7f)),
            null,
            HintLevel.BLIND,
            0,
        )

        assertEquals(
            "まだお題の漢字として読み取れません。" +
                "\n次の挑戦はヒントを増やします: 最小ヒント。" +
                "\nお題: 裂" +
                "\n認識候補: 拉, 拡" +
                "\n1画目: 順番が違うかもしれません" +
                "\n認識できましたが、線が乱れています",
            WritingFeedbackCopy.resultMessage(
                analysis,
                "裂",
                3,
                true,
                "Stroke 1: likely wrong order\nRecognized, but the stroke path was messy"
            )
        )
    }

    @Test
    fun japaneseLocaleLocalizesKnownAnalysisMessages() = withDefaultLocale(Locale.JAPANESE) {
        val localizedMessages = listOf(
            "Write in the square before checking." to "確認する前に枠の中に書いてください。",
            "The handwriting checker is unavailable on this device." to "この端末では手書き判定を使えません。",
            "Automatic handwriting checks are unavailable on this device." to "この端末では自動手書き判定を使えません。",
            "Download the handwriting checker before automatic checks." to "自動判定の前に手書き判定をダウンロードしてください。",
            "I could not read that as the target kanji yet." to "まだお題の漢字として読み取れません。",
            "The handwriting checker could not read this attempt. Try once more." to
                "手書き判定がこの入力を読み取れませんでした。もう一度試してください。",
            "Readable, but the stroke path needs one more careful pass." to
                "読めますが、線をもう少し丁寧に書くとよくなります。",
            "Clean match." to "きれいに一致しました。",
            "Matched the kanji. Keep tightening the stroke path." to
                "漢字は一致しました。線をさらに整えていきましょう。",
            "Recognized as the target kanji. Stroke order could not be checked because no guide is bundled yet." to
                "お題の漢字として認識しました。筆順ガイドがまだ含まれていないため、筆順は確認できませんでした。",
            "Recognized as the target kanji, but stroke order could not be checked because no guide is bundled yet." to
                "お題の漢字として認識しましたが、筆順ガイドがまだ含まれていないため筆順は確認できませんでした。",
            "No stroke-order guide is available for this kanji." to "この漢字の筆順ガイドはまだありません。",
            "No stroke-order guide is available for this kanji. I could not read that as the target kanji yet." to
                "この漢字の筆順ガイドはまだありません。まだお題の漢字として読み取れません。",
            "No ink was drawn." to "まだ何も書かれていません。",
            "Stroke path looks clean." to "線はきれいです。",
            "Readable path, but some strokes look shaky." to "読める線ですが、一部の画が不安定です。",
            "The stroke count or order does not match the guide yet." to
                "画数または筆順がまだガイドと一致していません。",
        )

        for ((english, japanese) in localizedMessages) {
            assertEquals(japanese, WritingFeedbackCopy.resultMessage(analysisWithMessage(english), null, null, false, null))
        }
        assertEquals(
            "Unmapped analyzer note.",
            WritingFeedbackCopy.resultMessage(analysisWithMessage("Unmapped analyzer note."), null, null, false, null)
        )
    }

    @Test
    fun japaneseLocaleLocalizesModelUnavailableResultMessages() = withDefaultLocale(Locale.JAPANESE) {
        assertEquals(
            "この端末では手書き判定を使えません。\nお題: 裂",
            WritingFeedbackCopy.resultMessage(
                WritingAnalysisEngine.modelUnavailable("The handwriting checker is unavailable on this device."),
                "裂",
                null,
                false,
                null
            )
        )
        assertEquals(
            "自動判定の前に手書き判定をダウンロードしてください。\nお題: 裂",
            WritingFeedbackCopy.resultMessage(
                WritingAnalysisEngine.modelUnavailable("Download the handwriting checker before automatic checks."),
                "裂",
                null,
                false,
                null
            )
        )
    }

    @Test
    fun japaneseLocaleLocalizesDiagnosisVariants() = withDefaultLocale(Locale.JAPANESE) {
        assertEquals(
            "きれいに一致しました。" +
                "\n1画目: 向きが違うかもしれません" +
                "\n2画目: 抜けているかもしれません" +
                "\n3画目: 余分な一画かもしれません" +
                "\n4画目: 部品のバランスや形を見直しましょう" +
                "\n5画目: 手本から離れすぎています" +
                "\n別の漢字に見えます。似ている部分を比べましょう" +
                "\n合格範囲ですが、線が乱れています" +
                "\nStroke 6: unknown label" +
                "\nUnmapped diagnosis",
            WritingFeedbackCopy.resultMessage(
                analysisWithMessage("Clean match."),
                null,
                null,
                false,
                "Stroke 1: likely wrong direction" +
                    "\nStroke 2: may be missing" +
                    "\nStroke 3: may be extra" +
                    "\nStroke 4: component proportion or shape looks rough" +
                    "\nStroke 5: too far from the guide" +
                    "\nIt looked like a different kanji; compare the similar parts" +
                    "\nGood enough, but the stroke path was messy" +
                    "\nStroke 6: unknown label" +
                    "\nUnmapped diagnosis"
            )
        )
    }

    @Test
    fun japaneseLocaleLocalizesBlockedStrokeFallbacks() = withDefaultLocale(Locale.JAPANESE) {
        assertEquals("手本\n手本に沿って書いてください。", WritingFeedbackCopy.blockedStrokeStatus("手本", null))
        assertEquals(
            "手本\nガイドの全ての画はすでに書かれています。",
            WritingFeedbackCopy.blockedStrokeStatus(
                "手本",
                StrokeGuideGuard.Decision.rejected(1, "All guided strokes are already drawn.")
            )
        )
        assertEquals(
            "手本\nCustom guard copy.",
            WritingFeedbackCopy.blockedStrokeStatus("手本", StrokeGuideGuard.Decision.rejected(1, "Custom guard copy."))
        )
    }

    @Test
    fun nonJapaneseLocaleKeepsEnglishWritingFeedbackCopy() = withDefaultLocale(Locale.CANADA) {
        assertEquals("Help level: Trace\nTrace the strokes, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide()))
        assertEquals("Minimal", WritingFeedbackCopy.stageLabel(HintLevel.MINIMAL))
        assertEquals("\nTarget: 裂", WritingFeedbackCopy.targetRevealText(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), "裂"))
        assertEquals("Checking...", WritingFeedbackCopy.checkWritingButtonText(true, false))
        assertEquals(
            "Guide\nStay close to stroke 1.",
            WritingFeedbackCopy.blockedStrokeStatus("Guide", StrokeGuideGuard.Decision.rejected(1, "Stay close to stroke 1."))
        )
    }

    @Test
    fun writingActionPolicyPreservesSubmittableAndFallbackStatuses() {
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(null))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canSubmitAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)))

        assertFalse(WritingFeedbackCopy.canManualOverride(null))
        assertFalse(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.canManualOverride(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)))

        assertFalse(WritingFeedbackCopy.canPracticeAfterAnalysis(null))
        assertTrue(WritingFeedbackCopy.canPracticeAfterAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(null))
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.CLOSE, true, HintLevel.BLIND, 0)))
        assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.NO_STROKE_DATA, false, HintLevel.BLIND, 0)))
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, HintLevel.BLIND, 0)))
    }

    @Test
    fun replayPolicyRequiresInkGuideAndReplayableAnalysis() {
        val order = strokeOrder()

        assertTrue(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), true, guide()))
        assertTrue(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.CLOSE, true, order), true, guide()))
        assertTrue(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.WRONG, false, order), true, guide()))

        assertFalse(WritingFeedbackCopy.canReplayAnalysis(null, true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), false, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), true, null))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, order), true, emptyGuide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.PASS, true, missingGuideOrder()), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.NO_INK, false, order), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.MODEL_UNAVAILABLE, false, order), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.NO_STROKE_DATA, false, order), true, guide()))
        assertFalse(WritingFeedbackCopy.canReplayAnalysis(analysisWithOrder(WritingAnalysis.Status.RECOGNITION_ERROR, false, order), true, guide()))
    }

    @Test
    fun learningPanelVisibilityPreservesRecallAndTeachingRules() {
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, true, false, 1))
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.NO_INK, false, HintLevel.BLIND, 0), true, false, 1))
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), true, false, 1))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), true, false, 1))

        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, false, 1))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, 1))
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, 3))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, HintLevel.BLIND, 0), false, false, 3))
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(analysis(WritingAnalysis.Status.WRONG, false, HintLevel.BLIND, 0), false, false, 3))
    }

    private fun <T> withDefaultLocale(locale: Locale, block: () -> T): T {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide("裂", listOf(stroke()))
    }

    private fun emptyGuide(): StrokeGuide {
        return StrokeGuide("裂", emptyList())
    }

    private fun strokeOrder(): StrokeOrderEvaluator.StrokeOrderResult {
        return StrokeOrderEvaluator.evaluate(guide(), sample())
    }

    private fun missingGuideOrder(): StrokeOrderEvaluator.StrokeOrderResult {
        return StrokeOrderEvaluator.evaluate(emptyGuide(), sample())
    }

    private fun sample(): WritingSample {
        return WritingSample(listOf(stroke()), 100f, 100f)
    }

    private fun stroke(): InkStroke {
        return InkStroke(
            listOf(
                InkPoint(0.1f, 0.2f, 0L),
                InkPoint(0.3f, 0.4f, 1L),
            )
        )
    }

    private fun analysis(
        status: WritingAnalysis.Status,
        passed: Boolean,
        hintLevel: HintLevel,
        hintsUsed: Int,
    ): WritingAnalysis {
        return WritingAnalysis(status, if (passed) "good" else "again", passed, status.name, emptyList(), null, hintLevel, hintsUsed)
    }

    private fun analysisWithMessage(message: String): WritingAnalysis {
        return WritingAnalysis(WritingAnalysis.Status.NO_INK, "again", false, message, emptyList(), null, HintLevel.BLIND, 0)
    }

    private fun analysisWithOrder(
        status: WritingAnalysis.Status,
        passed: Boolean,
        order: StrokeOrderEvaluator.StrokeOrderResult,
    ): WritingAnalysis {
        return WritingAnalysis(status, if (passed) "good" else "again", passed, status.name, emptyList(), order, HintLevel.BLIND, 0)
    }
}

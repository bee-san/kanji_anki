package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyWritingCopyTest {
    @Test
    fun studyWritingCopySwitchesBetweenEnglishAndJapaneseCopy() {
        withLocale(Locale.US) {
            assertEquals("Draw this kanji", StudyWritingCopy.title())
            assertEquals("Use the reference, trace, then check.", StudyWritingCopy.referenceInstruction())
            assertEquals("Prompt: split, rend", StudyWritingCopy.recallPromptLine("split, rend"))
            assertEquals("Reading: レツ", StudyWritingCopy.readingLine("レツ"))
            assertEquals(
                "Write from the prompt. The answer stays hidden until you check.",
                StudyWritingCopy.promptInstruction(),
            )
            assertEquals("Erase", StudyWritingCopy.eraseLabel())
            assertEquals("Undo", StudyWritingCopy.undoLabel())
            assertEquals(
                "Handwriting pad. Draw the requested kanji. With TalkBack on, use two fingers or " +
                    "TalkBack's pass-through gesture. Use Erase or Undo to edit, then choose Check.",
                StudyWritingCopy.drawingPadDescription(),
            )
            assertEquals("No strokes drawn.", StudyWritingCopy.drawingPadStrokeState(0))
            assertEquals("1 stroke drawn.", StudyWritingCopy.drawingPadStrokeState(1))
            assertEquals("3 strokes drawn.", StudyWritingCopy.drawingPadStrokeState(3))
            assertEquals("No strokes drawn.", StudyWritingCopy.drawingPadStrokeState(-1))
            assertEquals("Replay", StudyWritingCopy.replayLabel())
            assertEquals("Mark right anyway", StudyWritingCopy.manualOverrideLabel())
            assertEquals("Continue anyway", StudyWritingCopy.continueAnywayLabel())
            assertEquals("Skip", StudyWritingCopy.skipLabel())
            assertEquals("Try again with full guide", StudyWritingCopy.practiceWithGuideLabel())
            assertEquals("Checking handwriting...", StudyWritingCopy.checkingStatus())
            assertEquals(
                "The handwriting checker is unavailable on this device.",
                StudyWritingCopy.modelUnavailableStatus(),
            )
            assertEquals(
                "Download the handwriting checker before automatic checks.",
                StudyWritingCopy.downloadRequiredStatus(),
            )
            assertEquals("Downloading handwriting checker...", StudyWritingCopy.downloadingStatus())
            assertEquals(
                "Handwriting checker download failed: boom",
                StudyWritingCopy.downloadFailedStatus("boom"),
            )
            assertEquals("Handwriting checker ready.", StudyWritingCopy.readyStatus())
        }

        withLocale(Locale.JAPAN) {
            assertEquals("この漢字を書いてください", StudyWritingCopy.title())
            assertEquals("参考を見てなぞってから確認してください。", StudyWritingCopy.referenceInstruction())
            assertEquals("書き取りプロンプト: split, rend", StudyWritingCopy.recallPromptLine("split, rend"))
            assertEquals("読み: レツ", StudyWritingCopy.readingLine("レツ"))
            assertEquals(
                "問題を見て書いてください。答えは確認するまで隠れています。",
                StudyWritingCopy.promptInstruction(),
            )
            assertEquals("消去", StudyWritingCopy.eraseLabel())
            assertEquals("元に戻す", StudyWritingCopy.undoLabel())
            assertEquals(
                "手書きパッド。指定された漢字を書いてください。TalkBack使用中は、2本指または" +
                    "TalkBackのパススルージェスチャーを使ってください。修正には「消去」または" +
                    "「元に戻す」を使い、最後に「確認」を選んでください。",
                StudyWritingCopy.drawingPadDescription(),
            )
            assertEquals("まだ線はありません。", StudyWritingCopy.drawingPadStrokeState(0))
            assertEquals("3画入力済み。", StudyWritingCopy.drawingPadStrokeState(3))
            assertEquals("再生", StudyWritingCopy.replayLabel())
            assertEquals("それでも合格にする", StudyWritingCopy.manualOverrideLabel())
            assertEquals("このまま続行", StudyWritingCopy.continueAnywayLabel())
            assertEquals("スキップ", StudyWritingCopy.skipLabel())
            assertEquals("フルガイドで再挑戦", StudyWritingCopy.practiceWithGuideLabel())
            assertEquals("手書き判定中...", StudyWritingCopy.checkingStatus())
            assertEquals(
                "この端末では自動手書き判定は使えません。",
                StudyWritingCopy.modelUnavailableStatus(),
            )
            assertEquals(
                "自動判定を使う前に手書き判定器をダウンロードしてください。",
                StudyWritingCopy.downloadRequiredStatus(),
            )
            assertEquals("手書き判定器をダウンロードしています...", StudyWritingCopy.downloadingStatus())
            assertEquals(
                "手書き判定器のダウンロードに失敗しました: boom",
                StudyWritingCopy.downloadFailedStatus("boom"),
            )
            assertEquals("手書き判定器の準備ができました。", StudyWritingCopy.readyStatus())
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}

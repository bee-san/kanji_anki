package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyWritingCopyTest {
    @Test
    fun studyWritingCopySwitchesBetweenEnglishAndJapaneseCopy() {
        val studySession = RecordsSchedulerModels.StudySession(
            null,
            null,
            "session-token",
            "targeted_writing",
            false,
            "prompt text",
        )
        val repairSession = RecordsSchedulerModels.StudySession(
            null,
            null,
            "session-token",
            "repair_writing",
            false,
            "prompt text",
        )

        withLocale(Locale.US) {
            assertEquals("Draw this kanji", StudyWritingCopy.title())
            assertEquals("Writing", StudyWritingCopy.sectionTitle(studySession))
            assertEquals("", StudyWritingCopy.sectionTitle(repairSession))
            assertEquals("Use the reference, trace, then check.", StudyWritingCopy.referenceInstruction())
            assertEquals("Prompt: split, rend", StudyWritingCopy.recallPromptLine("split, rend"))
            assertEquals("Reading: レツ", StudyWritingCopy.readingLine("レツ"))
            assertEquals(
                "Write from the prompt. The answer stays hidden until you check.",
                StudyWritingCopy.promptInstruction(),
            )
            assertEquals("Erase", StudyWritingCopy.eraseLabel())
            assertEquals("Undo", StudyWritingCopy.undoLabel())
            assertEquals("Replay", StudyWritingCopy.replayLabel())
            assertEquals("Mark right anyway", StudyWritingCopy.manualOverrideLabel())
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
            assertEquals("書き取り", StudyWritingCopy.sectionTitle(studySession))
            assertEquals("", StudyWritingCopy.sectionTitle(repairSession))
            assertEquals("参考を見てなぞってから確認してください。", StudyWritingCopy.referenceInstruction())
            assertEquals("書き取りプロンプト: split, rend", StudyWritingCopy.recallPromptLine("split, rend"))
            assertEquals("読み: レツ", StudyWritingCopy.readingLine("レツ"))
            assertEquals(
                "問題を見て書いてください。答えは確認するまで隠れています。",
                StudyWritingCopy.promptInstruction(),
            )
            assertEquals("消去", StudyWritingCopy.eraseLabel())
            assertEquals("元に戻す", StudyWritingCopy.undoLabel())
            assertEquals("再生", StudyWritingCopy.replayLabel())
            assertEquals("それでも合格にする", StudyWritingCopy.manualOverrideLabel())
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
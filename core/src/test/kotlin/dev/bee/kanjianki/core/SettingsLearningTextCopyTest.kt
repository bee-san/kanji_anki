package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningTextCopyTest {
    @Test
    fun learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle())
        assertEquals(
            "Set new and missed waits. Due reviews move up.",
            SettingsLearningTextCopy.learningStepsBody(),
        )
        assertEquals("Missed reviews", SettingsLearningTextCopy.reviewMissesLabel())
        assertEquals("Use Anki defaults", SettingsLearningTextCopy.ankiDefaultLabel())
        assertEquals("Copy new-card steps", SettingsLearningTextCopy.sameLearningStepsLabel())
        assertEquals("Save steps", SettingsLearningTextCopy.saveLearningStepsLabel())
        assertEquals("Steps saved.", SettingsLearningTextCopy.learningStepsSavedToast())
    }

    @Test
    fun learningStepHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("学習ステップ", SettingsLearningTextCopy.learningStepsTitle())
            assertEquals(
                "新規とミス後の待ち時間を設定します。期限レビューは上に進みます。",
                SettingsLearningTextCopy.learningStepsBody(),
            )
            assertEquals("ミスしたレビュー", SettingsLearningTextCopy.reviewMissesLabel())
            assertEquals("Ankiの標準を使う", SettingsLearningTextCopy.ankiDefaultLabel())
            assertEquals("新規カードのステップをコピー", SettingsLearningTextCopy.sameLearningStepsLabel())
            assertEquals("ステップを保存", SettingsLearningTextCopy.saveLearningStepsLabel())
            assertEquals("ステップを保存しました。", SettingsLearningTextCopy.learningStepsSavedToast())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}

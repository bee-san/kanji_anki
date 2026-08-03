package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsImportTextCopyTest {
    @Test
    fun sourceLabelsResolveInEnglish() {
        assertEquals("Import active cards", SettingsImportTextCopy.activeCardsLabel())
        assertEquals("Import suspended cards", SettingsImportTextCopy.suspendedCardsLabel())
        assertEquals("Import weak cards", SettingsImportTextCopy.weakCardsLabel())
    }

    @Test
    fun sourceLabelsTranslateToJapaneseLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("アクティブカードを取り込む", SettingsImportTextCopy.activeCardsLabel())
            assertEquals("保留カードを取り込む", SettingsImportTextCopy.suspendedCardsLabel())
            assertEquals("苦手カードを取り込む", SettingsImportTextCopy.weakCardsLabel())
        } finally {
            Locale.setDefault(original)
        }
    }
}

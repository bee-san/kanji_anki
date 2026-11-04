package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsThemeTextCopyTest {
    @Test
    fun everyThemeNamesItselfInEnglish() {
        // Every choice has a non-blank title and subtitle, so no theme renders as a
        // gap; the `when` is exhaustive, so a new theme fails to compile until named.
        for (choice in KaniThemeChoice.entries) {
            assertTrue(choice.name, SettingsThemeTextCopy.themeTitle(choice).isNotBlank())
            assertTrue(choice.name, SettingsThemeTextCopy.themeSubtitle(choice).isNotBlank())
        }
        assertEquals("Girlypop", SettingsThemeTextCopy.themeTitle(KaniThemeChoice.GIRLYPOP))
        assertEquals("Pink, plum, and soft cream.", SettingsThemeTextCopy.themeSubtitle(KaniThemeChoice.GIRLYPOP))
        assertEquals("Selected", SettingsThemeTextCopy.selectedLabel())
    }

    @Test
    fun themeNamesTranslateToJapaneseLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("ガーリーポップ", SettingsThemeTextCopy.themeTitle(KaniThemeChoice.GIRLYPOP))
            assertEquals("ピンク、プラム、やわらかなクリーム。", SettingsThemeTextCopy.themeSubtitle(KaniThemeChoice.GIRLYPOP))
            assertEquals("森のコケ", SettingsThemeTextCopy.themeTitle(KaniThemeChoice.FOREST_MOSS))
            assertEquals("選択中", SettingsThemeTextCopy.selectedLabel())
            for (choice in KaniThemeChoice.entries) {
                assertTrue(choice.name, SettingsThemeTextCopy.themeTitle(choice).isNotBlank())
                assertTrue(choice.name, SettingsThemeTextCopy.themeSubtitle(choice).isNotBlank())
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}

package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuBarCopyTest {
    @Test
    fun menuTitlesStayStable() {
        assertEquals("Go", MenuBarCopy.goMenuLabel())
        assertEquals("Back", MenuBarCopy.backLabel())
    }

    @Test
    fun theStudyMenuIsNamedWhateverTheStudyTabIsNamed() {
        // Delegated, not restated: a menu titled differently from the tab it opens would
        // read as a different screen.
        assertEquals(NavigationCopy.studyLabel(), MenuBarCopy.studyMenuLabel())
        assertEquals("Study", MenuBarCopy.studyMenuLabel())
    }

    @Test
    fun menuStringsTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("移動", MenuBarCopy.goMenuLabel())
            assertEquals("戻る", MenuBarCopy.backLabel())
            assertEquals("学習", MenuBarCopy.studyMenuLabel())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}

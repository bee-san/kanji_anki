package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NavigationCopyTest {
    @Test
    fun navLabelsUseEnglishByDefault() {
        assertEquals("Home", NavigationCopy.homeLabel())
        assertEquals("Study", NavigationCopy.studyLabel())
        assertEquals("Stats", NavigationCopy.statsLabel())
        assertEquals("Settings", NavigationCopy.settingsLabel())
    }

    @Test
    fun navItemDescriptionsIncludeSelectionState() {
        assertEquals("Home tab, selected", NavigationCopy.navItemContentDescription("Home", true))
        assertEquals("Stats tab", NavigationCopy.navItemContentDescription("Stats", false))
    }

    @Test
    fun navLabelsTranslateToJapaneseLocale() {
        withJapaneseLocale {
            assertEquals("ホーム", NavigationCopy.homeLabel())
            assertEquals("学習", NavigationCopy.studyLabel())
            assertEquals("統計", NavigationCopy.statsLabel())
            assertEquals("設定", NavigationCopy.settingsLabel())
            assertEquals("ホームタブ、選択中", NavigationCopy.navItemContentDescription("ホーム", true))
            assertEquals("学習タブ", NavigationCopy.navItemContentDescription("学習", false))
        }
    }

    private fun withJapaneseLocale(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}

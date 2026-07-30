package dev.bee.kanjianki.core

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StudyGestureTextCopyTest {
    @Test
    fun englishCopyIsNonBlankAndStatusReflectsState() {
        withLocale(Locale.ENGLISH) {
            assertTrue(StudyGestureTextCopy.swipeTitle().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeBody().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeToggleLabel().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeEnabledToast().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeDisabledToast().isNotBlank())
            val on = StudyGestureTextCopy.swipeStatus(true)
            val off = StudyGestureTextCopy.swipeStatus(false)
            assertTrue(on.isNotBlank())
            assertTrue(off.isNotBlank())
            assertNotEquals(on, off)
        }
    }

    @Test
    fun japaneseCopyDiffersFromEnglishAndCoversEveryString() {
        val english = withLocale(Locale.ENGLISH) { StudyGestureTextCopy.swipeTitle() }
        val japanese = withLocale(Locale.JAPANESE) { StudyGestureTextCopy.swipeTitle() }
        assertNotEquals(english, japanese)
        withLocale(Locale.JAPANESE) {
            assertTrue(StudyGestureTextCopy.swipeBody().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeToggleLabel().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeEnabledToast().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeDisabledToast().isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeStatus(true).isNotBlank())
            assertTrue(StudyGestureTextCopy.swipeStatus(false).isNotBlank())
        }
    }

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}

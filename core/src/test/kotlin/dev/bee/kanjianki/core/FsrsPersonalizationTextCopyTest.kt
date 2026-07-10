package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FsrsPersonalizationTextCopyTest {
    @Test
    fun englishCopyCoversOffDefaultAndAdoptedStates() = withLocale(Locale.US) {
        assertEquals("Personalized scheduling", FsrsPersonalizationTextCopy.title())
        assertEquals("Off — using defaults", FsrsPersonalizationTextCopy.status(false, false, 0, null, null))
        assertEquals(
            "Using defaults — not enough history yet",
            FsrsPersonalizationTextCopy.status(true, false, 399, null, FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY),
        )
        assertEquals(
            "Using your fitted weights — 3.2% better on your last 1,840 reviews",
            FsrsPersonalizationTextCopy.status(true, true, 1840, 0.032, FsrsWeightFitter.REASON_ADOPTED),
        )
        assertEquals(
            "Using your previously fitted weights — the last fit was cancelled",
            FsrsPersonalizationTextCopy.status(true, true, 1840, null, FsrsWeightFitter.REASON_CANCELLED),
        )
        assertEquals(
            "Using your fitted weights",
            FsrsPersonalizationTextCopy.status(true, true, 1840, null, FsrsWeightFitter.REASON_ADOPTED),
        )
        assertEquals(
            "Using defaults — personalization was turned off during the fit",
            FsrsPersonalizationTextCopy.status(
                true,
                false,
                1840,
                null,
                FsrsWeightFitter.REASON_DISABLED_DURING_FIT,
            ),
        )
        assertEquals(
            "Using your previously fitted weights — the last fit failed",
            FsrsPersonalizationTextCopy.status(true, true, 1840, null, FsrsWeightFitter.REASON_FAILED),
        )
        assertEquals(
            "Using defaults — the last fit failed",
            FsrsPersonalizationTextCopy.status(true, false, 0, null, FsrsWeightFitter.REASON_FAILED),
        )
        assertTrue(FsrsPersonalizationTextCopy.body().contains("400"))
        assertEquals("Use my review history", FsrsPersonalizationTextCopy.toggleLabel())
        assertEquals("Fit now", FsrsPersonalizationTextCopy.fitNowLabel())
        assertEquals("Reset to defaults", FsrsPersonalizationTextCopy.resetLabel())
        assertTrue(FsrsPersonalizationTextCopy.enabledToast().isNotBlank())
        assertTrue(FsrsPersonalizationTextCopy.disabledToast().isNotBlank())
        assertTrue(FsrsPersonalizationTextCopy.fitQueuedToast().isNotBlank())
        assertTrue(FsrsPersonalizationTextCopy.turnOnFirstToast().isNotBlank())
        assertTrue(FsrsPersonalizationTextCopy.resetToast().isNotBlank())
    }

    @Test
    fun japaneseCopyCoversAllDefaultReasonsAndAdoption() = withLocale(Locale.JAPAN) {
        assertEquals("個人向けスケジュール", FsrsPersonalizationTextCopy.title())
        assertTrue(FsrsPersonalizationTextCopy.body().contains("400"))
        assertTrue(FsrsPersonalizationTextCopy.toggleLabel().contains("復習履歴"))
        assertTrue(FsrsPersonalizationTextCopy.fitNowLabel().contains("今すぐ"))
        assertTrue(FsrsPersonalizationTextCopy.resetLabel().contains("標準"))
        assertTrue(FsrsPersonalizationTextCopy.status(false, false, 0, null, null).contains("オフ"))
        assertTrue(FsrsPersonalizationTextCopy.status(true, false, 1, null, FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY).contains("不足"))
        assertTrue(FsrsPersonalizationTextCopy.status(true, false, 400, null, FsrsWeightFitter.REASON_INSUFFICIENT_IMPROVEMENT).contains("1%"))
        assertTrue(FsrsPersonalizationTextCopy.status(true, false, 400, null, FsrsWeightFitter.REASON_CANCELLED).contains("キャンセル"))
        assertTrue(FsrsPersonalizationTextCopy.status(true, false, 0, null, null).contains("まだ"))
        assertTrue(FsrsPersonalizationTextCopy.status(true, true, 1840, 0.032, FsrsWeightFitter.REASON_ADOPTED).contains("改善"))
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}

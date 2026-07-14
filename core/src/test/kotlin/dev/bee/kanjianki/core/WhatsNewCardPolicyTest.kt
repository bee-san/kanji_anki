package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewCardPolicyTest {
    @Test
    fun showsWhenVersionMatchesAndNotesPresent() {
        assertTrue(WhatsNewCardPolicy.shouldShow("0.4.33", "0.4.33", false, null))
    }

    @Test
    fun hiddenWhenStoredVersionDoesNotMatchCurrent() {
        assertFalse(WhatsNewCardPolicy.shouldShow("0.4.34", "0.4.33", false, null))
    }

    @Test
    fun hiddenWhenNotesBlank() {
        assertFalse(WhatsNewCardPolicy.shouldShow("0.4.33", "0.4.33", true, null))
    }

    @Test
    fun hiddenWhenAlreadySeen() {
        assertFalse(WhatsNewCardPolicy.shouldShow("0.4.33", "0.4.33", false, "0.4.33"))
    }

    @Test
    fun shownWhenSeenVersionIsDifferent() {
        assertTrue(WhatsNewCardPolicy.shouldShow("0.4.34", "0.4.34", false, "0.4.33"))
    }

    @Test
    fun hiddenWhenCurrentVersionEmpty() {
        assertFalse(WhatsNewCardPolicy.shouldShow("", "0.4.33", false, null))
    }

    @Test
    fun hiddenWhenCurrentVersionNull() {
        assertFalse(WhatsNewCardPolicy.shouldShow(null, "0.4.33", false, null))
    }

    @Test
    fun hiddenWhenStoredVersionNull() {
        assertFalse(WhatsNewCardPolicy.shouldShow("0.4.33", null, false, null))
    }
}

package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class HomeActionsTest {
    @Test
    fun toggleLocalSuspensionSuspendsActiveKanji() {
        val writer = RecordingSuspensionWriter()

        writer.setKanjiLocallySuspended("裂", true, 1234L)
        val toast = HomeTextCopy.localSuspendToast(false)

        assertEquals("裂", writer.kanji)
        assertTrue(writer.suspended)
        assertEquals(1234L, writer.changedAtMillis)
        assertEquals("Kanji suspended locally.", toast)
    }

    @Test
    fun toggleLocalSuspensionUnsuspendsSuspendedKanji() {
        val writer = RecordingSuspensionWriter()

        writer.setKanjiLocallySuspended("裂", false, 5678L)
        val toast = HomeTextCopy.localSuspendToast(true)

        assertEquals("裂", writer.kanji)
        assertFalse(writer.suspended)
        assertEquals(5678L, writer.changedAtMillis)
        assertEquals("Kanji unsuspended.", toast)
    }

    private class RecordingSuspensionWriter {
        var kanji: String? = null
        var suspended: Boolean = false
        var changedAtMillis: Long = 0

        fun setKanjiLocallySuspended(kanji: String, suspended: Boolean, changedAtMillis: Long) {
            this.kanji = kanji
            this.suspended = suspended
            this.changedAtMillis = changedAtMillis
        }
    }
}

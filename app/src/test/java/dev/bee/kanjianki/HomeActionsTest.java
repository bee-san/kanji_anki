package dev.bee.kanjianki;

import dev.bee.kanjianki.core.HomeTextCopy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HomeActionsTest {
    @Test
    public void toggleLocalSuspensionSuspendsActiveKanji() {
        RecordingSuspensionWriter writer = new RecordingSuspensionWriter();

        writer.setKanjiLocallySuspended("裂", true, 1234L);
        String toast = HomeTextCopy.localSuspendToast(false);

        assertEquals("裂", writer.kanji);
        assertTrue(writer.suspended);
        assertEquals(1234L, writer.changedAtMillis);
        assertEquals("Kanji suspended locally.", toast);
    }

    @Test
    public void toggleLocalSuspensionUnsuspendsSuspendedKanji() {
        RecordingSuspensionWriter writer = new RecordingSuspensionWriter();

        writer.setKanjiLocallySuspended("裂", false, 5678L);
        String toast = HomeTextCopy.localSuspendToast(true);

        assertEquals("裂", writer.kanji);
        assertFalse(writer.suspended);
        assertEquals(5678L, writer.changedAtMillis);
        assertEquals("Kanji unsuspended.", toast);
    }

    private static final class RecordingSuspensionWriter {
        private String kanji;
        private boolean suspended;
        private long changedAtMillis;

        public void setKanjiLocallySuspended(String kanji, boolean suspended, long changedAtMillis) {
            this.kanji = kanji;
            this.suspended = suspended;
            this.changedAtMillis = changedAtMillis;
        }
    }
}

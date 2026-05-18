package dev.bee.kanjianki;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HomeActionsTest {
    @Test
    public void toggleLocalSuspensionSuspendsActiveKanji() {
        RecordingSuspensionWriter writer = new RecordingSuspensionWriter();

        String toast = HomeActions.toggleLocalSuspension(writer, "裂", false, 1234L);

        assertEquals("裂", writer.kanji);
        assertTrue(writer.suspended);
        assertEquals(1234L, writer.changedAtMillis);
        assertEquals("Kanji suspended locally.", toast);
    }

    @Test
    public void toggleLocalSuspensionUnsuspendsSuspendedKanji() {
        RecordingSuspensionWriter writer = new RecordingSuspensionWriter();

        String toast = HomeActions.toggleLocalSuspension(writer, "裂", true, 5678L);

        assertEquals("裂", writer.kanji);
        assertFalse(writer.suspended);
        assertEquals(5678L, writer.changedAtMillis);
        assertEquals("Kanji unsuspended.", toast);
    }

    private static final class RecordingSuspensionWriter implements HomeActions.LocalSuspensionWriter {
        private String kanji;
        private boolean suspended;
        private long changedAtMillis;

        @Override
        public void setKanjiLocallySuspended(String kanji, boolean suspended, long changedAtMillis) {
            this.kanji = kanji;
            this.suspended = suspended;
            this.changedAtMillis = changedAtMillis;
        }
    }
}

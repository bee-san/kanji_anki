package dev.bee.kanjianki;

import dev.bee.kanjianki.core.SyncProgressCopy;
import dev.bee.kanjianki.sync.SyncProgress;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SyncProgressPanelTest {
    @Test
    public void syncProgressMapsAppStagesToCoreCopyStages() {
        assertEquals(SyncProgressCopy.Stage.FINDING_NOTE_TYPE, SyncProgress.coreStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
        assertEquals(SyncProgressCopy.Stage.READING_NOTES, SyncProgress.coreStage(SyncProgress.Stage.READING_NOTES));
        assertEquals(SyncProgressCopy.Stage.SCANNING_CARDS, SyncProgress.coreStage(SyncProgress.Stage.SCANNING_CARDS));
        assertEquals(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS, SyncProgress.coreStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS));
        assertEquals(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE, SyncProgress.coreStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE));
        assertEquals(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS, SyncProgress.coreStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
        assertEquals(SyncProgressCopy.Stage.SCANNING_CARDS, SyncProgress.cardsScanned(1, 2).coreStage());
        assertNull(SyncProgress.coreStage(null));
    }
}

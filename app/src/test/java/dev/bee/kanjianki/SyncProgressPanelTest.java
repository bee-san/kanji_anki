package dev.bee.kanjianki;

import dev.bee.kanjianki.core.SyncProgressCopy;
import dev.bee.kanjianki.sync.SyncProgress;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SyncProgressPanelTest {
    @Test
    public void mapsAppSyncStagesToCoreCopyStages() {
        assertEquals(SyncProgressCopy.Stage.FINDING_NOTE_TYPE, SyncProgressPanel.coreStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
        assertEquals(SyncProgressCopy.Stage.READING_NOTES, SyncProgressPanel.coreStage(SyncProgress.Stage.READING_NOTES));
        assertEquals(SyncProgressCopy.Stage.SCANNING_CARDS, SyncProgressPanel.coreStage(SyncProgress.Stage.SCANNING_CARDS));
        assertEquals(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS, SyncProgressPanel.coreStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS));
        assertEquals(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE, SyncProgressPanel.coreStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE));
        assertEquals(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS, SyncProgressPanel.coreStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
        assertEquals(null, SyncProgressPanel.coreStage(null));
    }
}

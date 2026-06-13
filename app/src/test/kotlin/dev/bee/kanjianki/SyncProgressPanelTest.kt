package dev.bee.kanjianki

import dev.bee.kanjianki.core.SyncProgressCopy
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SyncProgressPanelTest {
    @Test
    fun syncProgressMapsAppStagesToCoreCopyStages() {
        assertEquals(SyncProgressCopy.Stage.FINDING_NOTE_TYPE, SyncProgress.coreStage(SyncProgress.Stage.FINDING_NOTE_TYPE))
        assertEquals(SyncProgressCopy.Stage.READING_NOTES, SyncProgress.coreStage(SyncProgress.Stage.READING_NOTES))
        assertEquals(SyncProgressCopy.Stage.SCANNING_CARDS, SyncProgress.coreStage(SyncProgress.Stage.SCANNING_CARDS))
        assertEquals(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS, SyncProgress.coreStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS))
        assertEquals(SyncProgressCopy.Stage.SAVING_LOCAL_DATA, SyncProgress.coreStage(SyncProgress.Stage.SAVING_LOCAL_DATA))
        assertEquals(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE, SyncProgress.coreStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE))
        assertEquals(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS, SyncProgress.coreStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
        assertEquals(SyncProgressCopy.Stage.SCANNING_CARDS, SyncProgress.cardsScanned(1, 2).coreStage())
        assertNull(SyncProgress.coreStage(null))
    }

    @Test
    fun syncProgressPanelCopiesTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            val panel = SyncProgressPanel { 1_000L }

            assertEquals("ノートタイプを確認中", panel.state.stage)
            assertEquals("コレクションの詳細を読み込み中です。", panel.state.count)
            assertEquals("同期の進捗: ノートタイプを確認中", panel.state.progressDescription)

            panel.render(SyncProgress.atStage(null))
            assertEquals("カードを同期中", panel.state.stage)
            assertEquals("カードをスキャンする準備をしています。", panel.state.count)
            assertEquals("同期の進捗: カードを同期中", panel.state.progressDescription)

            panel.render(SyncProgress.cardsScanned(1, 2))
            assertEquals("カードをスキャン中", panel.state.stage)
            assertEquals("1 / 2 枚をスキャン済み", panel.state.count)
            assertEquals("同期の進捗: 1 / 2 枚をスキャン済み", panel.state.progressDescription)
        }
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}

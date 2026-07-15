package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SyncProgressCopyTest {
    @Test
    fun stageTitlesAndBodiesPreserveSyncProgressCopy() {
        withLocale(Locale.ENGLISH) {
            assertEquals("Finding note type", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.FINDING_NOTE_TYPE))
            assertEquals("Reading notes", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.READING_NOTES))
            assertEquals("Scanning cards", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.SCANNING_CARDS))
            assertEquals(
                "Processing imported cards",
                SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS),
            )
            assertEquals("Saving local data", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.SAVING_LOCAL_DATA))
            assertEquals(
                "Building practice queue",
                SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE),
            )
            assertEquals(
                "Archiving imported suspended cards",
                SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS),
            )
            assertEquals("Tagging repaired cards", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.TAGGING_REPAIRED))
            assertEquals("Syncing cards", SyncProgressCopy.stageTitle(null))

            assertEquals("Checking collection shape.", SyncProgressCopy.stageBody(SyncProgressCopy.Stage.FINDING_NOTE_TYPE))
            assertEquals(
                "Reading notes before the card total is known.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.READING_NOTES),
            )
            assertEquals(
                "Preparing card scan.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.SCANNING_CARDS),
            )
            assertEquals(
                "AnkiDroid read finished. Processing imported cards locally.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS),
            )
            assertEquals(
                "Saving the Anki snapshot and import evidence.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.SAVING_LOCAL_DATA),
            )
            assertEquals(
                "Saving the practice queue.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE),
            )
            assertEquals(
                "Updating archived suspended cards.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS),
            )
            assertEquals(
                "Tagging repaired notes in AnkiDroid.",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.TAGGING_REPAIRED),
            )
            assertEquals("Preparing card scan.", SyncProgressCopy.stageBody(null))
            assertEquals("Sync progress: Finding note type", SyncProgressCopy.progressDescription("Finding note type"))
            assertEquals("Reading collection details.", SyncProgressCopy.initialCountBody())
        }
    }

    @Test
    fun progressAndCardTextClampCounts() {
        withLocale(Locale.ENGLISH) {
            assertEquals(1000, SyncProgressCopy.progressPermille(0, 0))
            assertEquals(0, SyncProgressCopy.progressPermille(-5, 10))
            assertEquals(500, SyncProgressCopy.progressPermille(5, 10))
            assertEquals(1000, SyncProgressCopy.progressPermille(15, 10))
            assertEquals("0 / 10 cards scanned", SyncProgressCopy.cardProgressText(-2, 10))
            assertEquals("7 / 12 cards scanned", SyncProgressCopy.cardProgressText(7, 12))
            assertEquals("7 / 0 cards scanned", SyncProgressCopy.cardProgressText(7, -12))
        }
    }

    @Test
    fun scanRateTextPreservesEtaCopy() {
        withLocale(Locale.ENGLISH) {
            assertEquals(
                "Saving the practice queue.",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE, 10, 20, 1000),
            )
            assertEquals("Scanning cards.", SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 0, 20, 1000))
            assertEquals(
                "2.0 cards/sec - estimating time left",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 2, 20, 1000),
            )
            assertEquals(
                "5.0 cards/sec - about 3 sec left",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 5, 20, 1000),
            )
            assertEquals(
                "25 cards/sec - about 4 sec left",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 25, 125, 1000),
            )
            assertEquals(
                "10 cards/sec - finishing up",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 10, 10, 1000),
            )
        }
    }

    @Test
    fun shortDurationRoundsToCompactUnits() {
        withLocale(Locale.ENGLISH) {
            assertEquals("1 sec", SyncProgressCopy.shortDuration(0))
            assertEquals("59 sec", SyncProgressCopy.shortDuration(59_000))
            assertEquals("1 min", SyncProgressCopy.shortDuration(60_000))
            assertEquals("2 min", SyncProgressCopy.shortDuration(90_000))
            assertEquals("1 hr", SyncProgressCopy.shortDuration(3_600_000))
            assertEquals("2 hr", SyncProgressCopy.shortDuration(5_400_000))
        }
    }

    @Test
    fun japaneseLocaleTranslatesSyncProgressCopy() {
        withLocale(Locale.JAPANESE) {
            assertEquals("ノートタイプを確認中", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.FINDING_NOTE_TYPE))
            assertEquals("ノートを読み込み中", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.READING_NOTES))
            assertEquals("カードをスキャン中", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.SCANNING_CARDS))
            assertEquals(
                "取り込んだカードを処理中",
                SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS),
            )
            assertEquals("ローカルデータを保存中", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.SAVING_LOCAL_DATA))
            assertEquals(
                "練習キューを作成中",
                SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE),
            )
            assertEquals(
                "取り込んだ休止カードをアーカイブ中",
                SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS),
            )
            assertEquals("修復済みカードをタグ付け中", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.TAGGING_REPAIRED))
            assertEquals("カードを同期中", SyncProgressCopy.stageTitle(null))

            assertEquals("コレクションの形を確認しています。", SyncProgressCopy.stageBody(SyncProgressCopy.Stage.FINDING_NOTE_TYPE))
            assertEquals(
                "カード総数が分かる前にノートを読み込んでいます。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.READING_NOTES),
            )
            assertEquals(
                "カードをスキャンする準備をしています。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.SCANNING_CARDS),
            )
            assertEquals(
                "AnkiDroid の読み取りが完了しました。取り込んだカードをローカルで処理しています。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS),
            )
            assertEquals(
                "Anki のスナップショットと取り込み証跡を保存しています。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.SAVING_LOCAL_DATA),
            )
            assertEquals(
                "練習キューを保存しています。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE),
            )
            assertEquals(
                "取り込んだ休止カードをアーカイブしています。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS),
            )
            assertEquals(
                "修復済みノートをAnkiDroidでタグ付けしています。",
                SyncProgressCopy.stageBody(SyncProgressCopy.Stage.TAGGING_REPAIRED),
            )
            assertEquals("カードをスキャンする準備をしています。", SyncProgressCopy.stageBody(null))
            assertEquals("同期の進捗: ノートタイプを確認中", SyncProgressCopy.progressDescription("ノートタイプを確認中"))
            assertEquals("コレクションの詳細を読み込み中です。", SyncProgressCopy.initialCountBody())
            assertEquals("0 / 10 枚をスキャン済み", SyncProgressCopy.cardProgressText(-2, 10))
            assertEquals("7 / 12 枚をスキャン済み", SyncProgressCopy.cardProgressText(7, 12))
            assertEquals("7 / 0 枚をスキャン済み", SyncProgressCopy.cardProgressText(7, -12))
            assertEquals("カードをスキャンしています。", SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 0, 20, 1000))
            assertEquals(
                "2.0 枚/秒 - 残り時間を計算中",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 2, 20, 1000),
            )
            assertEquals(
                "5.0 枚/秒 - 残り約3秒",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 5, 20, 1000),
            )
            assertEquals(
                "25 枚/秒 - 残り約4秒",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 25, 125, 1000),
            )
            assertEquals(
                "10 枚/秒 - 仕上げ中",
                SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 10, 10, 1000),
            )
            assertEquals("1秒", SyncProgressCopy.shortDuration(0))
            assertEquals("59秒", SyncProgressCopy.shortDuration(59_000))
            assertEquals("1分", SyncProgressCopy.shortDuration(60_000))
            assertEquals("2分", SyncProgressCopy.shortDuration(90_000))
            assertEquals("1時間", SyncProgressCopy.shortDuration(3_600_000))
            assertEquals("2時間", SyncProgressCopy.shortDuration(5_400_000))
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

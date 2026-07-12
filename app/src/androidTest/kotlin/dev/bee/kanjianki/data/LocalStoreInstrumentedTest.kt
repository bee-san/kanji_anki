package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.TimelineCopy
import dev.bee.kanjianki.sync.SyncSettings

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import java.io.StringReader
import java.util.Arrays
import java.util.Collections
import java.util.LinkedHashMap


import dev.bee.kanjianki.TestDates.localDayStart
import dev.bee.kanjianki.TestDates.moveLocalDays
import dev.bee.kanjianki.TestRecords.card
import dev.bee.kanjianki.TestRecords.customMiningNote
import dev.bee.kanjianki.TestRecords.kikuCard
import dev.bee.kanjianki.TestRecords.review
import dev.bee.kanjianki.TestRecords.sourceKikuNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class LocalStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = LocalStore(context);
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            context.deleteDatabase("kanji_anki_simple.db")
        }
    }

    @Test
    fun testSyncPersistsActiveMirrorSuspendedArchiveAndDerivedRows() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val active = sourceKikuNote(1L, "確認", "かくにん", "confirmation", "確認した。")
        val suspended = sourceKikuNote(2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。")
        val activeCard = card(10L, 1L, "101", "例文マイニング")
                .history(45, 12, 1)
                .fsrs(18.5, 7.0, 0.48)
                .build()
        val suspendedCard = card(20L, 2L, "101", "例文マイニング").suspended().build()
        val snapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(active, suspended),
                listOf(activeCard, suspendedCard)
        )
        val source = RecordsImportModels.SuspendedSource("拉", 20L, 2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。")
        val imported = RecordsImportModels.SuspendedImport("拉", 3401, true, 3000, listOf(source))
        val example = RecordsImportModels.Example("suspended", 20L, 2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。", false, 0, 10, 5, 18.5, 7.0, 0.48)
        val row = RecordsImportModels.DashboardRow(
                "拉",
                3401,
                "ramen",
                "らーめん",
                "deck:例文マイニング 拉",
                93,
                "suspended_archive",
                "Imported from the local suspended archive.",
                0,
                1,
                0,
                listOf(example)
        )

        val syncId = store.saveSuccessfulSync(
                snapshot,
                listOf(imported),
                listOf(row),
                settings,
                1000L,
                2000L,
                null
        )
        store.updateSyncRemovalMessage(syncId, "Archived locally before provider cleanup.");

        assertLatestSyncArchivedSuspendedCard();
        assertSyncMirrorCounts();
        assertHistoricalCardSnapshot(syncId, 10L, 0, 1, 12, 1, 18.5, 7.0, 0.48);
        assertHistoricalIdentitySnapshot(syncId, 10L, "101", "例文マイニング", 1001L, "101", "例文マイニング");
        assertHistoricalKanjiSnapshot(syncId, "拉", 0, 1);
        assertSourceCardFsrs(18.5, 7.0, 0.48);
        assertDashboardRowFsrsStored();
        assertTrue(store.hasSuccessfulSyncSince(1500L));
        assertFalse(store.hasSuccessfulSyncSince(2500L));
        assertSuspendedImportStored();
        assertImportAuditStored();
    }

    @Test
    fun testInventoryMaintenanceSaveRowsPersistsDashboardRowsAndExamples() {
        val example = RecordsImportModels.Example(
                "suspended",
                20L,
                2L,
                "拉麺",
                "らーめん",
                "ramen",
                "拉麺を食べた。",
                false,
                0,
                10,
                5,
                18.5,
                7.0,
                0.48
        )
        val row = RecordsImportModels.DashboardRow(
                "拉",
                3401,
                "ramen radical gap",
                "らーめん",
                "deck:Kiku 拉",
                93,
                "suspended_archive",
                "Imported from the local suspended archive.",
                0,
                1,
                0,
                listOf(example)
        )

        val db = store.getWritableDatabase()
        LocalStoreInventoryMaintenance(store).saveRows(db, listOf(row), 4242L);

        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("kanji_examples"));
        assertEquals(1, store.dashboardRows().size);
        val stored = store.dashboardRows().get(0)
        assertEquals("拉", stored.kanji);
        assertEquals("ramen radical gap", stored.primaryMeaning);
        assertEquals(1, stored.examples.size);
        assertEquals("suspended", stored.examples.get(0).sourceType);
        assertEquals("拉麺", stored.examples.get(0).expression);
        assertEquals("ramen", stored.examples.get(0).meaning);
        assertScalarLong("dashboard_rows", "rebuilt_at", "kanji=?", arrayOf("拉"), 4242L);
        assertScalarString("kanji_examples", "source_type", "kanji=?", arrayOf("拉"), "suspended");
        assertScalarString("kanji_examples", "expression", "kanji=?", arrayOf("拉"), "拉麺");
        assertScalarString("kanji_examples", "meaning", "kanji=?", arrayOf("拉"), "ramen");
    }

    @Test
    fun testSyncPersistsNormalizedNoteArchiveAndHistoricalText() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val activeFields = LinkedHashMap<String, String>()
        activeFields.put("Expression", "<ruby>提<rt>てい</rt></ruby>　ＡＢＣ&nbsp;");
        activeFields.put("ExpressionReading", "  てい　  ");
        activeFields.put("MainDefinition", "<b>first</b> &amp; foremost|second");
        activeFields.put("Sentence", "<script>ignore()</script>　提示した。");
        activeFields.put("Frequency", "1000");
        activeFields.put("FreqSort", "1000");
        val active = RecordsSyncModels.Note(11L, 1011L, "Kiku", activeFields, listOf("focus"))

        val suspendedFields = LinkedHashMap<String, String>()
        suspendedFields.put("Expression", "  ＡＢＣ　拉麺  ");
        suspendedFields.put("ExpressionReading", "  らーめん  ");
        suspendedFields.put("MainDefinition", "ramen;noodles");
        suspendedFields.put("Sentence", "<style>.x{}</style>　拉麺を食べた。");
        suspendedFields.put("Frequency", "1000");
        suspendedFields.put("FreqSort", "1000");
        val suspended = RecordsSyncModels.Note(12L, 1012L, "Kiku", suspendedFields, emptyList())

        val activeCard = kikuCard(111L, 11L).history(30, 4, 0).build()
        val suspendedCard = kikuCard(112L, 12L).suspended().build()
        val imported = RecordsImportModels.SuspendedImport(
                "拉",
                3401,
                true,
                3000,
                listOf(RecordsImportModels.SuspendedSource("拉", 112L, 12L, "拉麺", "らーめん", "ramen", "拉麺を食べた。"))
        )

        val syncId = store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(listOf(active, suspended), listOf(activeCard, suspendedCard)),
                listOf(imported),
                listOf(row("提", 0)),
                settings,
                1000L,
                2000L,
                null
        )

        assertScalarString("source_notes", "expression", "note_id=?", arrayOf("11"), "提 ABC");
        assertScalarString("source_notes", "reading", "note_id=?", arrayOf("11"), "てい");
        assertScalarString("source_notes", "meaning", "note_id=?", arrayOf("11"), "first & foremost");
        assertScalarString("source_notes", "sentence", "note_id=?", arrayOf("11"), "提示した。");
        assertScalarString("suspended_archive", "expression", "card_id=?", arrayOf("112"), "ABC 拉麺");
        assertScalarString("suspended_archive", "meaning", "card_id=?", arrayOf("112"), "ramen");
        assertScalarString(
                "sync_note_snapshots",
                "expression",
                "sync_id=? AND note_id=?",
                arrayOf(syncId.toString(), "11"),
                "提 ABC"
        );
        assertScalarString(
                "sync_note_snapshots",
                "sentence",
                "sync_id=? AND note_id=?",
                arrayOf(syncId.toString(), "12"),
                "拉麺を食べた。"
        );
    }

    @Test
    fun testFailedImportSyncRollsBackAllDurableRows() {
        saveSingleRowSync(row("拉", 0), listOf(suspendedImport("拉")), 2000L);
        verifyBaselineImportRows();

        val malformedRow = RecordsImportModels.DashboardRow(
                "壊",
                3300,
                "broken import",
                "こわ",
                "deck:Kiku 壊",
                91,
                "suspended_archive",
                "Imported from suspended cards",
                1,
                0,
                0,
                listOf(null)
        )

        try {
            store.saveSuccessfulSync(
                    RecordsSyncModels.CollectionSnapshot(
                            listOf(sourceKikuNote(30L, "壊語", "こわ", "broken", "壊を見た。")),
                            listOf(kikuCard(300L, 30L).build())
                    ),
                    listOf(suspendedImport("壊")),
                    listOf(malformedRow),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    2500L,
                    3000L,
                    null
            );
            throw AssertionError("Malformed import row should fail before committing sync data");
        } catch (expected: NullPointerException) {
            // Expected: the important assertion is that the surrounding DB transaction rolled back.
        }

        verifyRolledBackImportRows();
    }

    private fun verifyBaselineImportRows() {
        assertEquals(1, count("sync_runs"));
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("suspended_imports"));
        assertEquals(1, count("import_rule_audits"));
        assertEquals(1, count("import_decisions"));
        assertEquals(1, count("sync_card_snapshots"));
    }

    private fun verifyRolledBackImportRows() {
        assertEquals(1, count("sync_runs"));
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("suspended_imports"));
        assertEquals(1, count("import_rule_audits"));
        assertEquals(1, count("import_decisions"));
        assertEquals(1, count("source_cards"));
        assertEquals(1, count("source_notes"));
        assertEquals(1, count("sync_card_snapshots"));
        assertEquals(0, countWhere("dashboard_rows", "kanji=?", "壊"));
        assertEquals(0, countWhere("suspended_imports", "kanji=?", "壊"));
        assertEquals(0, countWhere("sync_note_snapshots", "note_id=?", "30"));
    }

    @Test
    fun testKanjiInventorySearchAndLocalSuspensionSurviveSyncRebuild() {
        val row = row("拉", 0)
        saveSingleRowSync(row, listOf(suspendedImport("拉")), 2000L);

        val ramenMatches = store.searchKanjiInventory("ramen")
        assertFalse(ramenMatches.isEmpty());
        assertEquals("拉", ramenMatches.get(0).kanji);
        assertFalse(ramenMatches.get(0).suspended);
        assertEquals(1, store.activeDashboardRows().size);

        store.setKanjiLocallySuspended("拉", true, 2500L);
        val suspended = store.inventoryItemForKanji("拉")
        assertNotNull(suspended);
        assertTrue(requireNotNull(suspended).suspended);
        assertEquals(0, store.activeDashboardRows().size);
        assertEquals("拉", store.searchKanjiInventory("拉").get(0).kanji);
        assertTrue(store.searchKanjiInventory("拉").get(0).suspended);

        saveSingleRowSync(row, emptyList(), 3000L);
        assertTrue(requireNotNull(store.inventoryItemForKanji("拉")).suspended);
        assertEquals(0, store.activeDashboardRows().size);

        store.setKanjiLocallySuspended("拉", false, 3500L);
        assertFalse(requireNotNull(store.inventoryItemForKanji("拉")).suspended);
        assertEquals(1, store.activeDashboardRows().size);
    }

    @Test
    fun testKanjiInventorySearchFiltersSimilarKanjiAndBulkStudySelection() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val snapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(
                        sourceKikuNote(1L, "拉麺", "らーめん", "ramen", "拉麺を食べた。"),
                        sourceKikuNote(2L, "謎", "なぞ", "riddle", "謎を見た。")
                ),
                listOf(
                        kikuCard(10L, 1L).build(),
                        kikuCard(20L, 2L).build()
                )
        )
        val index = SimilarKanjiIndex.parseTsv(StringReader("""
                拉\t麺\tfixture
                """))

        store.saveSuccessfulSync(snapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(1000L, 2000L), null, index);

        assertContainsKanji(store.searchKanjiInventory(""), "謎");
        val similarOnly = store.searchKanjiInventory("", true)
        assertEquals(listOf("拉", "麺"), similarOnly.map { it.kanji });
        assertTrue(store.searchKanjiInventory("riddle", true).isEmpty());

        store.setKanjiLocallySuspendedForKanji(similarOnly.map { it.kanji }, true, 2500L)
        val suspendedSimilarOnly = store.searchKanjiInventory("", true)
        assertTrue(suspendedSimilarOnly.all { it.suspended })
        assertTrue(requireNotNull(store.inventoryItemForKanji("拉")).suspended)
        assertTrue(requireNotNull(store.inventoryItemForKanji("麺")).suspended)
        assertFalse(requireNotNull(store.inventoryItemForKanji("謎")).suspended)

        store.setKanjiLocallySuspendedForKanji(similarOnly.map { it.kanji }, false, 3000L)
        val unsuspendedSimilarOnly = store.searchKanjiInventory("", true)
        assertTrue(unsuspendedSimilarOnly.none { it.suspended })
        assertFalse(requireNotNull(store.inventoryItemForKanji("拉")).suspended)
        assertFalse(requireNotNull(store.inventoryItemForKanji("麺")).suspended)
    }

    @Test
    fun testKanjiInventoryShortensLongReadingListsButKeepsSearchText() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val snapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(
                        sourceKikuNote(1L, "読一", "あ", "read one", "読んだ。"),
                        sourceKikuNote(2L, "読二", "い", "read two", "読んだ。"),
                        sourceKikuNote(3L, "読三", "う", "read three", "読んだ。"),
                        sourceKikuNote(4L, "読四", "え", "read four", "読んだ。")
                ),
                listOf(
                        kikuCard(10L, 1L).build(),
                        kikuCard(20L, 2L).build(),
                        kikuCard(30L, 3L).build(),
                        kikuCard(40L, 4L).build()
                )
        )

        store.saveSuccessfulSync(snapshot, emptyList(), emptyList(), settings, 1000L, 2000L, null);

        val item = requireNotNull(store.inventoryItemForKanji("読"))
        assertEquals("あ / い / う +1 more", item.readings);
        assertContainsKanji(store.searchKanjiInventory("え"), "読");
    }

    @Test
    fun testSimilarPairsUseConfiguredInventoryFieldsAndPreserveFirstSeen() {
        val settings = RecordsSyncModels.Settings(
                "Custom Mining",
                "Mining",
                "Word",
                "Kana",
                "Gloss",
                "Context",
                "Frequency",
                "Sort",
                21,
                2,
                3000,
                24,
                3
        )
        val snapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(customMiningNote(1L, "拉麺", "らーめん", "ramen", "拉麺を食べた。")),
                listOf(card(10L, 1L, "Custom").build())
        )
        val index = SimilarKanjiIndex.parseTsv(StringReader("""
                拉\t麺\tfixture
                拉\t謎\tfixture
                """))

        store.saveSuccessfulSync(snapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(1000L, 2000L), null, index);

        assertNotNull(store.inventoryItemForKanji("拉"));
        assertNotNull(store.inventoryItemForKanji("麺"));
        assertTrue(store.hasSimilarLocalPair("拉", "麺"));
        assertTrue(store.hasSimilarLocalPair("麺", "拉"));
        assertFalse(store.hasSimilarLocalPair("拉", "謎"));
        assertEquals(1, store.similarPairsForKanji("拉").size);
        val first = store.allLocalSimilarPairs().get(0)
        assertEquals("拉", first.kanjiA);
        assertEquals("麺", first.kanjiB);
        assertEquals("fixture", first.source);
        assertEquals(2000L, first.firstSeenAtMillis);
        assertEquals(2000L, first.lastSeenAtMillis);

        store.saveSuccessfulSync(snapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(2500L, 3000L), null, index);

        val updated = store.allLocalSimilarPairs().get(0)
        assertEquals(2000L, updated.firstSeenAtMillis);
        assertEquals(3000L, updated.lastSeenAtMillis);
    }

    @Test
    fun testSimilarChoiceStateSelectionAdaptiveRepairAndNormalReviewIsolation() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val snapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(
                        sourceKikuNote(1L, "拉", "ら", "pull", "拉を見た。"),
                        sourceKikuNote(2L, "提", "てい", "carry", "提を見た。"),
                        sourceKikuNote(3L, "謎", "なぞ", "riddle", "謎を見た。")
                ),
                listOf(
                        kikuCard(10L, 1L).history(30, 4, 0).build(),
                        kikuCard(20L, 2L).history(30, 4, 0).build(),
                        kikuCard(30L, 3L).history(30, 4, 0).build()
                )
        )
        val index = SimilarKanjiIndex.parseTsv(StringReader("""
                拉\t提\tfixture
                拉\t謎\tfixture
                提\t外\tfixture
                """))

        store.saveSuccessfulSync(snapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(1000L, 2000L), null, index);

        val pull = findSimilarChoice("拉")
        assertInitialSimilarChoiceDue(pull);

        val wrong = store.submitSimilarChoice(pull, "提", 2500L)
        assertWrongSimilarChoiceUsesInlineAdaptiveRepair(wrong);

        val retry = store.dueSimilarChoiceForActiveTarget("拉", 3000L)
        assertNotNull(retry);
        assertSimilarChoiceRetryDue();
        val correct = store.submitSimilarChoice(retry, "拉", 3100L)
        assertCorrectSimilarChoicePasses(correct);

        store.saveSuccessfulSync(snapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(4000L, 5000L), null, index);
        assertNull("passed state should survive identical sync rebuild", store.dueSimilarChoiceForActiveTarget("拉", 5000L));
        assertTrue(findSimilarChoice("拉").passed());
    }

    @Test
    fun testSimilarChoiceRebuildDeletesStaleChoicesAndPendingRepairs() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val fullSnapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(
                        sourceKikuNote(1L, "拉", "ら", "pull", "拉を見た。"),
                        sourceKikuNote(2L, "提", "てい", "carry", "提を見た。"),
                        sourceKikuNote(3L, "謎", "なぞ", "riddle", "謎を見た。")
                ),
                listOf(
                        kikuCard(10L, 1L).history(30, 4, 0).build(),
                        kikuCard(20L, 2L).history(30, 4, 0).build(),
                        kikuCard(30L, 3L).history(30, 4, 0).build()
                )
        )
        val index = SimilarKanjiIndex.parseTsv(StringReader("拉\t提\tfixture\n拉\t謎\tfixture\n"))
        store.saveSuccessfulSync(fullSnapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(1000L, 2000L), null, index);
        val pull = findSimilarChoice("拉")

        store.submitSimilarChoice(pull, "提", 2500L);
        assertEquals(0, count("similar_kanji_repair_queue"));
        assertNotNull(store.dueSimilarChoiceForActiveTarget("拉", 2600L));

        // One-release compatibility: sync rebuilds still drain a persisted
        // repair created by an older app, even though new choices never add one.
        insertLegacySimilarWritingRepair("拉", "提", "拉|提|謎", "提", "pull", 2500L, 2500L)
        assertEquals(1, count("similar_kanji_repair_queue"));
        assertNull(store.dueSimilarChoiceForActiveTarget("拉", 2600L));

        val reducedSnapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(
                        sourceKikuNote(1L, "拉", "ら", "pull", "拉を見た。"),
                        sourceKikuNote(2L, "提", "てい", "carry", "提を見た。")
                ),
                listOf(
                        kikuCard(10L, 1L).history(30, 4, 0).build(),
                        kikuCard(20L, 2L).history(30, 4, 0).build()
                )
        )
        val emptyIndex = SimilarKanjiIndex.parseTsv(StringReader(""))
        store.saveSuccessfulSync(reducedSnapshot, emptyList(), emptyList(), settings, LocalStoreBase.SyncTiming(3000L, 4000L), null, emptyIndex);

        assertTrue(store.allSimilarChoiceCards().isEmpty());
        assertEquals(0, count("similar_kanji_repair_queue"));
        assertEquals(1, count("similar_kanji_review_log"));
    }

    @Test
    fun testTimelineRecordsSuspendedImportOnceAcrossRepeatedSync() {
        val row = row("拉", 0)
        val imported = suspendedImport("拉")

        saveSingleRowSync(row, listOf(imported), 2000L);
        assertEquals(1, countTimelineType("拉", "first_seen"));
        assertEquals(1, countTimelineType("拉", "suspended_imported"));
        assertEquals(1, countTimelineType("拉", "weak_support_seen"));

        saveSingleRowSync(row, listOf(imported), 3000L);
        assertEquals(1, countTimelineType("拉", "first_seen"));
        assertEquals(1, countTimelineType("拉", "suspended_imported"));
        assertEquals(1, countTimelineType("拉", "weak_support_seen"));
    }

    @Test
    fun testTimelineRecordsSupportRetirementAndReopen() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val firstSync = saveSingleRowSync(row("拉", 0), emptyList(), 2000L)
        assertTrue(firstSync > 0L);
        val active = RecordsStudyModels.StudyItem("拉", "review", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L)
        store.replaceStudyItems(listOf(active));

        val retireSync = saveSingleRowSync(row("拉", settings.matureSupportThreshold), emptyList(), 3000L)
        val retired = RecordsStudyModels.StudyItem("拉", "retired", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L)
        store.replaceStudyItems(listOf(retired), retireSync, 3000L, settings);

        val reopenSync = saveSingleRowSync(row("拉", 0), emptyList(), 4000L)
        val reopened = RecordsStudyModels.StudyItem("拉", "review", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L)
        store.replaceStudyItems(listOf(reopened), reopenSync, 4000L, settings);

        val timeline = store.timelineForKanji("拉")
        assertNotNull(timeline.currentRow);
        assertNotNull(timeline.currentStudyItem);
        assertEquals("review", requireNotNull(timeline.currentStudyItem).state);
        assertTrue(hasTimelineType(timeline, "support_improved"));
        assertTrue(hasTimelineType(timeline, "retired"));
        assertTrue(hasTimelineType(timeline, "support_dropped"));
        assertTrue(hasTimelineType(timeline, "reopened"));
    }

    @Test
    fun testTimelineRecordsOrphanStudyRetireAndReopenDetailsWithoutDashboardRow() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val active = RecordsStudyModels.StudyItem("孤", "review", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L)
        val retired = RecordsStudyModels.StudyItem("孤", "retired", 0L, 1.8, 4.8, 1, 0, 2, 1, null, 1000L)
        store.replaceStudyItems(listOf(active));

        val retireSync = saveSingleRowSync(row("別", 0), emptyList(), 2000L)
        store.replaceStudyItems(listOf(retired), retireSync, 2500L, settings);
        val reopenSync = saveSingleRowSync(row("他", 0), emptyList(), 3000L)
        store.replaceStudyItems(listOf(active), reopenSync, 3500L, settings);

        val timeline = store.timelineForKanji("孤")
        assertNull(timeline.currentRow);
        assertNotNull(timeline.currentStudyItem);
        assertEquals("review", requireNotNull(timeline.currentStudyItem).state);
        assertTimelineEventDetailContains(timeline, "retired", "No weak Anki evidence remained");
        assertTimelineEventDetailContains(timeline, "reopened", "found weak evidence again");
    }

    @Test
    fun testTimelineForNullKanjiReturnsEmptyTimeline() {
        val timeline = store.timelineForKanji("")

        assertNull(timeline.inventoryItem);
        assertNull(timeline.currentRow);
        assertNull(timeline.currentStudyItem);
        assertTrue(timeline.events.isEmpty());
    }

    @Test
    fun testTimelineReviewEventsMapPassFailAndManualOverride() {
        store.saveReview(RecordsSchedulerModels.ReviewRequest("拉", "pass-token", "good", true, true, false, 0), "good", 1000L);
        store.saveReview(RecordsSchedulerModels.ReviewRequest("拉", "fail-token", "good", true, false, false, 0), "again", 2000L);
        store.saveReview(RecordsSchedulerModels.ReviewRequest("拉", "override-token", "good", true, false, true, 0), "good", 3000L);

        val timeline = store.timelineForKanji("拉")
        assertEquals(1, countTimelineType(timeline, "review_passed"));
        assertEquals(1, countTimelineType(timeline, "review_failed"));
        assertEquals(1, countTimelineType(timeline, "manual_override"));
    }

    @Test
    fun testEmptyStatsAndImpactReportUseRealEmptyStates() {
        val streak = store.studyStreak(System.currentTimeMillis())
        assertEquals(0, streak.currentDays);
        assertEquals(0, streak.bestDays);
        assertFalse(streak.studiedToday);
        assertEquals(0, streak.reviewsToday);
        assertTrue(store.recentMistakes(0).isEmpty());
        assertTrue(KanjiImpactReportStore(store).report().empty());
        assertEquals(0L, store.studyTaskTimeStats(System.currentTimeMillis()).averageMillisPerTask());
    }

    @Test
    fun testRecentMistakesClampLimitAndKeepNewestHardAgainOnly() {
        store.saveReview(RecordsSchedulerModels.ReviewRequest("古", "old-good", "good", false, false, false, 0), "good", 1000L);
        store.saveReview(RecordsSchedulerModels.ReviewRequest("拉", "old-again", "again", false, false, false, 0), "again", 2000L);
        store.saveReview(RecordsSchedulerModels.ReviewRequest("提", "new-hard", "hard", false, false, false, 0), "hard", 3000L);

        val oneMistake = store.recentMistakes(0)
        assertEquals(1, oneMistake.size);
        assertEquals("提", oneMistake.get(0).kanji);
        assertEquals("hard", oneMistake.get(0).rating);
    }

    @Test
    fun testVersionEighteenMigrationPreservesLegacyTimelineHistory() {
        store.close();
        context.deleteDatabase("kanji_anki_simple.db");
        val db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null)
        try {
            createLegacyV1Schema(db);
            ContentValuesBuilder.insert(db, "suspended_imports")
                    .put("kanji", "拉")
                    .put("jiten_rank", 3401)
                    .put("rank_known", 1)
                    .put("cutoff_used", 3000)
                    .put("first_imported_at", 1500L)
                    .put("last_seen_sync_id", 7L)
                    .commit();
            ContentValuesBuilder.insert(db, "suspended_sources")
                    .put("kanji", "拉")
                    .put("card_id", 200L)
                    .put("note_id", 20L)
                    .put("expression", "拉麺")
                    .put("reading", "らーめん")
                    .put("meaning", "ramen")
                    .put("sentence", "拉麺を食べた。")
                    .put("sync_id", 7L)
                    .commit();
            ContentValuesBuilder.insert(db, "study_items")
                    .put("kanji", "孤")
                    .put("state", "retired")
                    .put("due_at", 0L)
                    .put("stability", 2.0)
                    .put("difficulty", 4.0)
                    .put("total_reviews", 3)
                    .put("lapses", 1)
                    .put("learning_step", 0)
                    .put("writing_level", 0)
                    .put("active_token", "")
                    .put("created_at", 1700L)
                    .commit();
            db.setVersion(1);
        } finally {
            db.close();
        }

        store = LocalStore(context);

        val imported = store.timelineForKanji("拉")
        val orphan = store.timelineForKanji("孤")
        assertEquals(3, count("kanji_timeline_events"));
        assertEquals(1, countTimelineType(imported, "suspended_imported"));
        assertNull(orphan.currentRow);
        assertNull(orphan.currentStudyItem);
        assertEquals(1, countTimelineType(orphan, "first_seen"));
        assertEquals(1, countTimelineType(orphan, "retired"));
    }

    @Test
    fun testVersionEighteenMigrationPreservesProviderMirrorAndLocalStatsHistory() {
        store.close();
        context.deleteDatabase("kanji_anki_simple.db");
        val db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null)
        try {
            createLegacyV1Schema(db);
            ContentValuesBuilder.insert(db, "dashboard_rows")
                    .put("kanji", "拉")
                    .put("jiten_rank", 3401)
                    .put("primary_meaning", "ramen radical gap")
                    .put("reading", "ら")
                    .put("browser_search", "deck:Kiku 拉")
                    .put("weakness_score", 88)
                    .put("reason_code", "suspended_archive")
                    .put("reason_text", "Imported from suspended cards")
                    .put("active_example_count", 1)
                    .put("suspended_example_count", 1)
                    .put("mature_support_count", 0)
                    .put("rebuilt_at", 2000L)
                    .commit();
            ContentValuesBuilder.insert(db, "kanji_examples")
                    .put("kanji", "拉")
                    .put("source_type", "active")
                    .put("card_id", 10L)
                    .put("note_id", 1L)
                    .put("expression", "拉麺")
                    .put("reading", "らーめん")
                    .put("meaning", "ramen")
                    .put("sentence", "拉麺を食べた。")
                    .put("mature", 0)
                    .put("lapses", 1)
                    .commit();
            ContentValuesBuilder.insert(db, "study_items")
                    .put("kanji", "拉")
                    .put("state", "review")
                    .put("due_at", 0L)
                    .put("stability", 1.8)
                    .put("difficulty", 4.8)
                    .put("total_reviews", 1)
                    .put("lapses", 0)
                    .put("learning_step", 2)
                    .put("writing_level", 1)
                    .put("active_token", "")
                    .put("created_at", 1500L)
                    .commit();
            ContentValuesBuilder.insert(db, "review_log")
                    .put("kanji", "拉")
                    .put("token", "legacy-token")
                    .put("rating", "good")
                    .put("writing_required", 1)
                    .put("writing_passed", 1)
                    .put("manual_override", 0)
                    .put("reviewed_at", 2500L)
                    .commit();
            db.setVersion(1);
        } finally {
            db.close();
        }

        store = LocalStore(context);
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("review_log"));
        assertScalarString("review_log", "rating", "token=?", arrayOf("legacy-token"), "good");
        assertScalarLong("review_log", "review_day_start", "token=?", arrayOf("legacy-token"), 0L);
        assertEquals(3, count("kanji_timeline_events"));
        val timeline = store.timelineForKanji("拉")
        assertEquals(1, countTimelineType(timeline, "first_seen"));
        assertEquals(1, countTimelineType(timeline, "weak_support_seen"));
        assertEquals(1, countTimelineType(timeline, "review_passed"));
        assertEquals(0, count("sync_kanji_snapshots"));
        assertMigratedColumnsExist();
    }

    @Test
    fun testVersionEighteenMigrationBackfillsMissingHistoricalSyncSnapshotsAndKeepsMirrorRows() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val note = sourceKikuNote(1L, "確認", "かくにん", "confirmation", "確認した。")
        val card = card(400L, 1L, "9001", "Kiku Deck")
                .history(25, 9, 1)
                .fsrs(6.5, 4.5, 0.82)
                .build()
        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card)),
                emptyList(),
                listOf(row("確", 2)),
                settings,
                1000L,
                2000L,
                null
        );
        val db = store.getWritableDatabase()
        db.delete("sync_card_snapshots", null, null);
        db.delete("sync_note_snapshots", null, null);
        db.delete("sync_kanji_snapshots", null, null);
        db.setVersion(11);
        store.close();

        store = LocalStore(context);

        assertEquals(1, count("source_cards"));
        assertEquals(1, count("source_notes"));
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("sync_card_snapshots"));
        assertEquals(1, count("sync_note_snapshots"));
        assertTrue(count("sync_kanji_snapshots") >= 1);
        assertEquals(1, countWhere("sync_kanji_snapshots", "kanji=?", "確"));
    }

    @Test
    fun testVersionEighteenMigrationKeepsExistingStatsAndSnapshotsIdempotent() {
        store.saveReview(RecordsSchedulerModels.ReviewRequest("拉", "migration-token", "good", true, true, false, 0), "good", 3000L);
        assertTrue(store.recordStudyTaskAnswered("migration-task", "拉", "kanji_meaning", 1000L, 2000L, 60_000L, "good"));
        saveSingleRowSync(row("拉", 0), emptyList(), 4000L);
        val reviewRows = count("review_log")
        val taskRows = count("study_task_log")
        val timelineRows = count("kanji_timeline_events")
        val cardSnapshots = count("sync_card_snapshots")
        val noteSnapshots = count("sync_note_snapshots")
        val kanjiSnapshots = count("sync_kanji_snapshots")

        val db = store.getWritableDatabase()
        db.setVersion(17);
        store.close();

        store = LocalStore(context);
        verifyVersionEighteenMigrationCounts(reviewRows, taskRows, timelineRows, cardSnapshots, noteSnapshots, kanjiSnapshots);

        store.close();
        store = LocalStore(context);
        verifyVersionEighteenMigrationCounts(reviewRows, taskRows, timelineRows, cardSnapshots, noteSnapshots, kanjiSnapshots);
    }

    private fun verifyVersionEighteenMigrationCounts(reviewRows: Int, taskRows: Int, timelineRows: Int, cardSnapshots: Int, noteSnapshots: Int, kanjiSnapshots: Int) {
        assertEquals(reviewRows, count("review_log"));
        assertEquals(taskRows, count("study_task_log"));
        assertEquals(timelineRows, count("kanji_timeline_events"));
        assertEquals(cardSnapshots, count("sync_card_snapshots"));
        assertEquals(noteSnapshots, count("sync_note_snapshots"));
        assertEquals(kanjiSnapshots, count("sync_kanji_snapshots"));
    }

    @Test
    fun testVersionSixteenMigrationPreservesLegacySchedulerStateAndClearsSideQueues() {
        store.close();
        context.deleteDatabase("kanji_anki_simple.db");
        val db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null)
        try {
            createLegacyV15SchedulerSchema(db);
            insertLegacyV15StudyItem(db, "書", "review", 2, 1, "word_reading", 950L, "書|書く|かく|write");
            insertLegacyV15StudyItem(db, "型", "learning", -1, 0, "", 0L, "型|かた|type");
            insertLegacyV15StudyItem(db, "水", "new", 0, 0, "", 0L, "水|みず|water");
            insertLegacyV15StudyItem(db, "字", "review", 1, 0, "", 0L, "字|じ|character");
            insertLegacyV15StudyItem(db, "読", "review", 2, 0, "", 0L, "読|よむ|read");
            insertLegacyV15StudyItem(db, "語", "review", 3, 0, "", 0L, "語|ご|word");
            ContentValuesBuilder.insert(db, "learning_repeats")
                    .put("kanji", "書")
                    .put("answer_signature", "書|書く|かく|write")
                    .put("task_type", "kanji_meaning")
                    .put("repeat_type", "new")
                    .put("step_index", 1)
                    .put("due_at", 1500L)
                    .put("active_token", "repeat-token")
                    .put("created_at", 500L)
                    .put("updated_at", 600L)
                    .commit();
            ContentValuesBuilder.insert(db, "similar_kanji_choice_state")
                    .put("target_kanji", "書")
                    .put("choice_signature", "書|晝")
                    .put("primary_meaning", "write")
                    .put("choices", "書\t晝")
                    .put("due_at", 1000L)
                    .put("passed_at", 0L)
                    .put("last_reviewed_at", 0L)
                    .put("correct_count", 0)
                    .put("wrong_count", 1)
                    .put("active_token", "")
                    .put("first_seen_at", 500L)
                    .put("last_seen_at", 600L)
                    .commit();
            ContentValuesBuilder.insert(db, "similar_kanji_repair_queue")
                    .put("target_kanji", "書")
                    .put("repair_kanji", "晝")
                    .put("choice_signature", "書|晝")
                    .put("wrong_selection", "晝")
                    .put("prompt_meaning", "write")
                    .put("status", "pending")
                    .put("due_at", 1000L)
                    .put("active_token", "")
                    .put("attempts", 1)
                    .put("created_at", 500L)
                    .put("updated_at", 600L)
                    .put("completed_at", 0L)
                    .commit();
            db.setVersion(15);
        } finally {
            db.close();
        }

        store = LocalStore(context);

        assertEquals(6, count("study_items"));
        assertEquals(0, count("learning_repeats"));
        assertEquals(0, count("similar_kanji_choice_state"));
        assertEquals(0, count("similar_kanji_repair_queue"));
        assertMigratedStudyColumns();
        assertScalarString("study_items", "suppressed_by_task_type", "kanji=? AND answer_signature=?", arrayOf("書", "書|書く|かく|write"), "word_reading");
        assertScalarLong("study_items", "suppressed_at", "kanji=? AND answer_signature=?", arrayOf("書", "書|書く|かく|write"), 950L);
        assertScalarLong("study_items", "mature_interval_days", "kanji=? AND answer_signature=?", arrayOf("書", "書|書く|かく|write"), 30L);
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("書", "書|書く|かく|write"), "write_kanji");
        assertScalarString("study_items", "phase", "kanji=? AND answer_signature=?", arrayOf("書", "書|書く|かく|write"), "review");
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("型", "型|かた|type"), "type_meaning");
        assertScalarString("study_items", "phase", "kanji=? AND answer_signature=?", arrayOf("型", "型|かた|type"), "new_learning");
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("水", "水|みず|water"), "kanji_meaning");
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("字", "字|じ|character"), "font_meaning");
        assertScalarString("study_items", "phase", "kanji=? AND answer_signature=?", arrayOf("字", "字|じ|character"), "review");
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("読", "読|よむ|read"), "word_reading");
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("語", "語|ご|word"), "word_reading");
    }

    private fun insertLegacyV15StudyItem(db: SQLiteDatabase, kanji: String, state: String, recognitionStage: Int, writingRemediationPending: Int, suppressedByTaskType: String, suppressedAt: Long, answerSignature: String) {
        ContentValuesBuilder.insert(db, "study_items")
                .put("kanji", kanji)
                .put("state", state)
                .put("due_at", 1000L)
                .put("stability", 2.0)
                .put("difficulty", 4.0)
                .put("total_reviews", if ("review" == state) 4 else 0)
                .put("lapses", 1)
                .put("learning_step", 0)
                .put("writing_level", 0)
                .put("recognition_stage", recognitionStage)
                .put("consecutive_failed_recognition_days", 2)
                .put("last_failed_recognition_day", 900L)
                .put("writing_remediation_pending", writingRemediationPending)
                .put("suppressed_by_task_type", suppressedByTaskType)
                .put("suppressed_at", suppressedAt)
                .put("mature_interval_days", 30)
                .put("answer_signature", answerSignature)
                .put("typing_meaning_memory", "")
                .put("kanji_meaning_memory", "")
                .put("font_meaning_memory", "")
                .put("word_reading_memory", "")
                .put("writing_remediation_memory", "")
                .put("active_token", "legacy-token")
                .put("created_at", 500L)
                .commit();
    }

    @Test
    fun testOldStudyRowsInitializeSiblingSuppressionFieldsAsUnsuppressed() {
        store.close();
        context.deleteDatabase("kanji_anki_simple.db");
        val db = context.openOrCreateDatabase("kanji_anki_simple.db", Context.MODE_PRIVATE, null)
        try {
            createLegacyV1Schema(db);
            ContentValuesBuilder.insert(db, "study_items")
                    .put("kanji", "水")
                    .put("state", "review")
                    .put("due_at", 2000L)
                    .put("stability", 3.0)
                    .put("difficulty", 5.0)
                    .put("total_reviews", 7)
                    .put("lapses", 0)
                    .put("learning_step", 0)
                    .put("writing_level", 1)
                    .put("active_token", "old-token")
                    .put("created_at", 100L)
                    .commit();
            db.setVersion(1);
        } finally {
            db.close();
        }

        store = LocalStore(context);

        assertEquals(1, count("study_items"));
        assertScalarString("study_items", "suppressed_by_task_type", "kanji=? AND answer_signature=?", arrayOf("水", ""), "");
        assertScalarLong("study_items", "suppressed_at", "kanji=? AND answer_signature=?", arrayOf("水", ""), 0L);
        assertScalarLong("study_items", "mature_interval_days", "kanji=? AND answer_signature=?", arrayOf("水", ""), 0L);
        assertScalarString("study_items", "kanji_meaning_memory", "kanji=? AND answer_signature=?", arrayOf("水", ""), "");
        assertScalarString("study_items", "font_meaning_memory", "kanji=? AND answer_signature=?", arrayOf("水", ""), "");
        assertScalarString("study_items", "word_reading_memory", "kanji=? AND answer_signature=?", arrayOf("水", ""), "");
        assertScalarString("study_items", "writing_remediation_memory", "kanji=? AND answer_signature=?", arrayOf("水", ""), "");
        assertScalarString("study_items", "rung", "kanji=? AND answer_signature=?", arrayOf("水", ""), "kanji_meaning");
        assertScalarString("study_items", "phase", "kanji=? AND answer_signature=?", arrayOf("水", ""), "new_learning");
    }

    private fun assertMigratedColumnsExist() {
        assertMigratedCardAndExampleColumns();
        assertMigratedStudyColumns();
        assertMigratedPracticeAndReviewColumns();
        assertMigratedHistoricalSyncColumns();
    }

    private fun assertMigratedCardAndExampleColumns() {
        assertTrue(hasColumn("source_cards", "fsrs_stability"));
        assertTrue(hasColumn("source_cards", "fsrs_difficulty"));
        assertTrue(hasColumn("source_cards", "fsrs_retrievability"));
        assertTrue(hasColumn("kanji_examples", "fsrs_stability"));
    }

    private fun assertMigratedStudyColumns() {
        assertTrue(hasColumn("study_items", "recognition_stage"));
        assertTrue(hasColumn("study_items", "consecutive_failed_recognition_days"));
        assertTrue(hasColumn("study_items", "last_failed_recognition_day"));
        assertTrue(hasColumn("study_items", "writing_remediation_pending"));
        assertTrue(hasColumn("study_items", "suppressed_by_task_type"));
        assertTrue(hasColumn("study_items", "suppressed_at"));
        assertTrue(hasColumn("study_items", "mature_interval_days"));
        assertTrue(hasColumn("study_items", "answer_signature"));
        assertTrue(hasColumn("study_items", "typing_meaning_memory"));
        assertTrue(hasColumn("study_items", "meaning_kanji_memory"));
        assertTrue(hasColumn("study_items", "kanji_meaning_memory"));
        assertTrue(hasColumn("study_items", "font_meaning_memory"));
        assertTrue(hasColumn("study_items", "word_reading_memory"));
        assertTrue(hasColumn("study_items", "writing_remediation_memory"));
        // Added in DB v16 for the ladder scheduler. The v16 migration does
        // a fresh-start rebuild of study_items, but the rebuilt shape must
        // include the ladder state columns.
        assertTrue(hasColumn("study_items", "rung"));
        assertTrue(hasColumn("study_items", "phase"));
        assertTrue(hasColumn("study_items", "real_pass_streak"));
        assertTrue(hasColumn("study_items", "real_again_streak"));
        assertTrue(hasColumn("study_items", "last_real_review_due_at"));
        assertTrue(hasColumn("study_items", "similar_kanji_memory"));
        assertTrue(hasColumn("study_items", "scheduler_revision"));
        assertTrue(hasColumn("study_items", "routing_version"));
        assertTrue(hasColumn("study_items", "adaptive_route_state_json"));
    }

    private fun assertMigratedPracticeAndReviewColumns() {
        assertTrue(hasColumn("similar_kanji_pairs", "source"));
        assertTrue(hasColumn("similar_kanji_choice_state", "choice_signature"));
        assertTrue(hasColumn("similar_kanji_repair_queue", "repair_kanji"));
        assertTrue(hasColumn("similar_kanji_review_log", "selected_kanji"));
        assertTrue(hasColumn("review_log", "task_type"));
        assertTrue(hasColumn("review_log", "review_day_start"));
        assertTrue(hasColumn("review_log", "hints_used"));
        assertTrue(hasColumn("review_log", "memory_before"));
        assertTrue(hasColumn("review_log", "scheduler_state_after_json"));
        assertTrue(hasColumn("review_log", "core_skill"));
        assertTrue(hasColumn("review_log", "failure_cause"));
        assertTrue(hasColumn("review_log", "evidence_source"));
        assertTrue(hasColumn("review_log", "selected_answer"));
        assertTrue(hasColumn("review_log", "correct_answer"));
        assertTrue(hasColumn("review_log", "answer_evidence_json"));
        assertTrue(hasColumn("study_task_log", "task_key"));
        assertTrue(hasColumn("study_task_log", "active_elapsed_ms"));
        assertTrue(hasIndex("review_log", "idx_review_log_reviewed_at"));
        assertTrue(hasIndex("review_log", "idx_review_log_day_reviewed"));
        assertTrue(hasIndex("review_log", "idx_review_log_kanji_reviewed"));
        assertTrue(hasIndex("review_log", "idx_review_log_rating_reviewed"));
        assertTrue(hasIndex("study_items", "idx_study_items_ladder_stats"));
        assertTrue(hasIndex("sync_kanji_snapshots", "idx_sync_kanji_snapshots_kanji_finished"));
    }

    private fun assertMigratedHistoricalSyncColumns() {
        assertTrue(hasColumn("sync_card_snapshots", "deck_id"));
        assertTrue(hasColumn("sync_card_snapshots", "model_id"));
        assertTrue(hasColumn("sync_card_snapshots", "fsrs_difficulty"));
        assertTrue(hasColumn("sync_note_snapshots", "model_id"));
        assertTrue(hasColumn("sync_note_snapshots", "deck_ids"));
        assertTrue(hasColumn("sync_note_snapshots", "extracted_kanji"));
        assertTrue(hasColumn("sync_kanji_snapshots", "weakness_score"));
        assertTrue(hasColumn("import_rule_audits", "settings_json"));
        assertTrue(hasColumn("import_decisions", "reason_text"));
        assertTrue(hasColumn("import_decisions", "rule_types"));
    }

    @Test
    fun testSettingsAndReviewTokensPersistAcrossStoreInstances() {
        store.putIntSetting("suspended_rank_cutoff", 4000);
        val request = RecordsSchedulerModels.ReviewRequest("拉", "token-1", "good", true, true, false, 0)
        store.saveReview(request, "good", 3000L);
        store.close();

        store = LocalStore(context);
        assertSyncSettingsPersistAndNormalize();
        assertReminderAndAdaptiveLoadSettingsPersist();
        assertAutoSyncSettingsPersist();
        assertReviewTokensPersist(request);
    }

    private fun assertSyncSettingsPersistAndNormalize() {
        assertEquals(4000, store.getIntSetting("suspended_rank_cutoff", 1000));
        val legacyFrequency = SyncSettings.fromStore(store)
        assertEquals("Kiku", legacyFrequency.modelName);
        assertEquals(100, legacyFrequency.suspendedRankMin);
        assertEquals(4000, legacyFrequency.suspendedRankMax);
        assertFalse(legacyFrequency.importActiveCards);
        assertTrue(legacyFrequency.importSuspendedCards);
        assertFalse(legacyFrequency.importTaggedCardsEnabled());
        assertFalse(legacyFrequency.importWeakCards);
        assertEquals(1, legacyFrequency.importMinMatchingCardsPerKanji);
        store.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, "Custom Japanese");
        store.putStringSetting(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, "Front");
        store.putStringSetting(SyncSettings.READING_FIELD_SETTING_KEY, "");
        store.putStringSetting(SyncSettings.MEANING_FIELD_SETTING_KEY, "Back");
        store.putStringSetting(SyncSettings.SENTENCE_FIELD_SETTING_KEY, "");
        store.putStringSetting(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, "");
        store.putStringSetting(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY, "");
        val customNoteType = SyncSettings.fromStore(store)
        assertEquals("Custom Japanese", customNoteType.modelName);
        assertEquals("Front", customNoteType.expressionField);
        assertEquals("", customNoteType.readingField);
        assertEquals("Back", customNoteType.meaningField);
        assertEquals("", customNoteType.sentenceField);
        assertEquals("", customNoteType.frequencyField);
        assertEquals("", customNoteType.frequencySortField);
        store.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, "   ");
        assertEquals("Kiku", SyncSettings.fromStore(store).modelName);
        store.putIntSetting("suspended_rank_min", 250);
        store.putIntSetting("suspended_rank_max", 3000);
        val rangeFrequency = SyncSettings.fromStore(store)
        assertEquals(250, rangeFrequency.suspendedRankMin);
        assertEquals(3000, rangeFrequency.suspendedRankMax);
        assertEquals(RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS, SyncSettings.fromStore(store).writingTriggerMissDays);
        store.putIntSetting("writing_trigger_miss_days", 4);
        store.putIntSetting(SyncSettings.RECOGNITION_PROMOTION_PASSES_SETTING_KEY, 5);
        store.putIntSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, 30);
        store.putIntSetting(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, 6);
        val ladderSettings = SyncSettings.fromStore(store)
        assertEquals(4, ladderSettings.writingTriggerMissDays);
        assertEquals(5, ladderSettings.recognitionPromotionPasses);
        assertEquals(30, ladderSettings.ladderPromotionIntervalDays);
        assertEquals(6, ladderSettings.ladderDemotionFailStreak);
        store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1);
        store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 1);
        store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 0);
        store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "");
        store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 0);
        store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY);
        store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES);
        store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI);
        val migratedOldDefaults = SyncSettings.fromStore(store)
        assertFalse(migratedOldDefaults.importActiveCards);
        assertTrue(migratedOldDefaults.importSuspendedCards);
        assertEquals(0, store.getIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1));
        store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1);
        store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, 2);
        val customizedImportDefaults = SyncSettings.fromStore(store)
        assertTrue(customizedImportDefaults.importActiveCards);
        assertEquals(2, customizedImportDefaults.importMinMatchingCardsPerKanji);
        store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 0);
        store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 1);
        store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 1);
        store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "focus, weak focus");
        store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 1);
        store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, 99.0);
        store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, -5);
        store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, 0);
        val importSettings = SyncSettings.fromStore(store)
        assertFalse(importSettings.importActiveCards);
        assertTrue(importSettings.importSuspendedCards);
        assertTrue(importSettings.importTaggedCardsEnabled());
        assertEquals(listOf("focus", "weak"), importSettings.importTags);
        assertTrue(importSettings.importWeakCards);
        assertClose(10.0, importSettings.importWeakFsrsDifficultyThreshold);
        assertEquals(1, importSettings.importWeakLapsesThreshold);
        assertEquals(1, importSettings.importMinMatchingCardsPerKanji);
    }

    private fun assertReminderAndAdaptiveLoadSettingsPersist() {
        val defaults = store.reminderSettings()
        assertFalse(defaults.enabled);
        assertEquals(19, defaults.hour);
        assertEquals(0, defaults.minute);
        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, store.adaptiveLoadMode());
        assertEquals(20, store.adaptiveLoadWorkPercent());
        store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, store.adaptiveLoadMode());
        store.saveAdaptiveLoadWorkPercent(23);
        assertEquals(25, store.adaptiveLoadWorkPercent());
        store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_AUTO);
        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, store.adaptiveLoadMode());

        assertEquals(0, store.studyAheadMinutes());
        store.saveStudyAheadMinutes(15);
        assertEquals(15, store.studyAheadMinutes());
        store.saveStudyAheadMinutes(-5);
        assertEquals(0, store.studyAheadMinutes());
        store.saveStudyAheadMinutes(99999);
        assertEquals(SettingsInputRules.MAX_STUDY_AHEAD_MINUTES, store.studyAheadMinutes());
        store.saveStudyAheadMinutes(0);
        assertEquals(0, store.studyAheadMinutes());

        store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 8, 30));
        val reminder = store.reminderSettings()
        assertTrue(reminder.enabled);
        assertEquals(8, reminder.hour);
        assertEquals(30, reminder.minute);
        assertEquals("08:30", reminder.displayTime());
        store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 99, -4));
        val clampedReminder = store.reminderSettings()
        assertEquals(23, clampedReminder.hour);
        assertEquals(0, clampedReminder.minute);
    }

    private fun assertAutoSyncSettingsPersist() {
        val autoDefaults = store.autoSyncSettings()
        assertFalse(autoDefaults.configured);
        assertFalse(autoDefaults.enabled);
        assertEquals(19, autoDefaults.hour);
        assertEquals(0, autoDefaults.minute);
        assertTrue(store.activateAutoSyncAfterFirstSuccess());
        assertFalse(store.activateAutoSyncAfterFirstSuccess());
        val activeAuto = store.autoSyncSettings()
        assertTrue(activeAuto.configured);
        assertTrue(activeAuto.enabled);
        assertEquals("19:00", activeAuto.displayTime());
        store.recordAutoSyncAttempt(5000L, false);
        val failedAuto = store.autoSyncSettings()
        assertEquals(5000L, failedAuto.lastAttemptAt);
        assertEquals(0L, failedAuto.lastSuccessAt);
        store.recordAutoSyncAttempt(6000L, true);
        store.markAutoSyncScheduled(9000L);
        val successfulAuto = store.autoSyncSettings()
        assertEquals(6000L, successfulAuto.lastAttemptAt);
        assertEquals(6000L, successfulAuto.lastSuccessAt);
        assertEquals(9000L, successfulAuto.nextRunAt);
        store.setAutoSyncEnabled(false);
        val disabledAuto = store.autoSyncSettings()
        assertTrue(disabledAuto.configured);
        assertFalse(disabledAuto.enabled);
        store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(false, true, 99, -4, -1L, -2L, -3L));
        val clampedAuto = store.autoSyncSettings()
        assertFalse(clampedAuto.configured);
        assertFalse(clampedAuto.enabled);
        assertEquals(23, clampedAuto.hour);
        assertEquals(0, clampedAuto.minute);
        assertEquals(0L, clampedAuto.lastAttemptAt);
        assertEquals(0L, clampedAuto.lastSuccessAt);
        assertEquals(0L, clampedAuto.nextRunAt);
    }

    private fun assertReviewTokensPersist(request: RecordsSchedulerModels.ReviewRequest) {
        val tokens = store.consumedTokens()
        assertEquals(1, tokens.size);
        assertEquals("token-1", tokens.get(0));

        store.saveReview(request, "easy", 4000L);
        assertEquals(1, store.consumedTokens().size);
        assertEquals(1, store.studiedKanjiSince(0L).size);
    }

    @Test
    fun testRichReviewHistoryStoresTaskContextAndSchedulerState() {
        val before = RecordsStudyModels.StudyItem(
                "裂",
                "review",
                1000L,
                2.0,
                6.0,
                3,
                1,
                2,
                1,
                "review-token",
                500L
        ).withAnswerSignature("裂|分裂|ぶんれつ|split")
        val after = RecordsStudyModels.StudyItem(
                "裂",
                "review",
                90_000_000L,
                4.0,
                5.8,
                4,
                1,
                2,
                2,
                null,
                500L
        ).withAnswerSignature("裂|分裂|ぶんれつ|split")
        val request = RecordsSchedulerModels.ReviewRequest(
                "裂",
                "review-token",
                "good",
                true,
                true,
                false,
                false,
                2,
                "kanji_meaning",
                "裂|分裂|ぶんれつ|split",
                "Imported from suspended cards"
        )

        store.saveReview(request, "good", 4000L, before, after);

        val cursor = store.getReadableDatabase().rawQuery(
                "SELECT task_type, answer_signature, prompt, hints_used, writing_clean, memory_before, memory_after, scheduler_state_after_json FROM review_log WHERE token=?",
                arrayOf("review-token")
        )
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("kanji_meaning", cursor.getString(0));
            assertEquals("裂|分裂|ぶんれつ|split", cursor.getString(1));
            assertEquals("Imported from suspended cards", cursor.getString(2));
            assertEquals(2, cursor.getInt(3));
            assertEquals(0, cursor.getInt(4));
            assertTrue(cursor.getString(5).contains("review"));
            assertTrue(cursor.getString(6).contains("review"));
            assertTrue(cursor.getString(7).contains("\"stability\":4.0"));
            assertTrue(cursor.getString(7).contains("\"recognition_stage\":0"));
        } finally {
            cursor.close();
        }
    }

    @Test
    fun testKanjiImpactBaselineStartsWhenKaniStartsTracking() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val firstNote = sourceKikuNote(101L, "裂語", "れつご", "split word", "裂語を見た。")
        val secondNote = sourceKikuNote(102L, "裂文", "れつぶん", "split sentence", "裂文を見た。")

        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(firstNote, secondNote),
                        listOf(
                                kikuCard(1001L, 101L).history(80, 50, 0).fsrs(null, 4.0, 0.95).build(),
                                kikuCard(1002L, 102L).history(80, 50, 0).fsrs(null, 4.0, 0.95).build()
                        )
                ),
                emptyList(),
                emptyList(),
                settings,
                1000L,
                2000L,
                null
        );

        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(firstNote, secondNote),
                        listOf(
                                kikuCard(1001L, 101L).history(5, 20, 8).fsrs(null, 7.2, 0.62).build(),
                                kikuCard(1002L, 102L).history(5, 20, 8).fsrs(null, 7.2, 0.62).build()
                        )
                ),
                emptyList(),
                listOf(row("裂", 0)),
                settings,
                3000L,
                4000L,
                null
        );

        store.saveReview(review("裂", "impact-baseline"), "good", 4500L);

        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(firstNote, secondNote),
                        listOf(
                                kikuCard(1001L, 101L).history(40, 30, 2).fsrs(null, 5.8, 0.84).build(),
                                kikuCard(1002L, 102L).history(40, 30, 2).fsrs(null, 5.8, 0.84).build()
                        )
                ),
                emptyList(),
                listOf(row("裂", 2)),
                settings,
                5000L,
                6000L,
                null
        );

        val report = KanjiImpactReportStore(store).report()

        assertEquals(1, report.helpedCount);
        assertEquals(0, report.notHelpingCount);
        assertEquals(1, report.rows.size);
        val impact = report.rows.get(0)
        assertEquals("裂", impact.kanji);
        assertEquals(KanjiImpactAnalyzer.BUCKET_HELPED, impact.bucket);
        assertClose(7.2, impact.baselineDifficulty);
        assertClose(5.8, impact.currentDifficulty);
        assertClose(0.62, impact.baselineRetention);
        assertClose(0.84, impact.currentRetention);
    }

    @Test
    fun testStudyOutcomeStatsRankWeaknessSupportAndLadderEvidence() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        saveSingleRowSync(rowWithStats("拉", 90, 0), emptyList(), 1000L);
        store.saveReview(review("拉", "outcome-lower-weakness"), "good", 1500L);
        saveSingleRowSync(rowWithStats("拉", 40, 2), emptyList(), 2500L);
        saveSingleRowSync(rowWithStats("提", 60, 1), emptyList(), 3000L);
        store.saveReview(review("提", "outcome-missing-after"), "again", 3500L);
        val promotionReady = RecordsStudyModels.StudyItem("拉", "review", 0L, 2.0, 4.0, 3, 0, 0, 0, null, 1000L)
                .withLadderProgress(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 3, 0, 1000L)
                .copyBuilder().matureIntervalDays(22).build()
        val demotionReady = RecordsStudyModels.StudyItem("提", "review", 0L, 2.0, 4.0, 3, 0, 0, 0, null, 1000L)
                .withLadderProgress(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0, 3, 1000L)
        val retired = RecordsStudyModels.StudyItem("謎", "retired", 0L, 2.0, 4.0, 3, 0, 0, 0, null, 1000L)
                .withLadderProgress(RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 0, 3, 3, 1000L)
        store.putIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, 3);
        store.replaceStudyItems(listOf(promotionReady, demotionReady, retired), 4L, 4000L, settings);

        val stats = store.kaniOutcomeStats()

        assertEquals(1, stats.weakKanjiImproved.improvedCount);
        assertEquals("拉", stats.weakKanjiImproved.examples.get(0).kanji);
        assertClose(0.90, stats.weakKanjiImproved.averageBeforeWeakness);
        assertClose(0.40, stats.weakKanjiImproved.averageAfterWeakness);
        assertEquals(1, stats.matureSupportGained.gainedSupportCount);
        assertEquals(2, stats.matureSupportGained.matureSupportGained);
        assertEquals(1, stats.matureSupportGained.firstSupportCount);
        assertEquals(2, stats.ladderHealth.totalActiveItems);
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals(1, stats.ladderHealth.promotionReadyCount);
        assertEquals(1, stats.ladderHealth.demotionRiskCount);
        assertEquals(1, stats.ladderHealth.demotionReadyCount);
    }

    @Test
    fun testKanjiImpactReportUsesSameCardMetricsAndCountsNewCards() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val baselineOne = sourceKikuNote(201L, "鈍語", "どんご", "dull word", "鈍語を見た。")
        val baselineTwo = sourceKikuNote(202L, "鈍文", "どんぶん", "dull sentence", "鈍文を見た。")
        val newCard = sourceKikuNote(203L, "鈍例", "どんれい", "dull example", "鈍例を見た。")

        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(baselineOne, baselineTwo),
                        listOf(
                                kikuCard(2001L, 201L).history(3, 10, 2).fsrs(null, 6.0, 0.70).build(),
                                kikuCard(2002L, 202L).history(3, 10, 2).fsrs(null, 6.0, 0.70).build()
                        )
                ),
                emptyList(),
                listOf(rowWithStats("鈍", 70, 0)),
                settings,
                1000L,
                2000L,
                null
        );
        store.saveReview(review("鈍", "impact-not-helping"), "again", 2500L);
        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(baselineOne, baselineTwo, newCard),
                        listOf(
                                kikuCard(2001L, 201L).history(3, 20, 4).fsrs(null, 6.1, 0.71).build(),
                                kikuCard(2002L, 202L).history(3, 20, 4).fsrs(null, 6.1, 0.71).build(),
                                kikuCard(2003L, 203L).history(1, 1, 0).fsrs(null, 4.0, 0.90).build()
                        )
                ),
                emptyList(),
                listOf(rowWithStats("鈍", 65, 0)),
                settings,
                3000L,
                4000L,
                null
        );

        val impact = impactRow(KanjiImpactReportStore(store).report(), "鈍")

        assertEquals(KanjiImpactAnalyzer.BUCKET_NOT_HELPING, impact.bucket);
        assertEquals(2, impact.sameCardCount);
        assertEquals(1, impact.newCardCount);
        assertEquals(3, impact.currentCardCount);
        assertEquals(1, impact.reviewCount);
        assertClose(6.0, impact.baselineDifficulty);
        assertClose(6.1, impact.currentDifficulty);
    }

    @Test
    fun testKanjiImpactReportKeepsReviewOnlyKanjiAsNeedsMoreCards() {
        store.saveReview(review("孤", "impact-review-only"), "good", 1000L);
        saveSingleRowSync(row("別", 0), listOf(suspendedImport("別")), 2000L);

        val report = KanjiImpactReportStore(store).report()

        assertEquals(0, report.helpedCount);
        assertEquals(0, report.notHelpingCount);
        assertTrue(report.needsMoreCardsCount >= 2);
        assertEquals(KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS, impactRow(report, "孤").bucket);
        assertEquals(1, impactRow(report, "孤").reviewCount);
        assertEquals(KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS, impactRow(report, "別").bucket);
    }

    @Test
    fun testKanjiImpactReportUsesFirstSnapshotWhenNoKaniSignalExists() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertKanjiSnapshot(db, 1L, 2000L, "泳", 2, 0, 0, 80, "active", 2, 0, null, null, null);

        val impact = impactRow(KanjiImpactReportStore(store).report(), "泳")

        assertEquals(0, impact.reviewCount);
        assertEquals(2, impact.currentCardCount);
        assertEquals(0, impact.sameCardCount);
    }

    @Test
    fun testKanjiImpactReportSameCardMetricsIncludeSuspendedFsrsCards() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertSyncRun(db, 2L, 3000L, 4000L);
        insertKanjiSnapshot(db, 1L, 2000L, "泳", 1, 1, 1, 80, "suspended_archive", 1, 1, 2.0, 6.0, 0.50);
        insertKanjiSnapshot(db, 2L, 4000L, "泳", 1, 1, 1, 60, "suspended_archive", 1, 1, 3.0, 5.0, 0.75);
        insertNoteSnapshot(db, 1L, 2000L, 100L, "泳");
        insertNoteSnapshot(db, 2L, 4000L, 100L, "泳");
        insertCardSnapshot(db, 1L, 2000L, 10L, 100L, true, true, 30, 8, 1, 2.0, 6.0, 0.50);
        insertCardSnapshot(db, 2L, 4000L, 10L, 100L, true, true, 40, 12, 1, 3.0, 5.0, 0.75);
        insertCardSnapshot(db, 2L, 4000L, 11L, 100L, false, false, 1, 0, 0, null, null, null);
        store.saveReview(review("泳", "impact-direct-review"), "good", 1500L);

        val impact = impactRow(KanjiImpactReportStore(store).report(), "泳")

        assertEquals(1, impact.sameCardCount);
        assertEquals(1, impact.newCardCount);
        assertEquals(2, impact.currentCardCount);
        assertClose(6.0, impact.baselineDifficulty);
        assertClose(5.0, impact.currentDifficulty);
        assertClose(0.50, impact.baselineRetention);
        assertClose(0.75, impact.currentRetention);
    }

    @Test
    fun testKanjiImpactReportFallsBackToSnapshotBeforeFirstReview() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertKanjiSnapshot(db, 1L, 2000L, "遅", 1, 0, 0, 72, "active", 1, 0, 2.5, 5.5, 0.60);
        store.saveReview(review("遅", "impact-after-only-sync"), "good", 3000L);

        val impact = impactRow(KanjiImpactReportStore(store).report(), "遅")

        assertEquals(1, impact.reviewCount);
        assertEquals(1, impact.currentCardCount);
        assertEquals(0, impact.sameCardCount);
        assertClose(5.5, impact.baselineDifficulty);
    }

    @Test
    fun testKanjiImpactReportTreatsMismatchedHistoricalCardsAsNewCurrentCards() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertSyncRun(db, 2L, 3000L, 4000L);
        insertKanjiSnapshot(db, 1L, 2000L, "替", 1, 0, 0, 80, "active", 1, 0, null, 6.0, null);
        insertKanjiSnapshot(db, 2L, 4000L, "替", 2, 0, 0, 70, "active", 2, 0, null, 5.0, null);
        insertNoteSnapshot(db, 1L, 2000L, 300L, "替");
        insertNoteSnapshot(db, 2L, 4000L, 400L, "替");
        insertCardSnapshot(db, 1L, 2000L, 30L, 300L, false, false, 5, 2, 0, null, 6.0, null);
        insertCardSnapshot(db, 2L, 4000L, 40L, 400L, false, false, 1, 0, 0, null, 5.0, null);
        insertCardSnapshot(db, 2L, 4000L, 41L, 400L, false, false, 1, 0, 0, null, 5.0, null);
        store.saveReview(review("替", "impact-no-common-cards"), "again", 1500L);

        val impact = impactRow(KanjiImpactReportStore(store).report(), "替")

        assertEquals(0, impact.sameCardCount);
        assertEquals(2, impact.newCardCount);
        assertEquals(2, impact.currentCardCount);
        assertEquals(1, impact.reviewCount);
    }

    @Test
    fun testKanjiImpactReportKeepsImportWithoutHistoricalSnapshotAsNeedsMoreCards() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertKanjiSnapshot(db, 1L, 2000L, "別", 1, 0, 0, 80, "active", 1, 0, null, null, null);
        ContentValuesBuilder.insert(db, "suspended_imports")
                .put("kanji", "零")
                .put("rank_known", 0)
                .put("cutoff_used", 2500)
                .put("first_imported_at", 0L)
                .put("last_seen_sync_id", 1L)
                .commit();

        val impact = impactRow(KanjiImpactReportStore(store).report(), "零")

        assertEquals(KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS, impact.bucket);
        assertEquals(0, impact.currentCardCount);
        assertEquals(0, impact.reviewCount);
    }

    @Test
    fun testStudyItemsPersistSeparateAnswerSignaturesForSameKanji() {
        val oldWord = RecordsStudyModels.StudyItem("拉", "review", 1000L, 2.0, 4.0, 2, 0, 2, 1, null, 1000L)
                .withAnswerSignature("拉|拉麺|らーめん|ramen")
        val newWord = RecordsStudyModels.StudyItem("拉", "new", 2000L, 0.4, 5.0, 0, 0, 0, 0, null, 2000L)
                .withAnswerSignature("拉|拉致|らち|abduction")

        store.replaceStudyItems(listOf(oldWord, newWord));
        var items = store.studyItems()

        assertEquals(2, items.size);
        assertEquals("拉|拉麺|らーめん|ramen", items.get(0).answerSignature);
        assertEquals("拉|拉致|らち|abduction", items.get(1).answerSignature);

        store.saveStudyItem(oldWord.withSuppression("word_reading", 3000L, 31));
        items = store.studyItems();
        assertEquals(2, items.size);
        assertEquals("word_reading", items.get(0).suppressedByTaskType);
        assertEquals("", items.get(1).suppressedByTaskType);
    }

    @Test
    fun testReviewStatsAndSchedulerParametersPersist() {
        val firstReviewAt = 86_400_000L
        store.saveReview(RecordsSchedulerModels.ReviewRequest("拉", "token-a", "again", true, false, false, 0), "again", firstReviewAt);
        store.saveReview(RecordsSchedulerModels.ReviewRequest("麺", "token-b", "good", true, true, false, 0), "good", firstReviewAt + 1000L);
        store.saveReview(RecordsSchedulerModels.ReviewRequest("泳", "token-c", "easy", false, false, true, 0), "easy", firstReviewAt + 2000L);
        val stats = store.reviewStatsSince(0L)
        assertEquals(3, stats.total);
        assertEquals(1, stats.again);
        assertEquals(1, stats.easy);
        assertEquals(1, stats.good);
        assertEquals(2, stats.writingRequired);
        assertEquals(1, stats.writingFailed);
        val impact = store.studyImpactStats()
        assertEquals(3, impact.totalReviews);
        assertEquals(3, impact.distinctReviewedKanji);
        assertEquals(2, impact.writingRequired);
        assertEquals(1, impact.writingPassed);
        assertEquals(1, impact.writingFailed);
        assertEquals(1, impact.manualOverrides);
        assertScalarLong(
                "review_log",
                "review_day_start",
                "token=?",
                arrayOf("token-a"),
                localDayStart(firstReviewAt)
        );

        val tuned = RecordsSchedulerModels.SchedulerParameters.defaults()
                .withTargetRetention(0.92)
                .withFrequencyRetention(true, "1-500=95%")
        store.saveSchedulerParameters(tuned);
        val loaded = store.schedulerParameters()
        assertClose(0.92, loaded.targetRetention);
        assertTrue(loaded.frequencyRetentionEnabled);
        assertEquals("1-500=95%", loaded.frequencyRetentionRanges);
    }

    @Test
    fun testStudyLadderSettingsPersist() {
        val settings = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
                .moveRung(RecordsBase.LadderRung.WORD_READING, -6)

        store.saveStudyLadderSettings(settings);
        val loaded = store.studyLadderSettings()

        assertEquals(RecordsBase.LadderRung.WORD_READING, loaded.orderedRungs.get(0));
        assertFalse(loaded.isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertTrue(loaded.isEnabled(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals(settings.orderText(), loaded.orderText());
        assertEquals(settings.enabledText(), loaded.enabledText());
    }

    @Test
    fun testAdaptiveLoadMaxItemsDefaultsAndClamps() {
        assertEquals(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS, store.adaptiveLoadMaxItems());

        store.saveAdaptiveLoadMaxItems(0);
        assertEquals(AdaptiveLoadPlanner.MIN_MAX_ITEMS, store.adaptiveLoadMaxItems());

        store.saveAdaptiveLoadMaxItems(99);
        assertEquals(AdaptiveLoadPlanner.MAX_MAX_ITEMS, store.adaptiveLoadMaxItems());

        store.saveAdaptiveLoadMaxItems(5);
        assertEquals(5, store.adaptiveLoadMaxItems());
    }

    @Test
    fun testStudyTaskAnsweredLogSuppressesDuplicateTaskKeys() {
        assertFalse(store.recordStudyTaskAnswered("", "拉", "kanji_meaning", 1000L, 2000L, 12_000L, "good"));
        assertFalse(store.recordStudyTaskAnswered(null, "拉", "kanji_meaning", 1000L, 2000L, 12_000L, "good"));
        assertTrue(store.recordStudyTaskAnswered("task-1", "拉", "kanji_meaning", 1000L, 2000L, 12_000L, "good"));
        assertFalse(store.recordStudyTaskAnswered("task-1", "拉", "kanji_meaning", 1000L, 3000L, 24_000L, "easy"));
        assertTrue(store.recordStudyTaskAnswered("task-2", null, null, 1000L, 2000L, -1L, null));

        val stats = store.studyTaskTimeStats(2500L)
        assertEquals(12_000L, stats.todayMillis);
        assertEquals(12_000L, stats.lastSevenDaysMillis);
        assertEquals(2, stats.answeredTasks);
        assertEquals(6_000L, stats.averageMillisPerTask());
        assertEquals(2, count("study_task_log"));
    }

    @Test
    fun testStudyTaskTimeStatsUseLocalDayBoundariesAndClampOutliers() {
        val today = localDayStart(System.currentTimeMillis())
        val tomorrow = moveLocalDays(today, 1)
        val yesterday = moveLocalDays(today, -1)
        val sixDaysAgo = moveLocalDays(today, -6)
        val sevenDaysAgo = moveLocalDays(today, -7)

        store.recordStudyTaskAnswered("today-start", "拉", "kanji_meaning", today - 500L, today, 30_000L, "good");
        store.recordStudyTaskAnswered("today-end", "提", BridgeScheduler.TASK_TYPE_MEANING, today, tomorrow - 1L, 90_000L, "good");
        store.recordStudyTaskAnswered("yesterday", "謎", "similar_choice", yesterday, yesterday + 60_000L, 120_000L, "wrong");
        store.recordStudyTaskAnswered("six-days", "麺", "similar_writing", sixDaysAgo, sixDaysAgo + 60_000L, 180_000L, "passed");
        store.recordStudyTaskAnswered("seven-days", "確", "kanji_meaning", sevenDaysAgo, sevenDaysAgo + 60_000L, 240_000L, "good");
        store.recordStudyTaskAnswered("clamped", "曜", "word_reading", today, today + 60_000L, 31L * 60L * 1000L, "easy");

        val stats = store.studyTaskTimeStats(today + 12 * 60_000L)

        assertEquals(30_000L + 90_000L + 30L * 60L * 1000L, stats.todayMillis);
        assertEquals(30_000L + 90_000L + 120_000L + 180_000L + 30L * 60L * 1000L, stats.lastSevenDaysMillis);
        assertEquals(5, stats.answeredTasks);
        assertEquals(stats.lastSevenDaysMillis / 5, stats.averageMillisPerTask());
    }

    @Test
    fun testNewPerDayDeckLimitPersistsIntoSyncSettings() {
        store.putIntSetting(SyncSettings.NEW_PER_DAY_SETTING_KEY, 37);
        store.close();
        store = LocalStore(context);

        val settings = SyncSettings.fromStore(store)

        assertEquals(37, settings.newPerDay);
    }

    @Test
    fun testNewPerDayDeckLimitIsBoundedInSyncSettings() {
        store.putIntSetting(SyncSettings.NEW_PER_DAY_SETTING_KEY, 2000);

        val settings = SyncSettings.fromStore(store)

        assertEquals(999, settings.newPerDay);
    }

    @Test
    fun testLearningStepSettingsPersist() {
        store.saveLearningStepSettings(RecordsSchedulerModels.LearningStepSettings(
                listOf(2, 15),
                listOf(20)
        ));
        val settings = store.learningStepSettings()
        assertEquals(listOf(2, 15), settings.newStepsMinutes);
        assertEquals(listOf(20), settings.reviewStepsMinutes);
    }

    @Test
    fun testAutoUpdateStatusPersistsAndClearPendingNormalizesNulls() {
        val defaults = store.autoUpdateStatus()
        assertTrue(defaults.enabled);
        assertEquals(0L, defaults.lastCheckAtMillis);
        assertEquals("No automatic update check has run yet.", defaults.lastResult);
        assertFalse(defaults.hasPendingUpdate());

        store.saveAutoUpdateEnabled(false);
        store.recordAutoUpdateResult(1234L, null, null, "kani-v9.apk", null);
        store.close();
        store = LocalStore(context);

        val pending = store.autoUpdateStatus()
        assertFalse(pending.enabled);
        assertEquals(1234L, pending.lastCheckAtMillis);
        assertEquals("", pending.lastResult);
        assertEquals("", pending.lastVersion);
        assertEquals("kani-v9.apk", pending.pendingApkName);
        assertEquals("", pending.pendingMessage);
        assertTrue(pending.hasPendingUpdate());

        store.clearPendingAutoUpdate(null);
        val cleared = store.autoUpdateStatus()
        assertEquals("", cleared.lastResult);
        assertEquals("", cleared.pendingApkName);
        assertEquals("", cleared.pendingMessage);
        assertFalse(cleared.hasPendingUpdate());
    }

    @Test
    fun testInstallPermissionPromptStatePersistsAndNormalizesNulls() {
        assertFalse(store.installPermissionPromptShown());
        assertEquals("", store.installPermissionPromptLastVersion());

        store.recordInstallPermissionPrompted("v9.9.9");
        store.close();
        store = LocalStore(context);

        assertTrue(store.installPermissionPromptShown());
        assertEquals("v9.9.9", store.installPermissionPromptLastVersion());

        store.recordInstallPermissionPrompted(null);
        assertTrue(store.installPermissionPromptShown());
        assertEquals("", store.installPermissionPromptLastVersion());
    }

    @Test
    fun testGuardInputsDoNotMutateDurableState() {
        assertEquals(0, count("similar_kanji_pairs"));
        store.rebuildSimilarKanjiPairs(null, 1000L);
        assertEquals(0, count("similar_kanji_pairs"));
        assertTrue(store.allLocalSimilarPairs().isEmpty());
        assertTrue(store.similarPairsForKanji(null).isEmpty());
        assertTrue(store.similarPairsForKanji("").isEmpty());

        val noChoice = store.submitSimilarChoice(null, "拉", 1100L)
        assertFalse(noChoice.correct);
        assertEquals("拉", noChoice.selectedKanji);
        assertTrue(noChoice.repairKanji.isEmpty());
        assertEquals(0, count("similar_kanji_review_log"));
        assertEquals(0, store.dueSimilarWritingRepairTaskCount(1200L));

        store.setKanjiLocallySuspended(null, true, 1300L);
        store.setKanjiLocallySuspended("", true, 1300L);
        assertTrue(store.locallySuspendedKanji().isEmpty());
    }

    @Test
    fun testInventoryFilteringSearchAndOrphanSuspendedSources() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(
                                sourceKikuNote(1L, "拉語", "ら", "ramen radical gap", "拉を見た。"),
                                sourceKikuNote(2L, "提語", "てい", "carry", "提を見た。")
                        ),
                        listOf(
                                kikuCard(10L, 1L).build(),
                                kikuCard(20L, 2L).build()
                        )
                ),
                listOf(suspendedImport("拉")),
                listOf(row("拉", 0), row("提", 0)),
                settings,
                1000L,
                2000L,
                null
        );

        assertNotNull(store.rowForKanji("拉"));
        assertNull(store.rowForKanji("孤"));
        assertNull(store.rowForKanji(null));
        assertNull(store.inventoryItemForKanji(null));
        assertFalse(store.isKanjiLocallySuspended("拉"));
        store.setKanjiLocallySuspended("拉", true, 3500L);
        assertTrue(store.isKanjiLocallySuspended("拉"));
        assertEquals(1, store.activeDashboardRows().size);
        assertEquals("提", store.activeDashboardRows().get(0).kanji);
        assertContainsKanji(store.searchKanjiInventory(null), "拉");
        assertContainsKanji(store.searchKanjiInventory("ramen"), "拉");

        ContentValuesBuilder.insert(store.getWritableDatabase(), "suspended_sources")
                .put("kanji", "孤")
                .put("card_id", 999L)
                .put("note_id", 99L)
                .put("expression", "孤例")
                .put("reading", "こ")
                .put("meaning", "orphan")
                .put("sentence", "孤例を見た。")
                .put("sync_id", 99L)
                .commit();

        val imports = store.suspendedImports()
        assertEquals(1, imports.size);
        assertEquals("拉", imports.get(0).kanji);
        assertEquals(1, imports.get(0).sources.size);
    }

    @Test
    fun testSimilarChoiceFallbackAndRepairEdgeStates() {
        val submittedOnly = RecordsImportModels.SimilarKanjiChoiceCard(
                "拉",
                "pull",
                listOf("拉", "提", "謎"),
                "拉|提|謎",
                0L,
                0L,
                0L,
                0,
                0
        )

        val wrong = store.submitSimilarChoice(submittedOnly, "提", 1000L)

        assertFalse(wrong.correct);
        assertEquals(listOf("拉", "提"), wrong.repairKanji);
        assertEquals(1, count("similar_kanji_review_log"));
        assertEquals(0, count("similar_kanji_repair_queue"));
        assertNull(store.nextDueInventorySimilarChoice(setOf("拉"), 1000L));
        assertNull(store.dueSimilarChoiceForActiveTarget("", 1000L));

        // Manually seed a legacy row so the compatibility drain API remains
        // covered without implying that submitSimilarChoice still enqueues it.
        insertLegacySimilarWritingRepair("拉", "拉", "拉|提|謎", "提", "pull", 1000L, 1000L)
        assertEquals(1, count("similar_kanji_repair_queue"));
        assertNull(store.nextDueSimilarWritingRepair(999L));
        store.saveSimilarWritingRepair(null);
        store.saveSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair(0L, "拉", "拉", "拉|提|謎", "提", "pull", "pending", 1000L, "", 0, 1000L, 1000L, 0L));

        val repair = requireNotNull(store.nextDueSimilarWritingRepair(1000L))
        assertFalse(store.finishSimilarWritingRepair(99_999L, "", true, 1100L));
        store.saveSimilarWritingRepair(repair.withToken("expected-token", 1200L));
        assertFalse(store.finishSimilarWritingRepair(repair.id, "wrong-token", true, 1300L));
        assertTrue(store.finishSimilarWritingRepair(repair.id, "expected-token", false, 1400L));

        val retry = requireNotNull(store.nextDueSimilarWritingRepair(1400L))
        assertEquals(1, retry.attempts);
        assertTrue(store.finishSimilarWritingRepair(retry.id, "", true, 1500L));
    }

    @Test
    fun testInventorySimilarChoiceReturnsNullWhenAllDueCandidatesAreUnavailable() {
        val db = store.getWritableDatabase()
        ContentValuesBuilder.insert(db, "similar_kanji_choice_state")
                .put("target_kanji", "拉")
                .put("choice_signature", "拉|提|謎")
                .put("primary_meaning", "pull")
                .put("choices", "拉\t提\t謎")
                .put("due_at", 1000L)
                .put("passed_at", 0L)
                .put("last_reviewed_at", 0L)
                .put("correct_count", 0)
                .put("wrong_count", 0)
                .put("active_token", "")
                .put("first_seen_at", 1000L)
                .put("last_seen_at", 1000L)
                .commit();
        ContentValuesBuilder.insert(db, "similar_kanji_choice_state")
                .put("target_kanji", "提")
                .put("choice_signature", "提|拉|謎")
                .put("primary_meaning", "carry")
                .put("choices", "提\t拉\t謎")
                .put("due_at", 1000L)
                .put("passed_at", 0L)
                .put("last_reviewed_at", 0L)
                .put("correct_count", 0)
                .put("wrong_count", 0)
                .put("active_token", "")
                .put("first_seen_at", 1000L)
                .put("last_seen_at", 1000L)
                .commit();
        ContentValuesBuilder.insert(db, "similar_kanji_repair_queue")
                .put("target_kanji", "提")
                .put("repair_kanji", "拉")
                .put("choice_signature", "提|拉|謎")
                .put("wrong_selection", "拉")
                .put("prompt_meaning", "carry")
                .put("status", "pending")
                .put("due_at", 1000L)
                .put("active_token", "")
                .put("attempts", 0)
                .put("created_at", 1000L)
                .put("updated_at", 1000L)
                .put("completed_at", 0L)
                .commit();

        assertNull(store.nextDueInventorySimilarChoice(setOf("拉"), 1000L));
    }

    @Test
    fun testPublicSimilarPairRebuildKeepsOnlyLocalInventoryPairs() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(
                                sourceKikuNote(1L, "拉語", "ら", "pull", "拉語を見た。"),
                                sourceKikuNote(2L, "提語", "てい", "carry", "提語を見た。")
                        ),
                        listOf(
                                kikuCard(10L, 1L).build(),
                                kikuCard(20L, 2L).build()
                        )
                ),
                emptyList(),
                listOf(row("拉", 0), row("提", 0)),
                settings,
                1000L,
                2000L,
                null
        );

        val index = SimilarKanjiIndex.parseTsv(StringReader("拉\t提\tfixture\n拉\t謎\tfixture\n"))
        store.rebuildSimilarKanjiPairs(index, 2500L);

        assertEquals(1, store.allLocalSimilarPairs().size);
        assertTrue(store.hasSimilarLocalPair("拉", "提"));
        assertFalse(store.hasSimilarLocalPair("", "提"));
        assertFalse(store.hasSimilarLocalPair("拉", ""));
        assertFalse(store.hasSimilarLocalPair("拉", "拉"));
        assertNotEquals("拉", requireNotNull(store.nextDueInventorySimilarChoice(setOf("拉"), 2500L)).targetKanji);
        assertNull(store.nextDueInventorySimilarChoice(hashSetOf("拉", "提"), 2500L));
    }

    @Test
    fun testTimelineHelperBranchesAndSyncStatusHeadlines() {
        val db = store.getWritableDatabase()
        store.addNullableColumn(db, "settings", "optional_sync_note", "TEXT");
        store.addNullableColumn(db, "settings", "optional_sync_note", "TEXT");
        try {
            store.addNullableColumn(db, "missing_table", "optional_sync_note", "TEXT");
            throw AssertionError("Expected invalid ALTER TABLE to throw");
        } catch (expected: RuntimeException) {
            assertNotNull(expected);
        }

        assertTrue(store.defaultTimelineTime(0L) > 0L);
        assertEquals(123L, store.defaultTimelineTime(123L));
        assertTrue(LocalStoreHistory.deserializeChoices(null).isEmpty());
        assertEquals(listOf("拉", "提"), LocalStoreHistory.deserializeChoices("拉\t\t提"));
        assertEquals("", LocalStoreHistory.serializeChoices(null));
        assertTrue(TimelineCopy.studyStateDetail(true, null, 3).contains("retired"));
        assertTrue(TimelineCopy.studyStateDetail(false, null, 3).contains("reopened"));
        assertTrue(TimelineCopy.studyStateDetail(false, 1, 3).contains("1 / target 3"));
        assertTrue(TimelineCopy.reviewDetail(RecordsSchedulerModels.ReviewRequest("拉", "manual", "good", false, false, true, 0), "good").contains("manual"));
        assertTrue(TimelineCopy.reviewDetail(RecordsSchedulerModels.ReviewRequest("拉", "recall", "again", false, false, false, 0), "again").contains("Recall missed"));
        assertTrue(TimelineCopy.reviewDetail(RecordsSchedulerModels.ReviewRequest("拉", "writing", "hard", true, false, false, 0), "hard").contains("not passed"));

        store.saveFailedSync(1000L, 2000L, "failed", "provider", "No provider")
        val failedSync = requireNotNull(store.latestSync())
        assertTrue(SettingsTextCopy.syncStatusHeadline(
                LocalStoreBase.STATUS_SUCCESS.equals(failedSync.status),
                failedSync.errorMessage,
                failedSync.suspendedCards,
                failedSync.importedKanji
        ).contains("Sync failed: No provider"));
        saveSingleRowSync(row("確", 0), emptyList(), 3000L);
        val latestSync = requireNotNull(store.latestSync())
        assertTrue(SettingsTextCopy.syncStatusHeadline(
                LocalStoreBase.STATUS_SUCCESS.equals(latestSync.status),
                latestSync.errorMessage,
                latestSync.suspendedCards,
                latestSync.importedKanji
        ).contains("kanji synced"));
    }

    @Test
    fun testHistoryHelpersSkipMissingSourcesDuplicatesAndEmptyAggregates() {
        val db = store.getWritableDatabase()
        val card = RecordsImportModels.SimilarKanjiChoiceCard(
                "拉",
                "pull",
                listOf("拉", "提"),
                "拉|提",
                0L,
                0L,
                0L,
                0,
                0
        )

        store.enqueueSimilarWritingRepair(db, card, "", "", 1000L);
        assertEquals(0, count("similar_kanji_repair_queue"));

        store.enqueueSimilarWritingRepair(db, card, "提", "", 1100L);
        store.enqueueSimilarWritingRepair(db, card, "提", "拉", 1200L);
        assertEquals(1, count("similar_kanji_repair_queue"));
        assertScalarString(
                "similar_kanji_repair_queue",
                "wrong_selection",
                "target_kanji=? AND repair_kanji=?",
                arrayOf("拉", "提"),
                ""
        );

        val missingSuspended = store.firstSuspendedSourceForKanji(db, "孤")
        assertEquals("", missingSuspended.expression);
        assertEquals("", missingSuspended.reading);
        val emptyImport = store.sourceFromImport(
                RecordsImportModels.SuspendedImport("孤", null, false, 0, emptyList())
        )
        assertEquals("", emptyImport.expression);
        assertEquals("", emptyImport.reading);

        val inventory = KanjiInventoryBuilder(1250L, RecordsSyncModels.Settings.kikuDefaults())
        inventory.addKnownKanji("");
        store.writeKanjiInventory(db, inventory);
        assertEquals(0, count("kanji_inventory"));

        val unchanged = RecordsStudyModels.StudyItem("拉", "review", 0L, 2.0, 4.0, 1, 0, 0, 0, null, 1000L)
        store.appendStudyStateTimelineEvent(db, unchanged, LocalStoreBase.StudySnapshot("review"), 7L, 1300L, 3);
        assertEquals(0, countTimelineType("拉", "retired"));
        assertEquals(0, countTimelineType("拉", "reopened"));

        store.createHistoricalSyncTables(db);
        val aggregates = LinkedHashMap<String, HistoricalKanjiAggregate>()
        aggregates.put("", HistoricalKanjiAggregate(""));
        store.insertHistoricalKanjiAggregates(db, 7L, 1400L, aggregates);
        assertEquals(0, count("sync_kanji_snapshots"));
    }

    @Test
    fun testHistoricalBackfillReturnsWhenSnapshotsOrNotesAlreadyExist() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertKanjiSnapshot(db, 1L, 2000L, "既", 1, 0, 0, 10, "existing", 1, 0, null, null, null);

        store.backfillLatestHistoricalSync(db);

        assertEquals(1, count("sync_kanji_snapshots"));

        db.delete("sync_kanji_snapshots", null, null);
        store.backfillLatestHistoricalSync(db);

        assertEquals(0, count("sync_kanji_snapshots"));
    }

    @Test
    fun testHistoricalBackfillSkipsCardsWithoutNotesAndNotesWithoutDecks() {
        val db = store.getWritableDatabase()
        store.createHistoricalSyncTables(db);
        insertSyncRun(db, 1L, 1000L, 2000L);
        insertSourceNote(db, 10L, "孤語", "孤語を見た。");
        insertSourceCard(db, 90L, 99L, "Kiku");

        store.backfillLatestHistoricalSync(db);

        assertEquals(0, count("sync_card_snapshots"));
        assertEquals(0, count("sync_note_snapshots"));
        assertEquals(0, count("sync_kanji_snapshots"));
    }

    @Test
    fun testHistoricalSyncSkipsOrphanCardsAndUndeckedNotes() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val noteWithoutCards = sourceKikuNote(1L, "孤語", "こ", "orphan note", "孤語を見た。")
        val cardWithoutNote = kikuCard(900L, 99L).history(15, 4, 0).build()

        store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(
                        listOf(noteWithoutCards),
                        listOf(cardWithoutNote)
                ),
                emptyList(),
                emptyList(),
                settings,
                1000L,
                2000L,
                null
        );

        assertEquals(0, count("source_cards"));
        assertEquals(0, count("source_notes"));
        assertEquals(0, count("sync_card_snapshots"));
        assertEquals(0, count("sync_note_snapshots"));
        assertEquals(0, count("sync_kanji_snapshots"));
    }

    @Test
    fun testStudyStreakCountsConsecutiveLocalReviewDays() {
        val today = localDayStart(System.currentTimeMillis())
        val yesterday = moveLocalDays(today, -1)
        val twoDaysAgo = moveLocalDays(today, -2)
        val fiveDaysAgo = moveLocalDays(today, -5)

        store.saveReview(review("拉", "token-five"), "good", fiveDaysAgo + 60_000L);
        store.saveReview(review("提", "token-two"), "good", twoDaysAgo + 60_000L);
        store.saveReview(review("謎", "token-yesterday"), "hard", yesterday + 60_000L);
        store.saveReview(review("麺", "token-today-a"), "good", today + 60_000L);
        store.saveReview(review("確", "token-today-b"), "easy", today + 120_000L);
        store.saveReview(review("確", "token-today-c"), "hard", today + 180_000L);

        val streak = store.studyStreak(today + 3_600_000L)
        assertEquals(3, streak.currentDays);
        assertEquals(3, streak.bestDays);
        assertTrue(streak.studiedToday);
        assertEquals(3, streak.reviewsToday);
        assertEquals(today + 180_000L, streak.lastStudyAtMillis);
        assertEquals(2, store.studiedKanjiSince(today).size);

        val tomorrow = store.studyStreak(moveLocalDays(today, 1) + 3_600_000L)
        assertEquals(3, tomorrow.currentDays);
        assertEquals(3, tomorrow.bestDays);
        assertFalse(tomorrow.studiedToday);
        assertEquals(0, tomorrow.reviewsToday);

        val afterMiss = store.studyStreak(moveLocalDays(today, 2) + 3_600_000L)
        assertEquals(0, afterMiss.currentDays);
        assertEquals(3, afterMiss.bestDays);
        assertFalse(afterMiss.studiedToday);
    }

    @Test
    fun testUnselectedSuspendedCardsStayOutOfArchiveButRemainInHistory() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val suspendedNote = sourceKikuNote(77L, "孤語", "こ", "alone", "孤語を見た。")
        val suspendedCard = kikuCard(770L, 77L)
                .suspended()
                .history(0, 5, 1)
                .fsrs(3.5, 7.5, 0.22)
                .build()

        val syncId = store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(listOf(suspendedNote), listOf(suspendedCard)),
                emptyList(),
                emptyList(),
                settings,
                1000L,
                2000L,
                null
        )

        val status = requireNotNull(store.latestSync())
        assertEquals(0, status.activeCards);
        assertEquals(0, status.suspendedCards);
        assertEquals(0, status.importedKanji);
        assertEquals(0, count("source_cards"));
        assertEquals(0, count("source_notes"));
        assertEquals(0, count("suspended_archive"));
        assertHistoricalCardSnapshot(syncId, 770L, 1, 0, 5, 1, 3.5, 7.5, 0.22);
        assertHistoricalKanjiSnapshot(syncId, "孤", 0, 1);
        assertScalarString(
                "sync_note_snapshots",
                "deck_names",
                "sync_id=? AND note_id=?",
                arrayOf(syncId.toString(), "77"),
                "Kiku"
        );
    }

    @Test
    fun testCursorReadersHandleOldSchemaNullsAndValues() {
        val cursor = MatrixCursor(arrayOf(
                "text_value",
                "int_value",
                "long_value",
                "double_value",
                "null_value"
        ))
        cursor.addRow(arrayOf<Any?>("value", 7, 8L, 1.25, null));
        assertTrue(cursor.moveToFirst());

        assertEquals("value", LocalStoreBase.string(cursor, "text_value"));
        assertEquals("", LocalStoreBase.string(cursor, "null_value"));
        assertEquals("", LocalStoreBase.string(cursor, "missing"));
        assertEquals(7, LocalStoreBase.integer(cursor, "int_value"));
        assertEquals(0, LocalStoreBase.integer(cursor, "null_value"));
        assertEquals(0, LocalStoreBase.integer(cursor, "missing"));
        assertEquals(Integer.valueOf(7), LocalStoreBase.nullableInt(cursor, "int_value"));
        assertNull(LocalStoreBase.nullableInt(cursor, "null_value"));
        assertNull(LocalStoreBase.nullableInt(cursor, "missing"));
        assertEquals(java.lang.Long.valueOf(8L), LocalStoreBase.nullableLong(cursor, "long_value"));
        assertNull(LocalStoreBase.nullableLong(cursor, "null_value"));
        assertNull(LocalStoreBase.nullableLong(cursor, "missing"));
        assertEquals(java.lang.Double.valueOf(1.25), LocalStoreBase.nullableDouble(cursor, "double_value"));
        assertNull(LocalStoreBase.nullableDouble(cursor, "null_value"));
        assertNull(LocalStoreBase.nullableDouble(cursor, "missing"));
        assertEquals(8L, LocalStoreBase.longValue(cursor, "long_value"));
        assertEquals(0L, LocalStoreBase.longValue(cursor, "null_value"));
        assertEquals(0L, LocalStoreBase.longValue(cursor, "missing"));

        val values = ContentValues()
        LocalStoreBase.putNullableDouble(values, "present", 2.5);
        LocalStoreBase.putNullableDouble(values, "absent", null);
        assertTrue(values.containsKey("present"));
        assertFalse(values.containsKey("absent"));
    }

    @Test
    fun testInventoryBuilderAndSettingsValuesNormalizeParserEdges() {
        val empty = KanjiInventoryBuilder(1L, RecordsSyncModels.Settings.kikuDefaults())
        empty.addKnownKanji(null);
        assertTrue(empty.build(Collections.emptyMap()).isEmpty());

        val builder = KanjiInventoryBuilder(2000L, RecordsSyncModels.Settings.kikuDefaults())
        builder.addSourceText(listOf("拉"), "pull", "ら", "拉語", "拉語を見た。");
        builder.addSourceText(listOf("拉"), "", "ラー", "", "");
        builder.addSourceText(listOf("拉"), null, "ラ", null, null);
        builder.addSourceText(listOf("拉"), "drag", "ろ", "拉致", "拉致した。");
        val previous = Collections.singletonMap(
                "拉",
                KanjiInventoryBuilder.PreviousItem(
                "old pull",
                "old reading",
                "deck:Kiku 拉",
                1,
                1,
                1000L,
                1000L
        ))
        val item = builder.build(previous).get(0)
        assertEquals("pull", item.primaryMeaning());
        assertEquals("ら / ラー / ラ +1 more", item.readings());
        assertTrue(item.searchText().contains("old pull"));
        assertTrue(item.searchText().contains("deck:kiku 拉"));

        val earlyReminder = LocalStoreBase.ReminderSettings(true, -3, 90).normalized()
        assertEquals("00:59", earlyReminder.displayTime());
        val lateReminder = LocalStoreBase.ReminderSettings(false, 30, -4).normalized()
        assertEquals("23:00", lateReminder.displayTime());

        val disabled = LocalStoreBase.AutoSyncSettings(false, true, -2, 70, -1L, -2L, -3L).normalized()
        assertFalse(disabled.configured);
        assertFalse(disabled.enabled);
        assertEquals("00:59", disabled.displayTime());
        assertEquals(0L, disabled.lastAttemptAt);
        assertEquals(0L, disabled.lastSuccessAt);
        assertEquals(0L, disabled.nextRunAt);
        val enabled = LocalStoreBase.AutoSyncSettings(true, true, 25, -3, 1L, 2L, 3L).normalized()
        assertTrue(enabled.enabled);
        assertEquals("23:00", enabled.displayTime());

        val emptyUpdate = LocalStoreBase.AutoUpdateStatus(true, 0L, null, null, null, null)
        assertFalse(emptyUpdate.hasPendingUpdate());
        assertEquals("", emptyUpdate.lastResult);
        val pendingUpdate = LocalStoreBase.AutoUpdateStatus(false, 123L, "ready", "0.4.34", "kani.apk", null)
        assertTrue(pendingUpdate.hasPendingUpdate());
        assertEquals("", pendingUpdate.pendingMessage);
    }

    private fun assertLatestSyncArchivedSuspendedCard() {
        val status = requireNotNull(store.latestSync())
        assertEquals("success", status.status);
        assertEquals(1, status.activeCards);
        assertEquals(1, status.suspendedCards);
        assertEquals(1, status.importedKanji);
        assertEquals("Archived locally before provider cleanup.", status.removalMessage);
    }

    private fun assertSyncMirrorCounts() {
        assertEquals(1, count("source_cards"));
        assertEquals(1, count("source_notes"));
        assertEquals(1, count("suspended_archive"));
        assertEquals(1, count("suspended_imports"));
        assertEquals(1, count("suspended_sources"));
        assertEquals(1, count("import_rule_audits"));
        assertEquals(1, count("import_decisions"));
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("kanji_examples"));
        assertEquals(2, count("sync_card_snapshots"));
        assertEquals(2, count("sync_note_snapshots"));
        assertTrue(count("sync_kanji_snapshots") >= 2);
    }

    private fun assertDashboardRowFsrsStored() {
        assertEquals("拉", store.dashboardRows().get(0).kanji);
        assertClose(18.5, store.dashboardRows().get(0).examples.get(0).fsrsStability);
    }

    private fun assertSuspendedImportStored() {
        val storedImports = store.suspendedImports()
        assertEquals(1, storedImports.size);
        assertEquals("拉", storedImports.get(0).kanji);
        assertEquals(1, storedImports.get(0).sources.size);
    }

    private fun assertImportAuditStored() {
        assertScalarString("import_rule_audits", "enabled_sources", "sync_id=?", arrayOf("1"), "suspended");
        assertScalarString("import_rule_audits", "browser_query", "sync_id=?", arrayOf("1"), "");
        assertScalarString("import_decisions", "reason_code", "sync_id=? AND kanji=?", arrayOf("1", "拉"), "suspended_import");
        assertScalarString("import_decisions", "source_types", "sync_id=? AND kanji=?", arrayOf("1", "拉"), "suspended");
        assertScalarString("import_decisions", "rule_types", "sync_id=? AND kanji=?", arrayOf("1", "拉"), "suspended");
        assertScalarString("import_decisions", "source_card_ids", "sync_id=? AND kanji=?", arrayOf("1", "拉"), "20");
    }

    private fun assertInitialSimilarChoiceDue(pull: RecordsImportModels.SimilarKanjiChoiceCard) {
        assertEquals(listOf("拉", "提", "謎"), pull.choices);
        assertEquals("pull", pull.primaryMeaning);
        assertNotNull(store.dueSimilarChoiceForActiveTarget("拉", 2000L));
        assertEquals(3, store.dueSimilarChoiceTaskCount(2000L));
        assertEquals(0, store.dueSimilarWritingRepairTaskCount(2000L));
        assertEquals(3, store.dueSimilarStudyTaskCount(2000L));
        val inventoryTarget = store.nextDueInventorySimilarChoice(setOf("拉"), 2000L)!!.targetKanji
        assertNotEquals("inventory-only cards should skip active targets", "拉", inventoryTarget);
    }

    private fun assertWrongSimilarChoiceUsesInlineAdaptiveRepair(wrong: RecordsImportModels.SimilarKanjiChoiceResult) {
        assertFalse(wrong.correct);
        assertEquals(listOf("拉", "提"), wrong.repairKanji);
        assertEquals(0, count("review_log"));
        assertEquals(1, count("similar_kanji_review_log"));
        assertEquals(0, count("similar_kanji_repair_queue"));
        assertNull(store.nextDueSimilarWritingRepair(2500L));
        assertNotNull(store.dueSimilarChoiceForActiveTarget("拉", 2500L));
        assertEquals(3, store.dueSimilarChoiceTaskCount(2500L));
        assertEquals(0, store.dueSimilarWritingRepairTaskCount(2500L));
        assertEquals(3, store.dueSimilarStudyTaskCount(2500L));
    }

    private fun insertLegacySimilarWritingRepair(
        targetKanji: String,
        repairKanji: String,
        choiceSignature: String,
        wrongSelection: String,
        promptMeaning: String,
        dueAtMillis: Long,
        createdAtMillis: Long,
    ) {
        ContentValuesBuilder.insert(store.getWritableDatabase(), "similar_kanji_repair_queue")
                .put("target_kanji", targetKanji)
                .put("repair_kanji", repairKanji)
                .put("choice_signature", choiceSignature)
                .put("wrong_selection", wrongSelection)
                .put("prompt_meaning", promptMeaning)
                .put("status", "pending")
                .put("due_at", dueAtMillis)
                .put("active_token", "")
                .put("attempts", 0)
                .put("created_at", createdAtMillis)
                .put("updated_at", createdAtMillis)
                .put("completed_at", 0L)
                .commit()
    }

    private fun assertSimilarChoiceRetryDue() {
        assertEquals(3, store.dueSimilarChoiceTaskCount(3000L));
        assertEquals(0, store.dueSimilarWritingRepairTaskCount(3000L));
        assertEquals(3, store.dueSimilarStudyTaskCount(3000L));
    }

    private fun assertCorrectSimilarChoicePasses(correct: RecordsImportModels.SimilarKanjiChoiceResult) {
        assertTrue(correct.correct);
        assertNull(store.dueSimilarChoiceForActiveTarget("拉", 3200L));
        assertEquals(2, store.dueSimilarStudyTaskCount(3200L));
        assertEquals(0, count("review_log"));
        assertEquals(2, count("similar_kanji_review_log"));
    }

    private fun assertContainsKanji(items: List<RecordsImportModels.KanjiInventoryItem>, kanji: String) {
        for (item in items) {
            if (kanji.equals(item.kanji)) {
                return;
            }
        }
        throw AssertionError("Expected search results to include " + kanji);
    }

    private fun saveSingleRowSync(row: RecordsImportModels.DashboardRow, imports: List<RecordsImportModels.SuspendedImport>, finishedAt: Long): Long {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val note = sourceKikuNote(1L, row.kanji + "語", row.reading, row.primaryMeaning, row.kanji + "を見た。")
        val card = kikuCard(10L, 1L).build()
        return store.saveSuccessfulSync(
                snapshot = RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card)),
                imports = imports,
                rows = listOf(row),
                settings = settings,
                timing = LocalStoreBase.SyncTiming(finishedAt - 500L, finishedAt),
                removalMessage = null,
                similarIndex = null,
        )
    }

    private fun row(kanji: String, matureSupportCount: Int): RecordsImportModels.DashboardRow {
        return rowWithStats(kanji, 88, matureSupportCount);
    }

    private fun rowWithStats(kanji: String, weaknessScore: Int, matureSupportCount: Int): RecordsImportModels.DashboardRow {
        val active = RecordsImportModels.Example("active", 10L, 1L, kanji + "語", "ら", "example", kanji + "を見た。", matureSupportCount > 0, 0)
        return RecordsImportModels.DashboardRow(
                kanji,
                3401,
                "ramen radical gap",
                "ら",
                "deck:Kiku " + kanji,
                weaknessScore,
                "suspended_archive",
                "Imported from suspended cards",
                1,
                0,
                matureSupportCount,
                listOf(active)
        );
    }

    private fun suspendedImport(kanji: String): RecordsImportModels.SuspendedImport {
        val source = RecordsImportModels.SuspendedSource(kanji, 20L, 2L, kanji + "例", "ら", "archive example", kanji + "を練習した。")
        return RecordsImportModels.SuspendedImport(kanji, 3401, true, 3000, listOf(source));
    }

    private fun impactRow(report: KanjiImpactAnalyzer.Report, kanji: String): KanjiImpactAnalyzer.Row {
        for (row in report.rows) {
            if (kanji.equals(row.kanji)) {
                return row;
            }
        }
        throw AssertionError("Expected impact row for " + kanji);
    }

    private fun countTimelineType(kanji: String, eventType: String): Int {
        return countTimelineType(store.timelineForKanji(kanji), eventType);
    }

    private fun countTimelineType(timeline: RecordsStudyModels.KanjiRecoveryTimeline, eventType: String): Int {
        var count = 0
        for (event in timeline.events) {
            if (eventType.equals(event.eventType)) {
                count++;
            }
        }
        return count;
    }

    private fun hasTimelineType(timeline: RecordsStudyModels.KanjiRecoveryTimeline, eventType: String): Boolean {
        return countTimelineType(timeline, eventType) > 0;
    }

    private fun assertTimelineEventDetailContains(timeline: RecordsStudyModels.KanjiRecoveryTimeline, eventType: String, detail: String) {
        for (event in timeline.events) {
            if (eventType.equals(event.eventType)) {
                assertTrue(event.detail.contains(detail));
                return;
            }
        }
        throw AssertionError("Expected timeline event " + eventType);
    }

    private fun insertSyncRun(db: SQLiteDatabase, id: Long, startedAt: Long, finishedAt: Long) {
        val values = ContentValues()
        values.put("id", id);
        values.put("started_at", startedAt);
        values.put("finished_at", finishedAt);
        values.put("status", "success");
        values.put("active_notes_count", 0);
        values.put("active_cards_count", 0);
        values.put("suspended_cards_archived_count", 0);
        values.put("suspended_kanji_imported_count", 0);
        values.put("deleted_notes_count", 0);
        values.put("deleted_cards_count", 0);
        values.put("error_code", "");
        values.put("error_message", "");
        values.put("removal_message", "");
        db.insertOrThrow("sync_runs", null, values);
    }

    private fun insertSourceNote(db: SQLiteDatabase, noteId: Long, expression: String, sentence: String) {
        val values = ContentValues()
        values.put("note_id", noteId);
        values.put("model_name", "Kiku");
        values.put("expression", expression);
        values.put("reading", "こ");
        values.put("meaning", "orphan");
        values.put("sentence", sentence);
        values.put("fields_json", "{}");
        values.put("tags", "");
        values.put("last_seen_sync_id", 1L);
        db.insertOrThrow("source_notes", null, values);
    }

    private fun insertSourceCard(db: SQLiteDatabase, cardId: Long, noteId: Long, deckName: String) {
        val values = ContentValues()
        values.put("card_id", cardId);
        values.put("note_id", noteId);
        values.put("deck_name", deckName);
        values.put("ord", 0);
        values.put("queue", 2);
        values.put("type", 2);
        values.put("due", 0);
        values.put("interval_days", 3);
        values.put("reps", 1);
        values.put("lapses", 0);
        values.put("last_seen_sync_id", 1L);
        db.insertOrThrow("source_cards", null, values);
    }

    private fun insertKanjiSnapshot(db: SQLiteDatabase, syncId: Long, finishedAt: Long, kanji: String, activeCards: Int, suspendedCards: Int, matureSupportCount: Int, weaknessScore: Int, reasonCode: String, activeExampleCount: Int, suspendedExampleCount: Int, stability: Double?, difficulty: Double?, retrievability: Double?) {
        val values = ContentValues()
        values.put("sync_id", syncId);
        values.put("finished_at", finishedAt);
        values.put("kanji", kanji);
        values.put("active_cards", activeCards);
        values.put("suspended_cards", suspendedCards);
        values.put("mature_support_count", matureSupportCount);
        values.put("average_interval_days", 30.0);
        values.put("total_lapses", 1);
        values.put("total_reps", 10);
        putNullableDouble(values, "fsrs_stability_avg", stability);
        putNullableDouble(values, "fsrs_difficulty_avg", difficulty);
        putNullableDouble(values, "fsrs_retrievability_avg", retrievability);
        values.put("weakness_score", weaknessScore);
        values.put("reason_code", reasonCode);
        values.put("active_example_count", activeExampleCount);
        values.put("suspended_example_count", suspendedExampleCount);
        db.insertOrThrow("sync_kanji_snapshots", null, values);
    }

    private fun insertNoteSnapshot(db: SQLiteDatabase, syncId: Long, finishedAt: Long, noteId: Long, extractedKanji: String) {
        val values = ContentValues()
        values.put("sync_id", syncId);
        values.put("finished_at", finishedAt);
        values.put("note_id", noteId);
        values.put("model_id", 1L);
        values.put("model_name", "Kiku");
        values.put("deck_ids", "deck-1");
        values.put("deck_names", "Kiku");
        values.put("expression", extractedKanji + "語");
        values.put("reading", "えい");
        values.put("meaning", "swim");
        values.put("sentence", extractedKanji + "語を見た。");
        values.put("tags", "");
        values.put("fields_json", "{}");
        values.put("extracted_kanji", extractedKanji);
        db.insertOrThrow("sync_note_snapshots", null, values);
    }

    private fun insertCardSnapshot(db: SQLiteDatabase, syncId: Long, finishedAt: Long, cardId: Long, noteId: Long, suspended: Boolean, mature: Boolean, intervalDays: Int, reps: Int, lapses: Int, stability: Double?, difficulty: Double?, retrievability: Double?) {
        val values = ContentValues()
        values.put("sync_id", syncId);
        values.put("started_at", finishedAt - 500L);
        values.put("finished_at", finishedAt);
        values.put("card_id", cardId);
        values.put("note_id", noteId);
        values.put("deck_id", "deck-1");
        values.put("deck_name", "Kiku");
        values.put("model_id", 1L);
        values.put("model_name", "Kiku");
        values.put("ord", 0);
        values.put("queue", if (suspended) -1 else 2);
        values.put("type", 2);
        values.put("due", 0);
        values.put("interval_days", intervalDays);
        values.put("reps", reps);
        values.put("lapses", lapses);
        values.put("suspended", if (suspended) 1 else 0)
        putNullableDouble(values, "fsrs_stability", stability);
        putNullableDouble(values, "fsrs_difficulty", difficulty);
        putNullableDouble(values, "fsrs_retrievability", retrievability);
        values.put("mature", if (mature) 1 else 0)
        db.insertOrThrow("sync_card_snapshots", null, values);
    }

    private fun putNullableDouble(values: ContentValues, key: String, value: Double?) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private fun findSimilarChoice(targetKanji: String): RecordsImportModels.SimilarKanjiChoiceCard {
        for (card in store.allSimilarChoiceCards()) {
            if (targetKanji.equals(card.targetKanji)) {
                return card;
            }
        }
        throw AssertionError("No similar-choice card for " + targetKanji);
    }

    private fun createLegacyV1Schema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, active_notes_count INTEGER NOT NULL, active_cards_count INTEGER NOT NULL, suspended_cards_archived_count INTEGER NOT NULL, suspended_kanji_imported_count INTEGER NOT NULL, deleted_notes_count INTEGER NOT NULL, deleted_cards_count INTEGER NOT NULL, error_code TEXT, error_message TEXT, removal_message TEXT)");
        db.execSQL("CREATE TABLE source_notes (note_id INTEGER PRIMARY KEY, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, tags TEXT NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE source_cards (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_archive (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, archived_at INTEGER NOT NULL, archived_sync_id INTEGER NOT NULL, restored_at INTEGER)");
        db.execSQL("CREATE TABLE suspended_imports (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, rank_known INTEGER NOT NULL, cutoff_used INTEGER NOT NULL, first_imported_at INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_sources (kanji TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, sync_id INTEGER NOT NULL, PRIMARY KEY (kanji, card_id))");
        db.execSQL("CREATE TABLE dashboard_rows (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, primary_meaning TEXT NOT NULL, reading TEXT NOT NULL, browser_search TEXT NOT NULL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE kanji_examples (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, mature INTEGER NOT NULL, lapses INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE study_items (kanji TEXT PRIMARY KEY, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, active_token TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_examples_kanji ON kanji_examples(kanji)");
        db.execSQL("CREATE INDEX idx_study_due ON study_items(state, due_at)");
    }

    private fun createLegacyV15SchedulerSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE study_items (kanji TEXT NOT NULL, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, recognition_stage INTEGER NOT NULL DEFAULT 0, consecutive_failed_recognition_days INTEGER NOT NULL DEFAULT 0, last_failed_recognition_day INTEGER NOT NULL DEFAULT 0, writing_remediation_pending INTEGER NOT NULL DEFAULT 0, suppressed_by_task_type TEXT NOT NULL DEFAULT '', suppressed_at INTEGER NOT NULL DEFAULT 0, mature_interval_days INTEGER NOT NULL DEFAULT 0, answer_signature TEXT NOT NULL DEFAULT '', typing_meaning_memory TEXT NOT NULL DEFAULT '', kanji_meaning_memory TEXT NOT NULL DEFAULT '', font_meaning_memory TEXT NOT NULL DEFAULT '', word_reading_memory TEXT NOT NULL DEFAULT '', writing_remediation_memory TEXT NOT NULL DEFAULT '', active_token TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature))");
        db.execSQL("CREATE INDEX idx_study_due ON study_items(state, due_at)");
        db.execSQL("CREATE TABLE learning_repeats (kanji TEXT NOT NULL, answer_signature TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL, repeat_type TEXT NOT NULL, step_index INTEGER NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (kanji, answer_signature, task_type))");
        db.execSQL("CREATE TABLE similar_kanji_choice_state (target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, primary_meaning TEXT NOT NULL, choices TEXT NOT NULL, due_at INTEGER NOT NULL, passed_at INTEGER NOT NULL DEFAULT 0, last_reviewed_at INTEGER NOT NULL DEFAULT 0, correct_count INTEGER NOT NULL DEFAULT 0, wrong_count INTEGER NOT NULL DEFAULT 0, active_token TEXT NOT NULL DEFAULT '', first_seen_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, PRIMARY KEY (target_kanji, choice_signature))");
        db.execSQL("CREATE TABLE similar_kanji_repair_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, target_kanji TEXT NOT NULL, repair_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, wrong_selection TEXT NOT NULL, prompt_meaning TEXT NOT NULL, status TEXT NOT NULL, due_at INTEGER NOT NULL, active_token TEXT NOT NULL DEFAULT '', attempts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER NOT NULL DEFAULT 0)");
    }

    private fun count(table: String): Int {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private fun countWhere(table: String, where: String, vararg args: String): Int {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where, args)
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private fun assertScalarString(table: String, column: String, where: String, args: Array<String>, expected: String) {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("SELECT " + column + " FROM " + table + " WHERE " + where, args)
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(expected, cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    private fun assertScalarLong(table: String, column: String, where: String, args: Array<String>, expected: Long) {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("SELECT " + column + " FROM " + table + " WHERE " + where, args)
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(expected, cursor.getLong(0));
        } finally {
            cursor.close();
        }
    }

    private fun assertSourceCardFsrs(stability: Double, difficulty: Double, retrievability: Double) {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("SELECT fsrs_stability, fsrs_difficulty, fsrs_retrievability FROM source_cards WHERE card_id=10", null)
        try {
            assertTrue(cursor.moveToFirst());
            assertClose(stability, cursor.getDouble(0));
            assertClose(difficulty, cursor.getDouble(1));
            assertClose(retrievability, cursor.getDouble(2));
        } finally {
            cursor.close();
        }
    }

    private fun assertHistoricalCardSnapshot(syncId: Long, cardId: Long, suspended: Int, mature: Int, reps: Int, lapses: Int, stability: Double, difficulty: Double, retrievability: Double) {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery(
                "SELECT suspended, mature, reps, lapses, fsrs_stability, fsrs_difficulty, fsrs_retrievability FROM sync_card_snapshots WHERE sync_id=? AND card_id=?",
                arrayOf(syncId.toString(), cardId.toString())
        )
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(suspended, cursor.getInt(0));
            assertEquals(mature, cursor.getInt(1));
            assertEquals(reps, cursor.getInt(2));
            assertEquals(lapses, cursor.getInt(3));
            assertClose(stability, cursor.getDouble(4));
            assertClose(difficulty, cursor.getDouble(5));
            assertClose(retrievability, cursor.getDouble(6));
        } finally {
            cursor.close();
        }
    }

    private fun assertHistoricalKanjiSnapshot(syncId: Long, kanji: String, activeCards: Int, suspendedCards: Int) {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery(
                "SELECT active_cards, suspended_cards FROM sync_kanji_snapshots WHERE sync_id=? AND kanji=?",
                arrayOf(syncId.toString(), kanji)
        )
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(activeCards, cursor.getInt(0));
            assertEquals(suspendedCards, cursor.getInt(1));
        } finally {
            cursor.close();
        }
    }

    private fun assertHistoricalIdentitySnapshot(syncId: Long, cardId: Long, deckId: String, deckName: String, modelId: Long, noteDeckIds: String, noteDeckNames: String) {
        val db = store.getReadableDatabase()
        val card = db.rawQuery(
                "SELECT deck_id, deck_name, model_id FROM sync_card_snapshots WHERE sync_id=? AND card_id=?",
                arrayOf(syncId.toString(), cardId.toString())
        )
        try {
            assertTrue(card.moveToFirst());
            assertEquals(deckId, card.getString(0));
            assertEquals(deckName, card.getString(1));
            assertEquals(modelId, card.getLong(2));
        } finally {
            card.close();
        }

        val note = db.rawQuery(
                "SELECT model_id, deck_ids, deck_names FROM sync_note_snapshots WHERE sync_id=? AND note_id=1",
                arrayOf(syncId.toString())
        )
        try {
            assertTrue(note.moveToFirst());
            assertEquals(modelId, note.getLong(0));
            assertEquals(noteDeckIds, note.getString(1));
            assertEquals(noteDeckNames, note.getString(2));
        } finally {
            note.close();
        }
    }

    private fun hasColumn(table: String, column: String): Boolean {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)
        try {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private fun hasIndex(table: String, index: String): Boolean {
        val db = store.getReadableDatabase()
        val cursor = db.rawQuery("PRAGMA index_list(" + table + ")", null)
        try {
            while (cursor.moveToNext()) {
                if (index.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(Math.abs(expected - actual) <= 0.001);
    }

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(actual != null && Math.abs(expected - actual) <= 0.001);
    }
}

package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreKanjiReadingUsageTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    // ---- migration v25 -> v26 ----

    @Test
    fun migrationTwentyFiveToTwentySixCreatesTablesAndPreservesRows() {
        val db = SQLiteDatabase.create(null)
        // A minimal pre-existing table with a row that must survive the upgrade.
        db.execSQL("CREATE TABLE ${LocalStoreBase.TABLE_DASHBOARD_ROWS} (kanji TEXT PRIMARY KEY)")
        db.execSQL("INSERT INTO ${LocalStoreBase.TABLE_DASHBOARD_ROWS} (kanji) VALUES ('脱')")

        store.onUpgrade(db, 25, 26)

        assertTrue(tableExists(db, LocalStoreBase.TABLE_KANJI_READING_USAGE))
        assertTrue(tableExists(db, LocalStoreBase.TABLE_KANJI_READING_POOL))
        db.rawQuery("SELECT kanji FROM ${LocalStoreBase.TABLE_DASHBOARD_ROWS}", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("脱", it.getString(0))
        }
        // Idempotent: re-running the same upgrade must not throw.
        store.onUpgrade(db, 25, 26)
        db.close()
    }

    @Test
    fun freshInstallHasReadingUsageTables() {
        val db = store.writableDatabase
        assertTrue(tableExists(db, LocalStoreBase.TABLE_KANJI_READING_USAGE))
        assertTrue(tableExists(db, LocalStoreBase.TABLE_KANJI_READING_POOL))
    }

    // ---- sync-save rebuild ----

    @Test
    fun syncSaveRebuildsUsageRowsWithCanonicalReadings() {
        saveSync(
            rows = listOf(
                rowWithExamples("脱", example("脱出", "だっしゅつ", noteId = 1, mature = true, lapses = 11)),
                rowWithExamples("配", example("心配", "しんぱい", noteId = 2, mature = true, lapses = 7)),
                rowWithExamples("好", example("好き", "すき", noteId = 3, mature = false, lapses = 0)),
                // Jukujikun word: no rows should be produced for 今.
                rowWithExamples("今", example("今日", "きょう", noteId = 4, mature = true, lapses = 0)),
            ),
        )

        // 脱出 → 脱=だつ (sokuon canonicalized), 出=しゅつ.
        assertEquals(setOf("だつ"), attestedReadings("脱"))
        // 心配 → 配=はい (rendaku h→p canonicalized back to はい).
        assertEquals(setOf("はい"), attestedReadings("配"))
        // 好き → 好=す (kun stem).
        assertEquals(setOf("す"), attestedReadings("好"))
        // 今日 fails alignment → no rows for 今.
        assertEquals(emptySet<String>(), attestedReadings("今"))
    }

    @Test
    fun secondSavePurgesStaleRows() {
        saveSync(
            rows = listOf(rowWithExamples("脱", example("脱出", "だっしゅつ", noteId = 1))),
        )
        assertEquals(setOf("だつ"), attestedReadings("脱"))

        // Re-sync with a different word set: the old 脱 rows must be purged.
        saveSync(
            rows = listOf(rowWithExamples("好", example("好き", "すき", noteId = 3))),
        )
        assertEquals(emptySet<String>(), attestedReadings("脱"))
        assertEquals(setOf("す"), attestedReadings("好"))
    }

    // ---- predicates ----

    @Test
    fun kanjiWithKanjiReadingRequiresUsageAndTwoReadings() {
        saveSync(
            rows = listOf(
                // 脱 has an attested usage AND >= 2 pool readings (だつ attested +
                // ぬ from the dictionary), so it qualifies.
                rowWithExamples("脱", example("脱出", "だっしゅつ", noteId = 1)),
                // 好 has attested す plus multiple dictionary readings → qualifies.
                rowWithExamples("好", example("好き", "すき", noteId = 3)),
            ),
        )
        val qualifying = store.kanjiWithKanjiReading(store.readableDatabase)
        assertTrue(qualifying.contains("脱"))
        assertTrue(qualifying.contains("好"))
    }

    @Test
    fun kanjiWithKanjiReadingExcludesSingleReadingKanji() {
        // A fabricated single-reading kanji (one dictionary reading, and its only
        // attested usage is that same reading) must not qualify.
        saveSync(
            dictionary = DictionaryLookup.fromKanjiEntries(
                listOf(kanjiEntry("凸", on = listOf("トツ"), kun = emptyList())),
            ),
            rows = listOf(rowWithExamples("凸", example("凸", "とつ", noteId = 9))),
        )
        val qualifying = store.kanjiWithKanjiReading(store.readableDatabase)
        assertFalse(qualifying.contains("凸"))
    }

    @Test
    fun kanjiWithReadingKanjiRequiresPoolOfThree() {
        // Three kanji all attested with reading こう → each qualifies (pool of 3).
        // A fourth reading attested by only one kanji does not.
        saveSync(
            dictionary = DictionaryLookup.fromKanjiEntries(
                listOf(
                    kanjiEntry("校", on = listOf("コウ"), kun = emptyList()),
                    kanjiEntry("高", on = listOf("コウ"), kun = emptyList()),
                    kanjiEntry("光", on = listOf("コウ"), kun = emptyList()),
                    kanjiEntry("駅", on = listOf("エキ"), kun = emptyList()),
                ),
            ),
            rows = listOf(
                rowWithExamples("校", example("校", "こう", noteId = 11)),
                rowWithExamples("高", example("高", "こう", noteId = 12)),
                rowWithExamples("光", example("光", "こう", noteId = 13)),
                rowWithExamples("駅", example("駅", "えき", noteId = 14)),
            ),
        )
        val qualifying = store.kanjiWithReadingKanji(store.readableDatabase)
        assertEquals(setOf("校", "高", "光"), qualifying)
    }

    @Test
    fun kanjiWithReadingKanjiExcludesPoolOfTwo() {
        saveSync(
            dictionary = DictionaryLookup.fromKanjiEntries(
                listOf(
                    kanjiEntry("校", on = listOf("コウ"), kun = emptyList()),
                    kanjiEntry("高", on = listOf("コウ"), kun = emptyList()),
                ),
            ),
            rows = listOf(
                rowWithExamples("校", example("校", "こう", noteId = 11)),
                rowWithExamples("高", example("高", "こう", noteId = 12)),
            ),
        )
        val qualifying = store.kanjiWithReadingKanji(store.readableDatabase)
        assertEquals(emptySet<String>(), qualifying)
    }

    @Test
    fun kanjiWithSentenceReadingRequiresSentenceAndReading() {
        saveSync(
            rows = listOf(
                rowWithExamples("脱", example("脱出", "だっしゅつ", noteId = 1, sentence = "彼は脱出した。")),
                // Reading present but sentence blank → excluded.
                rowWithExamples("好", example("好き", "すき", noteId = 3, sentence = "")),
                // Sentence present but reading blank → excluded.
                rowWithExamples("配", example("心配", "", noteId = 2, sentence = "心配ない。")),
            ),
        )
        val qualifying = store.kanjiWithSentenceReading(store.readableDatabase)
        assertTrue(qualifying.contains("脱"))
        assertFalse(qualifying.contains("好"))
        assertFalse(qualifying.contains("配"))
    }

    // ---- helpers ----

    private fun saveSync(
        rows: List<RecordsImportModels.DashboardRow>,
        dictionary: DictionaryLookup = fixtureDictionary(),
    ) {
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList<RecordsImportModels.SuspendedImport>(),
            rows,
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, 2_000L),
            null,
            null,
            null,
            LocalStoreBase.STATUS_SUCCESS,
            dictionary,
        )
    }

    private fun attestedReadings(kanji: String): Set<String> {
        val out = HashSet<String>()
        store.readableDatabase.rawQuery(
            "SELECT ${LocalStoreBase.COLUMN_READING} FROM ${LocalStoreBase.TABLE_KANJI_READING_USAGE} " +
                "WHERE ${LocalStoreBase.COLUMN_KANJI} = ?",
            arrayOf(kanji),
        ).use {
            while (it.moveToNext()) {
                out.add(it.getString(0))
            }
        }
        return out
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { return it.moveToFirst() }
    }

    private fun rowWithExamples(
        kanji: String,
        vararg examples: RecordsImportModels.Example,
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning-$kanji",
            "reading-$kanji",
            "browser-$kanji",
            1,
            "reason",
            "reason text",
            examples.count { it.sourceType == "active" },
            0,
            0,
            examples.toList(),
        )
    }

    private fun example(
        expression: String,
        reading: String,
        noteId: Long,
        sentence: String = "",
        mature: Boolean = false,
        lapses: Int = 0,
        intervalDays: Int = 0,
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            "active",
            noteId, // cardId
            noteId,
            expression,
            reading,
            "meaning",
            sentence,
            mature,
            lapses,
            intervalDays,
            0,
            null,
            null,
            null,
        )
    }

    private fun fixtureDictionary(): DictionaryLookup = DictionaryLookup.fromKanjiEntries(
        listOf(
            kanjiEntry("脱", on = listOf("ダツ"), kun = listOf("ぬ.ぐ", "ぬ.げる")),
            kanjiEntry("出", on = listOf("シュツ", "スイ"), kun = listOf("で.る", "だ.す")),
            kanjiEntry("心", on = listOf("シン"), kun = listOf("こころ")),
            kanjiEntry("配", on = listOf("ハイ"), kun = listOf("くば.る")),
            kanjiEntry("好", on = listOf("コウ"), kun = listOf("この.む", "す.く", "よ.い", "い.い")),
            kanjiEntry("今", on = listOf("コン", "キン"), kun = listOf("いま")),
            kanjiEntry("日", on = listOf("ニチ", "ジツ"), kun = listOf("ひ", "び", "か")),
        ),
    )

    private fun kanjiEntry(
        literal: String,
        on: List<String>,
        kun: List<String>,
    ): DictionaryLookup.KanjiEntry = DictionaryLookup.KanjiEntry(
        DictionaryLookup.KanjiEntryFields(
            literal,
            listOf("meaning"),
            on,
            kun,
            emptyList(),
            12,
            3,
            61,
            1000,
            null,
        ),
    )
}

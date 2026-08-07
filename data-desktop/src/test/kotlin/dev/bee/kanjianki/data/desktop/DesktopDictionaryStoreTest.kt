package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.data.sql.SqlConnectionMode
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The desktop dictionary against a real SQLite file in the shipped schema.
 *
 * Written with a real database rather than a fake connection because the thing most likely
 * to be wrong here is the SQL and the column order, and neither is exercised by a stub.
 * The fixture uses `U+001F`-separated lists, which is what the generator emits.
 */
class DesktopDictionaryStoreTest {
    private lateinit var directory: Path
    private lateinit var databaseFile: Path

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("kani-desktop-dictionary-")
        databaseFile = directory.resolve("kanji_dictionary.db")
        seed(databaseFile)
    }

    @After
    fun tearDown() {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun store() = DesktopDictionaryStore.open(databaseFile)

    /** Creates the shipped tables and three kanji, one of them unranked. */
    private fun seed(file: Path) {
        val driver = BundledSqlDriver(file.toString())
        driver.use {
            driver.openConnection(SqlConnectionMode.READ_WRITE).use { connection ->
                connection.execute(
                    """
                    CREATE TABLE kanji (
                        literal TEXT PRIMARY KEY,
                        meanings TEXT,
                        on_readings TEXT,
                        kun_readings TEXT,
                        nanori_readings TEXT,
                        stroke_count INTEGER,
                        grade INTEGER,
                        radical INTEGER,
                        kanjidic_frequency INTEGER,
                        jiten_rank INTEGER
                    )
                    """.trimIndent(),
                )
                connection.execute(
                    "CREATE TABLE jiten_ranks (literal TEXT PRIMARY KEY, rank INTEGER)",
                )
                insert(connection, "脱", "to escape, to flee${US}to remove", "ダツ", "ぬ.ぐ", "", 11, 8, 130, 900, 5)
                insert(connection, "出", "to exit${US}to leave", "シュツ", "で.る", "いず", 5, 1, 17, 50, 1)
                insert(connection, "麿", "I${US}you", "", "まろ", "まろ", 18, 9, 213, 9_000, null)
                connection.execute("INSERT INTO jiten_ranks (literal, rank) VALUES ('出', 1)")
                connection.execute("INSERT INTO jiten_ranks (literal, rank) VALUES ('脱', 5)")
            }
        }
    }

    private fun insert(
        connection: dev.bee.kanjianki.data.sql.SqlConnection,
        literal: String,
        meanings: String,
        on: String,
        kun: String,
        nanori: String,
        strokes: Int,
        grade: Int,
        radical: Int,
        frequency: Int,
        rank: Int?,
    ) {
        connection
            .prepare("INSERT INTO kanji VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                statement.bindText(1, literal)
                statement.bindText(2, meanings)
                statement.bindText(3, on)
                statement.bindText(4, kun)
                statement.bindText(5, nanori)
                statement.bindLong(6, strokes.toLong())
                statement.bindLong(7, grade.toLong())
                statement.bindLong(8, radical.toLong())
                statement.bindLong(9, frequency.toLong())
                if (rank == null) statement.bindNull(10) else statement.bindLong(10, rank.toLong())
                statement.execute()
            }
    }

    @Test
    fun readsAnEntryWithEveryFieldInTheRightColumn() {
        val entry = store().lookupKanji("脱")

        assertNotNull(entry)
        assertEquals("脱", entry?.literal)
        // The whole point of pinning the projection: a field landing one column across
        // would still produce a plausible-looking entry with a wrong stroke count.
        assertEquals(listOf("to escape, to flee", "to remove"), entry?.meanings)
        assertEquals(listOf("ダツ"), entry?.onReadings)
        assertEquals(listOf("ぬ.ぐ"), entry?.kunReadings)
        assertEquals(emptyList<String>(), entry?.nanoriReadings)
        assertEquals(11, entry?.strokeCount)
        assertEquals(8, entry?.grade)
        assertEquals(130, entry?.radical)
        assertEquals(900, entry?.kanjidicFrequency)
        assertEquals(5, entry?.jitenRank)
    }

    @Test
    fun splitsOnlyOnTheUnitSeparatorSoAMeaningKeepsItsCommas() {
        // "to escape, to flee" is one meaning. A `[;,]` split — which this first had —
        // would report three meanings for this row and be wrong on most of the corpus.
        val meanings = store().lookupKanji("脱")?.meanings

        assertEquals(2, meanings?.size)
        assertTrue(meanings?.first()?.contains(",") == true)
    }

    @Test
    fun anUnknownOrBlankLiteralIsNull() {
        val store = store()

        assertNull(store.lookupKanji("犬"))
        assertNull(store.lookupKanji(""))
        assertNull(store.lookupKanji("   "))
        assertNull(store.lookupKanji(null))
    }

    @Test
    fun countsAndRanksComeFromTheShippedTables() {
        val store = store()

        assertEquals(3, store.kanjiCount())
        val ranks = store.jitenRanks()
        assertEquals(2, ranks.size())
        assertEquals(1, ranks.rankOf("出"))
        assertEquals(5, ranks.rankOf("脱"))
        assertNull(ranks.rankOf("麿"))
    }

    @Test
    fun searchMatchesMeaningsReadingsAndTheLiteralItself() {
        val store = store()

        assertEquals(listOf("出"), store.searchKanji("to exit", 10).map { it.literal })
        assertEquals(listOf("脱"), store.searchKanji("ダツ", 10).map { it.literal })
        assertEquals(listOf("麿"), store.searchKanji("麿", 10).map { it.literal })
        // An exact literal match sorts ahead of a substring match on another row.
        assertEquals("出", store.searchKanji("出", 10).first().literal)
    }

    @Test
    fun searchHonoursItsLimitAndRejectsUselessInput() {
        val store = store()

        assertEquals(1, store.searchKanji("to", 1).size)
        assertTrue(store.searchKanji("", 10).isEmpty())
        assertTrue(store.searchKanji(null, 10).isEmpty())
        assertTrue(store.searchKanji("to", 0).isEmpty())
        assertTrue(store.searchKanji("to", -1).isEmpty())
    }

    @Test
    fun aSearchTermIsBoundSoItCannotAlterTheQuery() {
        val store = store()

        // A quote-and-comment payload finds nothing and, more to the point, does not
        // change the statement: interpolated, the trailing `--` would comment out the
        // ORDER BY and LIMIT, and the query would either error or return everything.
        assertTrue(store.searchKanji("' OR 1=1 --", 10).isEmpty())
        assertTrue(store.searchKanji("'; DROP TABLE kanji; --", 10).isEmpty())
        // The table is still there, which is the assertion that matters.
        assertEquals(3, store.kanjiCount())

        // `%` matching every row is not injection, it is LIKE doing its job on a bound
        // pattern — `"%" + "%" + "%"`. Android's reader builds the same pattern, so this
        // pins the parity rather than pretending the wildcard is escaped.
        assertEquals(3, store.searchKanji("%", 10).size)
    }

    @Test
    fun rankRangesCountAndPageInRankOrder() {
        val store = store()
        val ranked = DictionaryLookup.JitenRankRange(1, 10, false)

        assertEquals(2, store.eligibleKanjiCount(ranked))
        val page = store.kanjiByJitenRank(ranked, 0, 10)
        assertEquals(listOf("出", "脱"), page.entries.map { it.literal })
        assertEquals(2, page.totalEligible)
        // Exhausted, so null rather than 2: a caller paging while nextOffset is non-null
        // would loop forever on the final empty page.
        assertNull(page.nextOffset)
    }

    @Test
    fun anUnrankedKanjiIsIncludedOnlyWhenAskedForAndSortsLast() {
        val store = store()
        val withUnranked = DictionaryLookup.JitenRankRange(1, 10, true)

        assertEquals(3, store.eligibleKanjiCount(withUnranked))
        val page = store.kanjiByJitenRank(withUnranked, 0, 10)
        // Documented behaviour: unknown-rank kanji import and sort last.
        assertEquals(listOf("出", "脱", "麿"), page.entries.map { it.literal })
    }

    @Test
    fun aPartialPageReportsTheNextOffset() {
        val store = store()
        val withUnranked = DictionaryLookup.JitenRankRange(1, 10, true)

        val first = store.kanjiByJitenRank(withUnranked, 0, 2)
        assertEquals(listOf("出", "脱"), first.entries.map { it.literal })
        assertEquals(2, first.nextOffset)

        val second = store.kanjiByJitenRank(withUnranked, 2, 2)
        assertEquals(listOf("麿"), second.entries.map { it.literal })
        assertNull(second.nextOffset)
    }

    @Test
    fun invalidPagingArgumentsYieldAnEmptyPage() {
        val store = store()
        val ranked = DictionaryLookup.JitenRankRange(1, 10, false)

        assertTrue(store.kanjiByJitenRank(ranked, -1, 10).entries.isEmpty())
        assertTrue(store.kanjiByJitenRank(ranked, 0, 0).entries.isEmpty())
        // An inverted range is invalid, and must count zero rather than match everything.
        val inverted = DictionaryLookup.JitenRankRange(10, 1, false)
        assertEquals(0, store.eligibleKanjiCount(inverted))
        assertTrue(store.kanjiByJitenRank(inverted, 0, 10).entries.isEmpty())
    }

    @Test
    fun aMissingFileIsAbsentRatherThanAnEmptyDictionaryOrACrash() {
        val missing = DesktopDictionaryStore.open(directory.resolve("not-unpacked.db"))

        // SQLite would happily create an empty database at a path that does not exist,
        // turning "assets were not unpacked" into "the dictionary has no kanji" — a far
        // more confusing report. And the file must not be created as a side effect.
        assertTrue(missing.absent)
        assertEquals(0, missing.kanjiCount())
        assertNull(missing.lookupKanji("脱"))
        assertTrue(missing.searchKanji("to exit", 10).isEmpty())
        assertEquals(0, missing.jitenRanks().size())
        assertFalse(Files.exists(directory.resolve("not-unpacked.db")))
    }

    @Test
    fun aDirectoryAtTheDatabasePathIsAlsoAbsent() {
        val asDirectory = directory.resolve("kanji_dictionary_dir.db")
        Files.createDirectory(asDirectory)

        assertTrue(DesktopDictionaryStore.open(asDirectory).absent)
    }

    @Test
    fun anExplicitlyAbsentStoreNeverOpensAConnection() {
        val absent = DesktopDictionaryStore.absent()

        // The factory's lambda throws if called, so these passing proves no open happened.
        assertTrue(absent.absent)
        assertEquals(0, absent.kanjiCount())
        assertNull(absent.lookupKanji("出"))
        assertEquals(
            0,
            absent.eligibleKanjiCount(DictionaryLookup.JitenRankRange(1, 10, true)),
        )
        assertTrue(
            absent.kanjiByJitenRank(DictionaryLookup.JitenRankRange(1, 10, true), 0, 10)
                .entries.isEmpty(),
        )
    }

    @Test
    fun aCorruptDatabaseDegradesToEmptyRatherThanThrowing() {
        val corrupt = directory.resolve("corrupt.db")
        Files.writeString(corrupt, "this is not a SQLite file")
        val store = DesktopDictionaryStore.open(corrupt)

        // Not absent — the file exists and is readable — but every query fails. A lookup
        // is called during layout, so this must degrade rather than propagate.
        assertFalse(store.absent)
        assertEquals(0, store.kanjiCount())
        assertNull(store.lookupKanji("脱"))
        assertTrue(store.searchKanji("to exit", 10).isEmpty())
        assertEquals(0, store.jitenRanks().size())
    }

    @Test
    fun theDictionaryIsOpenedReadOnly() {
        val store = store()
        val before = Files.getLastModifiedTime(databaseFile)

        repeat(5) { store.lookupKanji("脱") }

        // No WAL or journal sidecar, and the file untouched: a read-write handle would
        // create both next to a file the installer owns.
        assertFalse(Files.exists(directory.resolve("kanji_dictionary.db-wal")))
        assertFalse(Files.exists(directory.resolve("kanji_dictionary.db-journal")))
        assertEquals(before, Files.getLastModifiedTime(databaseFile))
    }

    @Test
    fun repeatedReadsDoNotLeakConnections() {
        val store = store()

        // Each call opens and closes its own driver. If a handle leaked, a few hundred
        // reads would exhaust file descriptors before this finished.
        repeat(300) { index ->
            assertNotNull(store.lookupKanji(if (index % 2 == 0) "脱" else "出"))
        }
    }

    private companion object {
        /** The generator's list separator: ASCII unit separator. */
        const val US = "\u001f"
    }
}

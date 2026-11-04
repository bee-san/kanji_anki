package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.data.sql.SqlConnection
import dev.bee.kanjianki.data.sql.SqlConnectionMode
import dev.bee.kanjianki.data.sql.SqlRow
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop's [DictionaryLookup] over the bundled KANJIDIC2/Jiten database.
 *
 * The Android implementation is 823 lines, but almost none of that is the dictionary: it
 * is asset extraction, checksum verification, manifest parsing, connection invalidation
 * on file change, and an `org.json` reader — all of which exist because Android ships the
 * database inside an APK and must copy it out to private storage before SQLite can open
 * it. A desktop install has the file on disk already. What is left is the queries, and
 * they are ordinary SQLite with no Android in them.
 *
 * **Opened `READ_ONLY`, always.** The dictionary is shipped content, not user data: it is
 * never written, and every process that opens it opens the same bytes. A read-write handle
 * would let a bug or a stray `PRAGMA` modify content the checksum is supposed to
 * guarantee, and would put a WAL and a lock next to a file the installer owns.
 *
 * **A missing or unopenable database is [absent], not an exception.** The dictionary is
 * optional in exactly one real situation — a portable install whose reference assets were
 * not unpacked — and Kani's behaviour there is documented: the Missing Kanji report shows
 * no candidates rather than failing. Throwing from the constructor would take down
 * startup for a feature the user may never open.
 *
 * Connections are opened per call and closed. Dictionary reads are infrequent (a report
 * scan, a detail view) and a long-lived connection would need the invalidation machinery
 * the Android store carries for a case desktop does not have: the file cannot change
 * under a running install without an update, which restarts the process.
 */
class DesktopDictionaryStore private constructor(
    private val databaseFile: Path?,
    private val openConnection: (Path) -> SqlConnection,
) : DictionaryLookup() {
    /** True when no dictionary is available, so every lookup is empty rather than wrong. */
    val absent: Boolean get() = databaseFile == null

    override fun lookupKanji(literal: String?): KanjiEntry? {
        val normalized = literal?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return read(null) { connection ->
            connection.prepare("$ENTRY_COLUMNS FROM kanji WHERE literal = ? LIMIT 1").use { statement ->
                statement.bindText(1, normalized)
                statement.query().use { rows ->
                    if (rows.next()) entry(rows.row) else null
                }
            }
        }
    }

    override fun kanjiCount(): Int = read(0) { connection ->
        connection.prepare("SELECT COUNT(*) FROM kanji").use { statement ->
            statement.query().use { rows -> if (rows.next()) rows.row.long(0).toInt() else 0 }
        }
    }

    override fun jitenRanks(): JitenKanjiRanks = read(JitenKanjiRanks.empty()) { connection ->
        val ranks = LinkedHashMap<String, Int>()
        connection
            .prepare("SELECT literal, rank FROM jiten_ranks ORDER BY rank ASC, literal ASC")
            .use { statement ->
                statement.query().use { rows ->
                    while (rows.next()) {
                        ranks[rows.row.text(0)] = rows.row.long(1).toInt()
                    }
                }
            }
        JitenKanjiRanks(ranks)
    }

    override fun searchKanji(query: String?, limit: Int): List<KanjiEntry> {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty() || limit < 1) return emptyList()
        return read(emptyList()) { connection ->
            connection
                .prepare(
                    "$ENTRY_COLUMNS FROM kanji " +
                        "WHERE meanings LIKE ? OR on_readings LIKE ? OR kun_readings LIKE ? " +
                        "OR literal = ? " +
                        "ORDER BY CASE WHEN literal = ? THEN 0 ELSE 1 END, " +
                        "CASE WHEN jiten_rank IS NULL THEN 1 ELSE 0 END, jiten_rank ASC, literal ASC " +
                        "LIMIT ?",
                )
                .use { statement ->
                    // Bound, never interpolated: a query is user text, and `%` around it
                    // is a LIKE pattern rather than a string that could close a literal.
                    val pattern = "%$trimmed%"
                    statement.bindText(1, pattern)
                    statement.bindText(2, pattern)
                    statement.bindText(3, pattern)
                    statement.bindText(4, trimmed)
                    statement.bindText(5, trimmed)
                    statement.bindLong(6, limit.toLong())
                    statement.query().use { rows ->
                        buildList { while (rows.next()) add(entry(rows.row)) }
                    }
                }
        }
    }

    override fun eligibleKanjiCount(range: JitenRankRange): Int {
        if (!range.isValid()) return 0
        return read(0) { connection ->
            connection
                .prepare("SELECT COUNT(*) FROM kanji WHERE ${rangeSelection(range)}")
                .use { statement ->
                    bindRange(statement, range)
                    statement.query().use { rows ->
                        if (rows.next()) rows.row.long(0).toInt() else 0
                    }
                }
        }
    }

    override fun kanjiByJitenRank(
        range: JitenRankRange,
        offset: Int,
        limit: Int,
    ): KanjiEntryPage {
        if (!range.isValid() || offset < 0 || limit < 1) return KanjiEntryPage.empty()
        val total = eligibleKanjiCount(range)
        if (total == 0) return KanjiEntryPage.empty()
        val entries = read(emptyList<KanjiEntry>()) { connection ->
            connection
                .prepare(
                    "$ENTRY_COLUMNS FROM kanji WHERE ${rangeSelection(range)} " +
                        "ORDER BY ${rangeOrder(range)} LIMIT ? OFFSET ?",
                )
                .use { statement ->
                    bindRange(statement, range)
                    statement.bindLong(3, limit.toLong())
                    statement.bindLong(4, offset.toLong())
                    statement.query().use { rows ->
                        buildList { while (rows.next()) add(entry(rows.row)) }
                    }
                }
        }
        val consumed = offset + entries.size
        return KanjiEntryPage(
            entries = entries,
            totalEligible = total,
            // Null rather than the next index when the page is exhausted: a caller that
            // paged on a non-null offset forever would loop on the last empty page.
            nextOffset = if (consumed < total && entries.isNotEmpty()) consumed else null,
        )
    }

    /**
     * Runs [block] on a fresh read-only connection, or returns [fallback].
     *
     * Failures are swallowed by design and the reason is the same as [absent]: a
     * dictionary is reference content, and a corrupt or locked one must degrade a report
     * to empty rather than propagate out of a lookup the UI called during layout.
     */
    private fun <T> read(fallback: T, block: (SqlConnection) -> T): T {
        val file = databaseFile ?: return fallback
        return try {
            openConnection(file).use(block)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun bindRange(statement: dev.bee.kanjianki.data.sql.SqlStatement, range: JitenRankRange) {
        statement.bindLong(1, range.minimumRank.toLong())
        statement.bindLong(2, range.maximumRank.toLong())
    }

    companion object {
        /**
         * The projection every entry read shares.
         *
         * Named columns rather than `SELECT *`, unlike the Android store's search query:
         * `entry` reads by index, so a column added to the shipped schema would silently
         * shift every field across. The Android version reads by name and is safe from
         * that; this one has to pin the order.
         */
        private const val ENTRY_COLUMNS =
            "SELECT literal, meanings, on_readings, kun_readings, nanori_readings, " +
                "stroke_count, grade, radical, kanjidic_frequency, jiten_rank"

        /**
         * Opens the dictionary at [databaseFile], or an absent store when it is missing.
         *
         * The file is required to be a regular readable file *before* any connection is
         * attempted, because SQLite will happily create an empty database at a path that
         * does not exist — which would turn "assets were not unpacked" into "the
         * dictionary contains no kanji", a far more confusing report.
         */
        fun open(
            databaseFile: Path,
            openConnection: (Path) -> SqlConnection = ::readOnlyConnection,
        ): DesktopDictionaryStore {
            val usable = Files.isRegularFile(databaseFile) && Files.isReadable(databaseFile)
            return DesktopDictionaryStore(
                databaseFile = if (usable) databaseFile else null,
                openConnection = openConnection,
            )
        }

        /** An explicitly absent store, for a profile with no reference assets. */
        fun absent(): DesktopDictionaryStore =
            DesktopDictionaryStore(null) { error("an absent dictionary never opens a connection") }

        private fun readOnlyConnection(databaseFile: Path): SqlConnection {
            val driver = BundledSqlDriver(databaseFile.toString())
            return try {
                CloseDriverConnection(driver.openConnection(SqlConnectionMode.READ_ONLY), driver)
            } catch (failure: Throwable) {
                // The driver holds a native handle, so a failed open must not leak it.
                runCatching { driver.close() }
                throw failure
            }
        }
    }

    /** Closes the driver with the connection, so a per-call open leaks no native handle. */
    private class CloseDriverConnection(
        private val delegate: SqlConnection,
        private val driver: AutoCloseable,
    ) : SqlConnection by delegate {
        override fun close() {
            try {
                delegate.close()
            } finally {
                driver.close()
            }
        }
    }
}

private fun rangeSelection(range: DictionaryLookup.JitenRankRange): String {
    val ranked = "jiten_rank BETWEEN ? AND ?"
    return if (range.includeUnranked) "($ranked OR jiten_rank IS NULL)" else ranked
}

private fun rangeOrder(range: DictionaryLookup.JitenRankRange): String =
    if (range.includeUnranked) {
        "CASE WHEN jiten_rank IS NULL THEN 1 ELSE 0 END, jiten_rank ASC, literal ASC"
    } else {
        "jiten_rank ASC, literal ASC"
    }

private fun entry(row: SqlRow): DictionaryLookup.KanjiEntry = DictionaryLookup.KanjiEntry(
    DictionaryLookup.KanjiEntryFields(
        row.text(0),
        splitList(row, 1),
        splitList(row, 2),
        splitList(row, 3),
        splitList(row, 4),
        row.long(5).toInt(),
        row.long(6).toInt(),
        row.long(7).toInt(),
        row.long(8).toInt(),
        if (row.isNull(9)) null else row.long(9).toInt(),
    ),
)

private fun splitList(row: SqlRow, index: Int): List<String> {
    if (row.isNull(index)) return emptyList()
    return row.text(index)
        .split(LIST_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/**
 * The shipped list separator: ASCII unit separator, `U+001F`.
 *
 * The same value `DictionaryStore.LIST_SEPARATOR` uses on Android, and it has to be
 * exact. A plausible-looking `[;,]` split — which this first had — silently returns one
 * run-together string per row instead of a list, because a meaning like "to escape, to
 * flee" legitimately contains a comma. The generator picked a control character precisely
 * so that content punctuation is never a delimiter.
 */
private const val LIST_SEPARATOR = "\u001f"

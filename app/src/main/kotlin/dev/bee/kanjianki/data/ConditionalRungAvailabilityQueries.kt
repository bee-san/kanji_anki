package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Targeted capability lookups for the conditional study rungs.
 *
 * The original annotation path built four global sets on first use. That made opening Home scan
 * every example and reading row even though the dashboard asks about at most 120 kanji. These
 * queries preserve the same predicates while restricting work to the kanji being annotated.
 */
internal object ConditionalRungAvailabilityQueries {
    internal const val QUERY_CHUNK_SIZE = 400

    fun similarKanji(db: SQLiteDatabase, requestedKanji: Collection<String>): Set<String> {
        val requested = normalized(requestedKanji)
        if (requested.isEmpty()) {
            return emptySet()
        }
        val result = HashSet<String>()
        for (chunk in requested.chunked(QUERY_CHUNK_SIZE)) {
            val placeholders = placeholders(chunk.size)
            val bothInInventory =
                "p.${LocalStoreBase.COLUMN_KANJI_A} IN " +
                    "(SELECT ${LocalStoreBase.COLUMN_KANJI} FROM ${LocalStoreBase.TABLE_KANJI_INVENTORY}) AND " +
                    "p.${LocalStoreBase.COLUMN_KANJI_B} IN " +
                    "(SELECT ${LocalStoreBase.COLUMN_KANJI} FROM ${LocalStoreBase.TABLE_KANJI_INVENTORY})"
            val sql =
                "SELECT p.${LocalStoreBase.COLUMN_KANJI_A} " +
                    "FROM ${LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS} p " +
                    "WHERE p.${LocalStoreBase.COLUMN_KANJI_A} IN ($placeholders) AND $bothInInventory " +
                    "UNION SELECT p.${LocalStoreBase.COLUMN_KANJI_B} " +
                    "FROM ${LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS} p " +
                    "WHERE p.${LocalStoreBase.COLUMN_KANJI_B} IN ($placeholders) AND $bothInInventory"
            querySingleColumn(db, sql, (chunk + chunk).toTypedArray(), result)
        }
        return result
    }

    fun kanjiReading(db: SQLiteDatabase, requestedKanji: Collection<String>): Set<String> {
        val requested = normalized(requestedKanji)
        if (requested.isEmpty()) {
            return emptySet()
        }
        val result = HashSet<String>()
        for (chunk in requested.chunked(QUERY_CHUNK_SIZE)) {
            val sql =
                "SELECT DISTINCT u.${LocalStoreBase.COLUMN_KANJI} " +
                    "FROM ${LocalStoreBase.TABLE_KANJI_READING_USAGE} u " +
                    "WHERE u.${LocalStoreBase.COLUMN_KANJI} IN (${placeholders(chunk.size)}) " +
                    "AND (SELECT COUNT(*) FROM ${LocalStoreBase.TABLE_KANJI_READING_POOL} p " +
                    "WHERE p.${LocalStoreBase.COLUMN_KANJI} = u.${LocalStoreBase.COLUMN_KANJI}) >= 2"
            querySingleColumn(db, sql, chunk.toTypedArray(), result)
        }
        return result
    }

    fun readingKanji(db: SQLiteDatabase, requestedKanji: Collection<String>): Set<String> {
        val requested = normalized(requestedKanji)
        if (requested.isEmpty()) {
            return emptySet()
        }
        val result = HashSet<String>()
        for (chunk in requested.chunked(QUERY_CHUNK_SIZE)) {
            querySingleColumn(db, readingKanjiSql(chunk.size), chunk.toTypedArray(), result)
        }
        return result
    }

    /**
     * One statement per requested-kanji chunk. [targetReadings] is bounded by the requested keys,
     * while [sharedReadings] deliberately counts distinct kanji across the complete usage table:
     * the two distractors that make a reading-kanji card valid need not be requested themselves.
     */
    internal fun readingKanjiSql(requestedCount: Int): String {
        require(requestedCount > 0)
        val requestedValues = List(requestedCount) { "(?)" }.joinToString(",")
        return "WITH requested(${LocalStoreBase.COLUMN_KANJI}) AS (VALUES $requestedValues), " +
            "target_readings(${LocalStoreBase.COLUMN_KANJI}, ${LocalStoreBase.COLUMN_READING}) AS (" +
            "SELECT u.${LocalStoreBase.COLUMN_KANJI}, u.${LocalStoreBase.COLUMN_READING} " +
            "FROM requested r JOIN ${LocalStoreBase.TABLE_KANJI_READING_USAGE} u " +
            "ON u.${LocalStoreBase.COLUMN_KANJI} = r.${LocalStoreBase.COLUMN_KANJI} " +
            "GROUP BY u.${LocalStoreBase.COLUMN_KANJI}, u.${LocalStoreBase.COLUMN_READING}), " +
            "shared_readings(${LocalStoreBase.COLUMN_READING}) AS (" +
            "SELECT t.${LocalStoreBase.COLUMN_READING} " +
            "FROM (SELECT DISTINCT ${LocalStoreBase.COLUMN_READING} FROM target_readings) t " +
            "CROSS JOIN ${LocalStoreBase.TABLE_KANJI_READING_USAGE} u " +
            "WHERE u.${LocalStoreBase.COLUMN_READING} = t.${LocalStoreBase.COLUMN_READING} " +
            "GROUP BY t.${LocalStoreBase.COLUMN_READING} " +
            "HAVING COUNT(DISTINCT u.${LocalStoreBase.COLUMN_KANJI}) >= 3) " +
            "SELECT t.${LocalStoreBase.COLUMN_KANJI} FROM target_readings t " +
            "JOIN shared_readings s " +
            "ON s.${LocalStoreBase.COLUMN_READING} = t.${LocalStoreBase.COLUMN_READING} " +
            "GROUP BY t.${LocalStoreBase.COLUMN_KANJI}"
    }

    internal fun readingKanjiStatementCount(requestedCount: Int): Int {
        require(requestedCount >= 0)
        return if (requestedCount == 0) 0 else (requestedCount + QUERY_CHUNK_SIZE - 1) / QUERY_CHUNK_SIZE
    }

    fun sentenceReading(db: SQLiteDatabase, requestedKanji: Collection<String>): Set<String> {
        val requested = normalized(requestedKanji)
        if (requested.isEmpty()) {
            return emptySet()
        }
        val result = HashSet<String>()
        for (chunk in requested.chunked(QUERY_CHUNK_SIZE)) {
            querySingleColumn(db, sentenceReadingSql(chunk.size), chunk.toTypedArray(), result)
        }
        return result
    }

    internal fun sentenceReadingSql(requestedCount: Int): String {
        require(requestedCount > 0)
        val requestedValues = List(requestedCount) { "(?)" }.joinToString(",")
        return "WITH requested(${LocalStoreBase.COLUMN_KANJI}) AS (VALUES $requestedValues) " +
            "SELECT r.${LocalStoreBase.COLUMN_KANJI} FROM requested r WHERE EXISTS (" +
            "SELECT 1 FROM ${LocalStoreBase.TABLE_KANJI_EXAMPLES} e " +
            "WHERE e.${LocalStoreBase.COLUMN_KANJI} = r.${LocalStoreBase.COLUMN_KANJI} " +
            "AND e.${LocalStoreBase.COLUMN_SENTENCE} IS NOT NULL " +
            "AND TRIM(e.${LocalStoreBase.COLUMN_SENTENCE}) <> '' " +
            "AND e.${LocalStoreBase.COLUMN_READING} IS NOT NULL " +
            "AND TRIM(e.${LocalStoreBase.COLUMN_READING}) <> '' LIMIT 1)"
    }

    private fun normalized(kanji: Collection<String>): List<String> {
        return kanji.asSequence().filter { it.isNotBlank() }.distinct().toList()
    }

    private fun placeholders(size: Int): String = List(size) { "?" }.joinToString(",")

    private fun querySingleColumn(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>,
        destination: MutableSet<String>,
    ) {
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(0)
                if (!value.isNullOrEmpty()) {
                    destination.add(value)
                }
            }
        }
    }
}

internal enum class ConditionalRungCapability {
    SIMILAR_KANJI,
    KANJI_READING,
    READING_KANJI,
    SENTENCE_READING,
}

/**
 * Per-kanji positive/negative capability cache with generation-checked publication.
 *
 * Queries intentionally run outside [publicationLock]. Invalidation increments [generation] and
 * clears every capability while holding that lock. A query that began against the old database
 * generation therefore either publishes before invalidation (and is then cleared) or observes the
 * generation mismatch and retries; it can never publish an old result after the clear.
 */
internal class ConditionalRungAvailabilityCache {
    private val publicationLock = Any()
    private val generation = AtomicLong(0L)
    private val valuesByCapability = ConditionalRungCapability.values().associateWith {
        ConcurrentHashMap<String, Boolean>()
    }

    fun invalidate() {
        synchronized(publicationLock) {
            generation.incrementAndGet()
            valuesByCapability.values.forEach { it.clear() }
        }
    }

    fun load(
        capability: ConditionalRungCapability,
        requestedKanji: Collection<String>,
        loader: (Collection<String>) -> Set<String>,
    ): Set<String> {
        val requested = requestedKanji.asSequence().filter { it.isNotBlank() }.distinct().toList()
        if (requested.isEmpty()) {
            return emptySet()
        }
        val cache = valuesByCapability.getValue(capability)
        while (true) {
            val observedGeneration = generation.get()
            val missing = requested.filter { !cache.containsKey(it) }
            if (missing.isNotEmpty()) {
                val loaded = loader(missing)
                val published = synchronized(publicationLock) {
                    if (generation.get() != observedGeneration) {
                        false
                    } else {
                        for (kanji in missing) {
                            cache[kanji] = loaded.contains(kanji)
                        }
                        true
                    }
                }
                if (!published) {
                    continue
                }
            }
            val result = requested.filterTo(LinkedHashSet()) { cache[it] == true }
            if (generation.get() == observedGeneration) {
                return result
            }
        }
    }

    internal fun generationForTest(): Long = generation.get()
}

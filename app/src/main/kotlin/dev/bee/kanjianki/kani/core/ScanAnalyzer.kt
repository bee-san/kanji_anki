package dev.bee.kanjianki.kani.core

/**
 * Turns the user's Anki history into the kanji Kani trains.
 *
 * A failing word only flags the kanji the user actually misses: a kanji that the user reads
 * fine in plenty of healthy words is cleared, so `待ち合わせ` flags 待, not 合.
 */
object ScanAnalyzer {
    enum class Reason { LEECH, FAILED, SUSPENDED }

    data class Word(
        val noteId: Long,
        val expression: String,
        /** Null for a healthy or not-yet-judged word. */
        val problem: Reason?,
        /** Reviewed for a while without lapses. */
        val healthy: Boolean,
        /** Still a new card in Anki. */
        val isNew: Boolean,
    )

    data class Flagged(
        val kanji: String,
        val reason: Reason,
        val problemNoteIds: Set<Long>,
        val healthyWords: Int,
    )

    data class Cleared(val kanji: String, val problemNoteIds: Set<Long>, val healthyWords: Int)

    data class Result(
        val wordsScanned: Int,
        val distinctKanji: Int,
        val flagged: List<Flagged>,
        val cleared: List<Cleared>,
        val reasonCounts: Map<Reason, Int>,
        /** Every kanji that appears in a reviewed (not new) word. */
        val known: Set<String>,
        /** New-in-Anki kanji that look like a known one: kanji → the known look-alike. */
        val early: Map<String, String>,
    )

    const val CLEARED_MIN_HEALTHY = 3

    /** [similar] lists a kanji's look-alikes closest first: a new kanji is paired with the first one known. */
    fun analyze(scanned: List<Word>, similar: (String) -> List<String>): Result {
        // A new card suspended before it was ever studied says nothing about the user: it is no
        // problem, and no kanji they are about to meet in Anki either.
        val words = scanned.filterNot { it.isNew && it.problem == Reason.SUSPENDED }
        val problemNotes = HashMap<String, MutableSet<Long>>()
        val problemReasons = HashMap<String, MutableList<Reason>>()
        val healthyCount = HashMap<String, Int>()
        val known = HashSet<String>()
        val newOnly = HashSet<String>()
        val all = HashSet<String>()
        for (w in words) {
            val kanji = AnkiText.kanjiIn(w.expression)
            all += kanji
            if (w.isNew) newOnly += kanji else known += kanji
            if (w.healthy) kanji.forEach { healthyCount[it] = (healthyCount[it] ?: 0) + 1 }
        }
        newOnly.removeAll(known)

        val flagged = LinkedHashSet<String>()
        val cleared = LinkedHashMap<String, Cleared>()
        for (w in words) {
            val reason = w.problem ?: continue
            val kanji = AnkiText.kanjiIn(w.expression)
            if (kanji.isEmpty()) continue
            val culprits = kanji.filterNot { isCleared(healthyCount[it] ?: 0) }
                // Every failing word yields at least one kanji: the least-proven one.
                .ifEmpty { listOf(kanji.minBy { healthyCount[it] ?: 0 }) }
            for (k in kanji) {
                problemNotes.getOrPut(k) { LinkedHashSet() } += w.noteId
                problemReasons.getOrPut(k) { ArrayList() } += reason
            }
            for (k in kanji - culprits.toSet()) {
                cleared[k] = Cleared(k, problemNotes.getValue(k), healthyCount[k] ?: 0)
            }
            flagged += culprits
        }
        val flaggedList = flagged.map { k ->
            Flagged(
                kanji = k,
                reason = problemReasons.getValue(k).minBy { it.ordinal },
                problemNoteIds = problemNotes.getValue(k),
                healthyWords = healthyCount[k] ?: 0,
            )
        }.sortedWith(compareBy<Flagged> { it.reason.ordinal }.thenByDescending { it.problemNoteIds.size })
        val clearedList = cleared.values.filter { it.kanji !in flagged }

        val reasonCounts = Reason.entries.associateWith { r -> flaggedList.count { it.reason == r } }

        val early = LinkedHashMap<String, String>()
        for (k in newOnly) {
            val lookAlike = similar(k).firstOrNull { it in known } ?: continue
            early[k] = lookAlike
        }
        return Result(words.size, all.size, flaggedList, clearedList, reasonCounts, known, early)
    }

    fun isCleared(healthyWords: Int): Boolean = healthyWords >= CLEARED_MIN_HEALTHY
}

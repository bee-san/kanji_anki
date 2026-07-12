package dev.bee.kanjianki.core

import java.security.SecureRandom
import java.util.Random

/**
 * Pure, stateless planner for the `kanji_reading` rung (Goal 78). Builds a
 * forced-choice card: "How is <targetKanji> read in <word>?" with the correct
 * reading plus distractors drawn from the kanji's OTHER canonical readings.
 *
 * Word selection prefers a usage row whose reading has weak evidence (not
 * mature, or lapses > 0 — the unfamiliar reading the learner is failing), else
 * any attested word; ties break deterministically by note id.
 *
 * Distractor ordering (D-R1: target without touching movement semantics):
 * mature-attested readings first (the exact confusion), then other attested
 * readings, then dictionary-only pool readings. If fewer than
 * [MIN_CHOICE_COUNT] total choices can be built the planner returns null and
 * the caller falls back to a plain flashcard.
 */
object KanjiReadingChoicePlanner {
    const val MIN_CHOICE_COUNT: Int = 2
    const val MAX_CHOICE_COUNT: Int = 4

    /** One attested usage of the target kanji, from `kanji_reading_usage`. */
    @JvmRecord
    data class Usage(
        val word: String,
        val reading: String,
        val meaning: String,
        val noteId: Long,
        val mature: Boolean,
        val lapses: Int,
    )

    /** A candidate reading in the kanji's pool, with attestation/maturity. */
    @JvmRecord
    data class PoolReading(
        val reading: String,
        val attested: Boolean,
        val matureAttested: Boolean,
    )

    fun buildChoiceCard(
        targetKanji: String?,
        usages: List<Usage>?,
        pool: List<PoolReading>?,
        random: Random?,
    ): RecordsImportModels.KanjiReadingChoiceCard? {
        val kanji = targetKanji?.trim().orEmpty()
        if (kanji.isEmpty()) {
            return null
        }
        val safeUsages = usages.orEmpty().filter { it.word.isNotBlank() && it.reading.isNotBlank() }
        if (safeUsages.isEmpty()) {
            return null
        }
        val prompt = selectPromptUsage(safeUsages) ?: return null
        return buildForPrompt(kanji, prompt, pool, random)
    }

    /**
     * Builds a repair card for the exact usage that produced the failed core
     * check. A missing persisted usage fails closed instead of drifting to a
     * different word.
     */
    @JvmStatic
    fun buildExactChoiceCard(
        targetKanji: String?,
        exactWord: String?,
        exactReading: String?,
        usages: List<Usage>?,
        pool: List<PoolReading>?,
        random: Random?,
    ): RecordsImportModels.KanjiReadingChoiceCard? {
        val kanji = targetKanji?.trim().orEmpty()
        val word = exactWord?.trim().orEmpty()
        val reading = exactReading?.trim().orEmpty()
        if (kanji.isEmpty() || word.isEmpty() || reading.isEmpty()) return null
        val prompt = usages.orEmpty().firstOrNull {
            it.word.trim() == word && it.reading.trim() == reading
        } ?: return null
        return buildForPrompt(kanji, prompt, pool, random)
    }

    private fun buildForPrompt(
        kanji: String,
        prompt: Usage,
        pool: List<PoolReading>?,
        random: Random?,
    ): RecordsImportModels.KanjiReadingChoiceCard? {
        val correct = prompt.reading.trim()

        val distractors = orderedDistractors(correct, pool)
        if (distractors.isEmpty()) {
            return null
        }
        val rng = random ?: SecureRandom()
        val choices = ArrayList<String>()
        choices.add(correct)
        for (distractor in distractors) {
            if (choices.size >= MAX_CHOICE_COUNT) {
                break
            }
            if (!choices.contains(distractor)) {
                choices.add(distractor)
            }
        }
        if (choices.size < MIN_CHOICE_COUNT) {
            return null
        }
        choices.shuffle(rng)
        return RecordsImportModels.KanjiReadingChoiceCard(kanji, prompt.word, prompt.meaning, correct, choices)
    }

    /**
     * Prefer the weakest-evidence usage (not mature, or with lapses), so the
     * card drills the reading the learner is actually failing; deterministic
     * given equal evidence via a stable note-id sort.
     */
    private fun selectPromptUsage(usages: List<Usage>): Usage? {
        return usages.sortedWith(
            compareByDescending<Usage> { weaknessScore(it) }.thenBy { it.noteId }.thenBy { it.word },
        ).firstOrNull()
    }

    private fun weaknessScore(usage: Usage): Int {
        var score = 0
        if (!usage.mature) {
            score += 2
        }
        if (usage.lapses > 0) {
            score += 1
        }
        return score
    }

    /**
     * The kanji's other canonical readings as distractors, ordered
     * mature-attested → attested → dictionary-only, excluding the correct
     * reading. Ordering within each tier is by reading for determinism.
     */
    private fun orderedDistractors(correct: String, pool: List<PoolReading>?): List<String> {
        val candidates = pool.orEmpty()
            .map { it.reading.trim() to it }
            .filter { it.first.isNotEmpty() && it.first != correct }
        val matureAttested = candidates.filter { it.second.matureAttested }.map { it.first }.sorted()
        val attested = candidates
            .filter { it.second.attested && !it.second.matureAttested }
            .map { it.first }
            .sorted()
        val dictionaryOnly = candidates
            .filter { !it.second.attested }
            .map { it.first }
            .sorted()
        val ordered = ArrayList<String>()
        for (reading in matureAttested + attested + dictionaryOnly) {
            if (!ordered.contains(reading)) {
                ordered.add(reading)
            }
        }
        return ordered
    }
}

package dev.bee.kanjianki.core

import java.security.SecureRandom
import java.util.Random

/**
 * Pure, stateless planner for the `reading_kanji` homophone-discrimination rung
 * (Goal 79): the production-side mirror of `kanji_reading`. Show a reading in
 * kana plus the attested word with the target kanji blanked (e.g. だつ — 〇出)
 * and its meaning gloss; the learner picks the correct kanji among same-reading
 * candidates.
 *
 * Distractor preference (D-R1): kanji whose shared reading is mature-attested,
 * then other inventory kanji sharing the reading. A minimum of
 * [MIN_CHOICE_COUNT] (3) choices is required — a 2-option homophone card is a
 * coin flip — else the caller falls back to a plain flashcard.
 */
object ReadingKanjiChoicePlanner {
    const val MIN_CHOICE_COUNT: Int = 3
    const val MAX_CHOICE_COUNT: Int = 4
    const val BLANK: String = "〇"

    /** An attested usage of the target kanji (from `kanji_reading_usage`). */
    @JvmRecord
    data class TargetUsage(
        val word: String,
        val reading: String,
        val meaning: String,
        val noteId: Long,
        val mature: Boolean,
        val lapses: Int,
    )

    /** A candidate distractor kanji that shares the target reading. */
    @JvmRecord
    data class Candidate(
        val kanji: String,
        val matureAttested: Boolean,
    )

    /**
     * @param targetKanji the answer kanji.
     * @param usages attested usages of the target kanji (its own words).
     * @param candidatesByReading distractor kanji keyed by canonical reading:
     *   the OTHER kanji that share each reading the target is attested with.
     */
    fun buildChoiceCard(
        targetKanji: String?,
        usages: List<TargetUsage>?,
        candidatesByReading: Map<String, List<Candidate>>?,
        random: Random?,
    ): RecordsImportModels.ReadingKanjiChoiceCard? {
        val kanji = targetKanji?.trim().orEmpty()
        if (kanji.isEmpty()) {
            return null
        }
        val safeUsages = usages.orEmpty().filter { it.word.isNotBlank() && it.reading.isNotBlank() }
        val safeCandidates = candidatesByReading.orEmpty()
        // Prefer a usage whose reading actually has a >= 2-other-kanji pool AND
        // is weak evidence; deterministic on ties.
        val prompt = selectPrompt(kanji, safeUsages, safeCandidates) ?: return null
        return buildForPrompt(kanji, prompt, safeCandidates, random)
    }

    /** Exact-focus counterpart used by inline homophone repair. */
    @JvmStatic
    fun buildExactChoiceCard(
        targetKanji: String?,
        exactWord: String?,
        exactSharedReading: String?,
        usages: List<TargetUsage>?,
        candidatesByReading: Map<String, List<Candidate>>?,
        random: Random?,
    ): RecordsImportModels.ReadingKanjiChoiceCard? {
        val kanji = targetKanji?.trim().orEmpty()
        val word = exactWord?.trim().orEmpty()
        val reading = exactSharedReading?.trim().orEmpty()
        if (kanji.isEmpty() || word.isEmpty() || reading.isEmpty()) return null
        val candidates = candidatesByReading.orEmpty()
        val prompt = usages.orEmpty().firstOrNull {
            it.word.trim() == word && it.reading.trim() == reading
        } ?: return null
        return buildForPrompt(kanji, prompt, candidates, random)
    }

    private fun buildForPrompt(
        kanji: String,
        prompt: TargetUsage,
        candidatesByReading: Map<String, List<Candidate>>,
        random: Random?,
    ): RecordsImportModels.ReadingKanjiChoiceCard? {
        val reading = prompt.reading.trim()
        val distractors = orderedDistractors(kanji, candidatesByReading[reading])
        if (distractors.size < MIN_CHOICE_COUNT - 1) {
            return null
        }
        val rng = random ?: SecureRandom()
        val choices = ArrayList<String>()
        choices.add(kanji)
        for (candidate in distractors) {
            if (choices.size >= MAX_CHOICE_COUNT) {
                break
            }
            if (!choices.contains(candidate)) {
                choices.add(candidate)
            }
        }
        if (choices.size < MIN_CHOICE_COUNT) {
            return null
        }
        choices.shuffle(rng)
        val blanked = blankTargetKanji(prompt.word, kanji)
        return RecordsImportModels.ReadingKanjiChoiceCard(kanji, reading, blanked, prompt.meaning, choices)
    }

    private fun selectPrompt(
        targetKanji: String,
        usages: List<TargetUsage>,
        candidatesByReading: Map<String, List<Candidate>>,
    ): TargetUsage? {
        // Only usages whose reading has a buildable distractor pool of >= 2 other
        // kanji qualify.
        val buildable = usages.filter { usage ->
            orderedDistractors(targetKanji, candidatesByReading[usage.reading.trim()]).size >= MIN_CHOICE_COUNT - 1
        }
        if (buildable.isEmpty()) {
            return null
        }
        return buildable.sortedWith(
            compareByDescending<TargetUsage> { weaknessScore(it) }.thenBy { it.noteId }.thenBy { it.word },
        ).first()
    }

    private fun weaknessScore(usage: TargetUsage): Int {
        var score = 0
        if (!usage.mature) {
            score += 2
        }
        if (usage.lapses > 0) {
            score += 1
        }
        return score
    }

    private fun orderedDistractors(targetKanji: String, candidates: List<Candidate>?): List<String> {
        val filtered = candidates.orEmpty().filter { it.kanji.isNotBlank() && it.kanji != targetKanji }
        val mature = filtered.filter { it.matureAttested }.map { it.kanji }.distinct().sorted()
        val other = filtered.filter { !it.matureAttested }.map { it.kanji }.distinct().sorted()
        val out = ArrayList<String>()
        for (kanji in mature + other) {
            if (!out.contains(kanji)) {
                out.add(kanji)
            }
        }
        return out
    }

    private fun blankTargetKanji(word: String, targetKanji: String): String {
        if (word.isEmpty()) {
            return targetKanji
        }
        // Replace only the first occurrence of the target kanji with the blank.
        val index = word.indexOf(targetKanji)
        if (index < 0) {
            return word
        }
        return word.substring(0, index) + BLANK + word.substring(index + targetKanji.length)
    }
}

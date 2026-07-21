package dev.bee.kanjianki.core

import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.Random

class KanjiGameEngine {
    enum class GameMode(
        @JvmField val id: String,
        @JvmField val title: String,
        @JvmField val label: String,
        @JvmField val prompt: String,
        @JvmField val lockedLabel: String = "",
    ) {
        MEANING_POP("meaning_pop", "Meaning Pop", "Kanji -> meaning", "Choose the meaning."),
        READING_RUSH("reading_rush", "Reading Rush", "Word -> reading", "Choose the reading."),
        CONFUSABLE_CLASH("confusable_clash", "Confusable Clash", "Meaning -> kanji", "Choose the kanji."),
        MISS_SWEEP("miss_sweep", "Miss Sweep", "Recent misses", "Choose the meaning.", "Need 2+ recent misses"),
    }

    class GameQuestion(
        @JvmField val mode: GameMode,
        targetKanji: String?,
        prompt: String?,
        promptDetail: String?,
        correctAnswer: String?,
        choices: List<String?>?,
        explanation: String?,
    ) {
        @JvmField val targetKanji: String = clean(targetKanji)
        @JvmField val prompt: String = clean(prompt)
        @JvmField val promptDetail: String = clean(promptDetail)
        @JvmField val correctAnswer: String = clean(correctAnswer)
        @JvmField val choices: List<String> = Collections.unmodifiableList(ArrayList(cleanChoices(choices)))
        @JvmField val explanation: String = clean(explanation)

        fun isCorrect(selectedAnswer: String?): Boolean {
            return answerKey(correctAnswer) == answerKey(selectedAnswer)
        }
    }

    fun availableModes(
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        recentMissKanji: List<String?>? = null,
    ): List<GameMode> {
        val out = ArrayList<GameMode>()
        for (mode in GameMode.values()) {
            if (nextQuestion(mode, rows, inventory, pairs, SecureRandom(), recentMissKanji) != null) {
                out.add(mode)
            }
        }
        return out
    }

    fun nextQuestion(
        mode: GameMode?,
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        random: Random?,
        recentMissKanji: List<String?>? = null,
    ): GameQuestion? {
        val safeMode = mode ?: GameMode.MEANING_POP
        val safeRandom = random ?: SecureRandom()
        val candidates = candidates(rows, inventory)
        return when (safeMode) {
            GameMode.MEANING_POP -> meaningQuestion(candidates, safeRandom)
            GameMode.READING_RUSH -> readingQuestion(candidates, safeRandom)
            GameMode.CONFUSABLE_CLASH -> confusableQuestion(candidates, pairs, safeRandom)
            GameMode.MISS_SWEEP -> missSweepQuestion(candidates, recentMissKanji, safeRandom)
        }
    }

    private data class GameCandidate(
        val kanji: String,
        val meaning: String,
        val reading: String,
        val expression: String,
        val fromDashboard: Boolean,
    ) {
        fun hasAnyAnswer(): Boolean = kanji.isNotEmpty() && (hasMeaning() || hasReading())

        fun hasMeaning(): Boolean = meaning.isNotEmpty()

        fun hasReading(): Boolean = reading.isNotEmpty()

        fun explanation(): String {
            if (meaning.isEmpty()) {
                return "$kanji = $reading"
            }
            return "$kanji = $reading · $meaning"
        }

        companion object {
            fun fromRow(row: RecordsImportModels.DashboardRow?): GameCandidate {
                if (row == null) {
                    return GameCandidate("", "", "", "", true)
                }
                var exampleMeaning = ""
                var exampleReading = ""
                var expression = ""
                for (example in row.examples) {
                    if (exampleMeaning.isEmpty()) {
                        exampleMeaning = example.meaning
                    }
                    if (expression.isEmpty() &&
                        example.expression.isNotBlank() &&
                        example.reading.isNotBlank()
                    ) {
                        expression = example.expression
                        exampleReading = example.reading
                    }
                }
                return GameCandidate(
                    clean(row.kanji),
                    firstNonEmpty(row.primaryMeaning, exampleMeaning),
                    if (expression.isEmpty()) {
                        firstNonEmpty(row.reading, exampleReading)
                    } else {
                        clean(exampleReading)
                    },
                    firstNonEmpty(expression, row.kanji),
                    true,
                )
            }

            fun fromInventory(item: RecordsImportModels.KanjiInventoryItem?): GameCandidate {
                if (item == null) {
                    return GameCandidate("", "", "", "", false)
                }
                return GameCandidate(
                    clean(item.kanji),
                    clean(item.primaryMeaning),
                    clean(item.readings),
                    clean(item.kanji),
                    false,
                )
            }

            private fun firstNonEmpty(first: String?, second: String?): String {
                val safeFirst = clean(first)
                return if (safeFirst.isEmpty()) clean(second) else safeFirst
            }
        }
    }

    companion object {
        private const val MAX_CHOICES = 4

        private fun meaningQuestion(candidates: List<GameCandidate>, random: Random): GameQuestion? {
            val targets = targets(candidates) { it.hasMeaning() }
            if (targets.isEmpty()) {
                return null
            }
            val target = randomCandidate(targets, random)
            val choices = answerChoices(candidates, target.meaning, { it.meaning }, random)
            if (choices.size < 2) {
                return null
            }
            return GameQuestion(
                GameMode.MEANING_POP,
                target.kanji,
                target.kanji,
                "Pick the meaning",
                target.meaning,
                choices,
                "${target.kanji} = ${target.meaning}",
            )
        }

        private fun readingQuestion(candidates: List<GameCandidate>, random: Random): GameQuestion? {
            val targets = targets(candidates) { it.hasReading() }
            if (targets.isEmpty()) {
                return null
            }
            val target = randomCandidate(targets, random)
            val choices = answerChoices(candidates, target.reading, { it.reading }, random)
            if (choices.size < 2) {
                return null
            }
            return GameQuestion(
                GameMode.READING_RUSH,
                target.kanji,
                if (target.expression.isEmpty()) target.kanji else target.expression,
                "Pick the reading for ${target.kanji}",
                target.reading,
                choices,
                target.explanation(),
            )
        }

        private fun confusableQuestion(
            candidates: List<GameCandidate>,
            pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
            random: Random,
        ): GameQuestion? {
            val neighbors = neighborMap(pairs)
            val targets = ArrayList<GameCandidate>()
            for (candidate in targets(candidates) { it.hasMeaning() }) {
                val direct = neighbors[candidate.kanji]
                if (!direct.isNullOrEmpty()) {
                    targets.add(candidate)
                }
            }
            if (targets.isEmpty()) {
                return null
            }
            val target = randomCandidate(targets, random)
            val choices = LinkedHashSet<String>()
            choices.add(target.kanji)
            val direct = ArrayList(neighbors.getOrDefault(target.kanji, emptyList()))
            Collections.shuffle(direct, random)
            for (kanji in direct) {
                choices.add(kanji)
                if (choices.size >= MAX_CHOICES) {
                    break
                }
            }
            val shuffled = ArrayList(choices)
            if (shuffled.size < 2) {
                return null
            }
            Collections.shuffle(shuffled, random)
            return GameQuestion(
                GameMode.CONFUSABLE_CLASH,
                target.kanji,
                "Which kanji means ${target.meaning}?",
                "Watch the shape",
                target.kanji,
                shuffled,
                "${target.kanji} = ${target.meaning}",
            )
        }

        private fun missSweepQuestion(
            candidates: List<GameCandidate>,
            recentMissKanji: List<String?>?,
            random: Random,
        ): GameQuestion? {
            val missSet = HashSet<String>()
            recentMissKanji.orEmpty().forEach { k ->
                val cleaned = clean(k)
                if (cleaned.isNotEmpty()) missSet.add(cleaned)
            }
            val missTargets = candidates.filter { it.hasMeaning() && missSet.contains(it.kanji) }
            if (missTargets.size < 2) return null
            val target = randomCandidate(missTargets, random)
            val choices = answerChoices(candidates, target.meaning, { it.meaning }, random)
            if (choices.size < 2) return null
            return GameQuestion(
                GameMode.MISS_SWEEP,
                target.kanji,
                target.kanji,
                "Pick the meaning",
                target.meaning,
                choices,
                "${target.kanji} = ${target.meaning}",
            )
        }

        private fun candidates(
            rows: List<RecordsImportModels.DashboardRow?>?,
            inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        ): List<GameCandidate> {
            val byKanji = LinkedHashMap<String, GameCandidate>()
            if (rows != null) {
                for (row in rows) {
                    val candidate = GameCandidate.fromRow(row)
                    if (candidate.hasAnyAnswer()) {
                        byKanji[candidate.kanji] = candidate
                    }
                }
            }
            if (inventory != null) {
                for (item in inventory) {
                    val candidate = GameCandidate.fromInventory(item)
                    if (candidate.hasAnyAnswer() && !byKanji.containsKey(candidate.kanji)) {
                        byKanji[candidate.kanji] = candidate
                    }
                }
            }
            return ArrayList(byKanji.values)
        }

        private fun targets(
            candidates: List<GameCandidate>,
            filter: (GameCandidate) -> Boolean,
        ): List<GameCandidate> {
            val active = ArrayList<GameCandidate>()
            val fallback = ArrayList<GameCandidate>()
            for (candidate in candidates) {
                if (filter(candidate)) {
                    if (candidate.fromDashboard) {
                        active.add(candidate)
                    } else {
                        fallback.add(candidate)
                    }
                }
            }
            return if (active.isEmpty()) fallback else active
        }

        private fun randomCandidate(candidates: List<GameCandidate>, random: Random): GameCandidate {
            return candidates[random.nextInt(candidates.size)]
        }

        private fun answerChoices(
            candidates: List<GameCandidate>,
            correct: String?,
            extractor: (GameCandidate) -> String,
            random: Random,
        ): List<String> {
            val answers = LinkedHashSet<String>()
            val safeCorrect = clean(correct)
            answers.add(safeCorrect)
            var decoys = ArrayList<String>()
            for (candidate in candidates) {
                val answer = clean(extractor(candidate))
                if (answer.isNotEmpty() && answerKey(answer) != answerKey(safeCorrect)) {
                    decoys.add(answer)
                }
            }
            decoys = ArrayList(uniqueAnswers(decoys))
            Collections.shuffle(decoys, random)
            for (decoy in decoys) {
                answers.add(decoy)
                if (answers.size >= MAX_CHOICES) {
                    break
                }
            }
            val shuffled = ArrayList(answers)
            Collections.shuffle(shuffled, random)
            return shuffled
        }

        private fun uniqueAnswers(values: List<String>): List<String> {
            val byKey = LinkedHashMap<String, String>()
            for (value in values) {
                byKey.putIfAbsent(answerKey(value), value)
            }
            return ArrayList(byKey.values)
        }

        private fun neighborMap(pairs: List<RecordsImportModels.SimilarKanjiPair?>?): Map<String, List<String>> {
            val out = HashMap<String, MutableList<String>>()
            if (pairs == null) {
                return out
            }
            for (pair in pairs) {
                if (hasUsablePair(pair)) {
                    val a = clean(pair!!.kanjiA)
                    val b = clean(pair.kanjiB)
                    addNeighbor(out, a, b)
                    addNeighbor(out, b, a)
                }
            }
            return out
        }

        private fun hasUsablePair(pair: RecordsImportModels.SimilarKanjiPair?): Boolean {
            if (pair == null) {
                return false
            }
            val a = clean(pair.kanjiA)
            val b = clean(pair.kanjiB)
            return a.isNotEmpty() && b.isNotEmpty() && a != b
        }

        private fun addNeighbor(neighbors: MutableMap<String, MutableList<String>>, kanji: String, neighbor: String) {
            val direct = neighbors.computeIfAbsent(kanji) { ArrayList() }
            if (!direct.contains(neighbor)) {
                direct.add(neighbor)
            }
        }

        private fun cleanChoices(values: List<String?>?): List<String> {
            val out = ArrayList<String>()
            if (values == null) {
                return out
            }
            for (value in values) {
                val cleaned = clean(value)
                if (cleaned.isNotEmpty()) {
                    out.add(cleaned)
                }
            }
            return out
        }

        private fun clean(value: String?): String {
            if (value == null) {
                return ""
            }
            return value.replace("Meaning:", "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun answerKey(value: String?): String {
            return clean(value).lowercase(Locale.ROOT)
        }
    }
}

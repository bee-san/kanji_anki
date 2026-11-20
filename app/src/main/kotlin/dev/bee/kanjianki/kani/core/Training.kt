package dev.bee.kanjianki.kani.core

import kotlin.random.Random

enum class Status { TRAINING, SOLID, CLEARED }

/** What a pair or kanji shows as its pill. */
enum class Stage { TRAINING, WATCHING, SOLID }

data class KanjiState(
    val kanji: String,
    val status: Status,
    val reason: ScanAnalyzer.Reason?,
    val noteIds: Set<Long>,
    /** Distinct days with a right and fast answer since the last miss. */
    val goodDays: List<Int>,
    val lastMissDay: Int?,
    val lastSeenDay: Int?,
    val meaningMisses: Int,
    val hook: String?,
    val tagged: Boolean,
) {
    val stage: Stage
        get() = when {
            status == Status.SOLID -> Stage.SOLID
            goodDays.isNotEmpty() -> Stage.WATCHING
            else -> Stage.TRAINING
        }

    companion object {
        fun fresh(kanji: String, reason: ScanAnalyzer.Reason?, noteIds: Set<Long>) = KanjiState(
            kanji, Status.TRAINING, reason, noteIds, emptyList(), null, null, 0, null, false,
        )
    }
}

/**
 * The exit rule: a kanji is solid when it is right on [DAYS_TO_SOLID] separate days and fast
 * (successive relearning, Rawson & Dunlosky 2011; fluency as a mastery signal, Mettler et al. 2016).
 */
object Progress {
    const val DAYS_TO_SOLID = 3

    /** Time to first keystroke that still counts as recognising the kanji at a glance. */
    const val FAST_MS = 3000L

    fun answer(state: KanjiState, day: Int, correct: Boolean, latencyMs: Long): KanjiState {
        if (!correct) {
            return state.copy(
                status = Status.TRAINING,
                goodDays = emptyList(),
                lastMissDay = day,
                lastSeenDay = day,
            )
        }
        val credit = latencyMs <= FAST_MS && state.lastMissDay != day
        val days = if (credit) (state.goodDays + day).distinct().sorted() else state.goodDays
        val solid = days.size >= DAYS_TO_SOLID
        return state.copy(
            status = if (solid) Status.SOLID else state.status,
            goodDays = days,
            lastSeenDay = day,
        )
    }
}

enum class Check { MEANING, READING }

data class SessionItem(val kanji: String, val check: Check)

object SessionPlanner {
    fun sizeFor(minutes: Int): Int = when {
        minutes <= 3 -> 5
        minutes <= 5 -> 7
        else -> 12
    }

    /**
     * Today's training kanji: chains already in progress first, then the most mixed-up, then
     * the ones the memory model expects the user to have forgotten most, then the longest unseen.
     */
    fun due(states: List<KanjiState>, mixups: Map<String, Int>, day: Int, recall: Recall = Recall.NONE): List<KanjiState> =
        states.filter { it.status == Status.TRAINING && day !in it.goodDays && it.lastMissDay != day }
            .sortedWith(
                compareByDescending<KanjiState> { it.goodDays.isNotEmpty() }
                    .thenByDescending { mixups[it.kanji] ?: 0 }
                    .thenBy { recall.weakest(it.kanji) ?: 0f }
                    .thenBy { it.lastSeenDay ?: Int.MIN_VALUE },
            )

    /**
     * Solid kanji the memory model says are slipping: predicted recall of either check is below
     * [MemoryRules.TARGET_RECALL]. Weakest first. A miss on one sends it back to training.
     */
    fun maintenance(states: List<KanjiState>, day: Int, recall: Recall): List<KanjiState> =
        states.asSequence()
            .filter { it.status == Status.SOLID && it.lastSeenDay != day }
            .mapNotNull { s -> recall.weakest(s.kanji)?.takeIf { it < MemoryRules.TARGET_RECALL }?.let { s to it } }
            .sortedBy { it.second }
            .map { it.first }
            .toList()

    fun plan(
        states: List<KanjiState>,
        mixups: Map<String, Int>,
        day: Int,
        minutes: Int,
        recall: Recall = Recall.NONE,
        hasWord: (String) -> Boolean,
    ): List<SessionItem> {
        val size = sizeFor(minutes)
        val training = due(states, mixups, day, recall)
        val upkeep = maintenance(states, day, recall)
        // Keep a quarter of the session for slipping solid kanji, so they are not starved by a
        // long training list; either side fills whatever the other leaves empty.
        val reserved = minOf(upkeep.size, maxOf(1, size / 4))
        val trainingItems = training.take(size - reserved).map {
            // Alternate meaning and reading across a kanji's three days, when a word exists.
            val reading = it.goodDays.size % 2 == 1 && hasWord(it.kanji)
            SessionItem(it.kanji, if (reading) Check.READING else Check.MEANING)
        }
        val upkeepItems = upkeep.take(size - trainingItems.size).map {
            SessionItem(it.kanji, weakerCheck(it.kanji, recall, hasWord))
        }
        return trainingItems + upkeepItems
    }

    /** How many kanji a session would hold today: the Today screen, widget and reminder count. */
    fun count(states: List<KanjiState>, mixups: Map<String, Int>, day: Int, minutes: Int, recall: Recall = Recall.NONE): Int =
        plan(states, mixups, day, minutes, recall) { false }.size

    /** The check the model expects the user to miss first; reading only when there is a word for it. */
    private fun weakerCheck(kanji: String, recall: Recall, hasWord: (String) -> Boolean): Check {
        val reading = recall.of(kanji, Check.READING) ?: return Check.MEANING
        val meaning = recall.of(kanji, Check.MEANING) ?: return Check.MEANING
        return if (reading < meaning && hasWord(kanji)) Check.READING else Check.MEANING
    }
}

data class SprintTrial(val shown: String, val options: List<String>)

object SprintBuilder {
    const val ROUNDS = 6

    fun build(a: String, b: String, similar: (String) -> List<String>, random: Random = Random.Default): List<SprintTrial> {
        val pool = (listOf(a, b) + similar(a) + similar(b)).distinct()
        return List(ROUNDS) { round ->
            val shown = if (round % 2 == 0) a else b
            val others = pool.filter { it != shown }
            val pinned = listOf(if (shown == a) b else a)
            val rest = (others - pinned.toSet()).shuffled(random).take(2)
            SprintTrial(shown, (listOf(shown) + pinned + rest).distinct().shuffled(random))
        }.shuffled(random)
    }
}

/** Mix-up graph: how often [target] was read as [picked]. */
data class Mixup(val target: String, val picked: String, val count: Int, val lastDay: Int)

object Escalation {
    /** A pair that keeps coming back after the mix-up card and sprints moves to writing. */
    const val WRITE_AFTER_MIXUPS = 3

    /**
     * A mix-up the user was sure of is a confident error: the wrong shape is firmly stored, so
     * the pair moves to writing one mix-up sooner.
     */
    const val WRITE_AFTER_CONFIDENT_MIXUPS = 2

    /** Two misses on the meaning alone ask the user to build a hook. */
    const val HOOK_AFTER_MISSES = 2
}

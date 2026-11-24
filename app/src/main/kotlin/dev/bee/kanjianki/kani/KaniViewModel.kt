package dev.bee.kanjianki.kani

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bee.kanjianki.core.TypedReadingPolicy
import dev.bee.kanjianki.core.TypingAnswerMatcher
import dev.bee.kanjianki.kani.anki.AnkiSource
import dev.bee.kanjianki.kani.core.AnswerClock
import dev.bee.kanjianki.kani.core.Check
import dev.bee.kanjianki.kani.core.Escalation
import dev.bee.kanjianki.kani.core.KanjiState
import dev.bee.kanjianki.kani.core.Progress
import dev.bee.kanjianki.kani.core.Romaji
import dev.bee.kanjianki.kani.core.ScanAnalyzer
import dev.bee.kanjianki.kani.core.SessionItem
import dev.bee.kanjianki.kani.core.SessionPlanner
import dev.bee.kanjianki.kani.core.SprintBuilder
import dev.bee.kanjianki.kani.core.SprintTrial
import dev.bee.kanjianki.kani.core.Stage
import dev.bee.kanjianki.kani.core.Status
import dev.bee.kanjianki.kani.data.KaniPrefs
import dev.bee.kanjianki.kani.data.KaniStore
import dev.bee.kanjianki.kani.data.Reference
import dev.bee.kanjianki.kani.data.Scanner
import dev.bee.kanjianki.kani.memory.KaniMemory
import dev.bee.kanjianki.kani.reminder.DailyReminder
import dev.bee.kanjianki.kani.update.InstallReceiver
import dev.bee.kanjianki.kani.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** A look-alike pair as the user sees it: the kanji they meant and the one they saw instead. */
data class Pair2(val target: String, val other: String, val mixups: Int, val stage: Stage)

data class Reveal(
    val correct: Boolean,
    val answer: String,
    val word: KaniStore.Word?,
    val hook: String?,
)

sealed interface Screen {
    data object Loading : Screen
    data object Welcome : Screen
    data object Connect : Screen
    data class Cards(val detected: Scanner.Detected?, val error: String? = null) : Screen
    data class Scanning(val scanned: Int, val result: ScanSummary?) : Screen
    data object Daily : Screen
    data object Today : Screen
    data class CheckScreen(val item: SessionItem, val word: KaniStore.Word?, val reveal: Reveal?) : Screen
    data class Contrast(val target: String, val picked: String) : Screen
    data class Sprint(val trials: List<SprintTrial>) : Screen
    data class ReadingFamily(val kanji: String, val word: KaniStore.Word) : Screen
    data class Hook(val kanji: String) : Screen
    data class Write(val target: String, val other: String) : Screen
    data class Early(val newKanji: String, val known: String) : Screen
    data class Done(val summary: SessionSummary) : Screen
    data object BlindSpots : Screen
    data class Words(val from: Screen) : Screen
    data object Settings : Screen
}

data class ScanSummary(
    val wordsScanned: Int,
    val distinctKanji: Int,
    val flagged: Int,
    val reasons: Map<ScanAnalyzer.Reason, Int>,
    val lookAlikes: List<Pair<String, String>>,
)

data class SessionSummary(
    val seconds: Long,
    val solidNow: Int,
    val rows: List<KanjiState>,
    val medianMs: Long?,
    val sprintRight: Int,
    val sprintTotal: Int,
    val tagged: Int,
)

/**
 * A downloaded, verified update waiting for the user's tap, or the last install failure.
 * [installsOnLeave]: Android lets Kani install it without asking, so it installs itself once the
 * user leaves Kani.
 */
data class UpdateOffer(val version: String, val apk: File, val error: String? = null, val installsOnLeave: Boolean = false)

data class TodayData(val due: Int, val pairs: List<Pair2>, val solid: Int, val training: Int)

class KaniViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = KaniPrefs(app)
    val store = KaniStore(app)
    val ref = Reference(app)
    val anki = AnkiSource(app)
    private val scanner = Scanner(anki, ref)
    val memory = KaniMemory(app, store)

    var screen: Screen by mutableStateOf(Screen.Loading)
        private set
    var today: TodayData by mutableStateOf(TodayData(0, emptyList(), 0, 0))
        private set
    var themeMode by mutableStateOf(prefs.theme)
        private set

    private val updater = Updater(app)
    var update: UpdateOffer? by mutableStateOf(null)
        private set

    /** Kani is in the background. */
    private var away = false

    /** One automatic install per process, so a refused one is not retried on every leave. */
    private var autoInstallTried = false

    private var run: Run? = null

    private class Run(val items: List<SessionItem>) {
        var index = 0
        val startedAt = System.currentTimeMillis()
        val latencies = ArrayList<Long>()
        var sprintRight = 0
        var sprintTotal = 0
        val touched = LinkedHashSet<String>()
        val solidBefore = HashSet<String>()
        var finishing = false
    }

    /**
     * The screen the last [advance] left. Moving on shows the next kanji only after a database
     * read, so a second tap lands on the same screen and would skip a kanji unasked.
     */
    private var advancedFrom: Screen? = null

    /** On to the next kanji of the run, once per screen however often it is tapped. */
    private fun advance() {
        val from = screen
        if (from === advancedFrom) return
        advancedFrom = from
        val r = run ?: return goToday()
        r.index++
        showCurrent()
    }

    /** Set when a widget tap arrives before startup has finished loading. */
    private var sprintRequested = false

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.deleteDatabase(KaniStore.LEGACY_NAME)
                ref.warm()
            }
            launch { fetchUpdate() }
            // Brings the cached curves up to date (e.g. after an upgrade or a model change).
            launch { memory.catchUp(); refreshToday() }
            when {
                !prefs.setupDone -> screen = Screen.Welcome
                sprintRequested -> sprintBlindSpots()
                else -> goToday()
            }
        }
    }

    /** A widget tap: sprint the top look-alikes, now or as soon as startup is done. */
    fun requestSprint() {
        if (!prefs.setupDone) return
        if (screen == Screen.Loading) sprintRequested = true else sprintBlindSpots()
    }

    fun day(): Int = LocalDate.now().toEpochDay().toInt()

    // region setup

    fun startSetup() {
        screen = if (anki.state() == AnkiSource.State.READY) Screen.Cards(null).also { detect() } else Screen.Connect
    }

    fun onPermissionResult() {
        if (anki.state() == AnkiSource.State.READY) {
            screen = Screen.Cards(null)
            detect()
        }
    }

    private fun detect() {
        viewModelScope.launch {
            val detected = withContext(Dispatchers.IO) { runCatching { scanner.detect() } }
            // The user may have gone Back while AnkiDroid was being read.
            if (screen !is Screen.Cards) return@launch
            screen = detected.fold(
                onSuccess = { d ->
                    if (d.models.isEmpty()) Screen.Cards(d, "Kani couldn't find Japanese words in your AnkiDroid notes.") else Screen.Cards(d)
                },
                onFailure = { Screen.Cards(null, it.message ?: "AnkiDroid didn't answer.") },
            )
        }
    }

    fun confirmCards(detected: Scanner.Detected) {
        prefs.source = detected.toSource()
        rescan(fromSetup = true)
    }

    fun rescan(fromSetup: Boolean = false) {
        val source = prefs.source ?: return startSetup()
        screen = Screen.Scanning(0, null)
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                runCatching {
                    val scan = scanner.scan(source) { n ->
                        viewModelScope.launch(Dispatchers.Main) {
                            // A late progress tick must never replace the finished result.
                            val shown = screen as? Screen.Scanning
                            if (shown != null && shown.result == null) screen = Screen.Scanning(n, null)
                        }
                    }
                    store.applyScan(scan.result, scan.words)
                    prefs.lastScanAt = System.currentTimeMillis()
                    summarize(scan.result)
                }
            }
            // The user may have left the Scanning screen (Back, then Start): never pull them out
            // of a session for a scan they walked away from.
            val stillWatching = screen is Screen.Scanning
            summary.onSuccess { s ->
                if (stillWatching) screen = Screen.Scanning(s.wordsScanned, s)
                if (!fromSetup) refreshToday()
            }.onFailure {
                if (stillWatching) screen = Screen.Cards(null, it.message ?: "AnkiDroid didn't answer.")
            }
        }
    }

    private fun summarize(r: ScanAnalyzer.Result): ScanSummary {
        val flagged = r.flagged.map { it.kanji }.toSet()
        // One pair per kanji, so a single busy kanji like 生 doesn't fill every chip.
        val pairs = ArrayList<Pair<String, String>>()
        val used = HashSet<String>()
        for (k in flagged) {
            if (k in used) continue
            val s = ref.similar(k).firstOrNull { it in r.known && it !in used } ?: continue
            pairs += k to s
            used += k
            used += s
            if (pairs.size >= 6) break
        }
        return ScanSummary(r.wordsScanned, r.distinctKanji, r.flagged.size, r.reasonCounts, pairs.toList())
    }

    fun afterScan() {
        screen = if (prefs.setupDone) Screen.Today.also { refreshToday() } else Screen.Daily
    }

    fun finishSetup(minutes: Int, reminder: Boolean, handwriting: Boolean, tag: Boolean, startNow: Boolean) {
        prefs.minutes = minutes
        prefs.reminder = reminder
        prefs.handwriting = handwriting
        prefs.tagInAnki = tag
        prefs.setupDone = true
        DailyReminder.sync(getApplication(), prefs)
        if (startNow) startSession() else goToday()
    }

    // endregion

    // region today

    fun goToday() {
        run = null
        screen = Screen.Today
        refreshToday()
    }

    fun refreshToday() {
        viewModelScope.launch {
            today = withContext(Dispatchers.IO) {
                val states = store.allKanji()
                val mixupCounts = mixupCountByTarget()
                TodayData(
                    due = SessionPlanner.count(states, mixupCounts, day(), prefs.minutes, store.recall(System.currentTimeMillis())),
                    pairs = pairs(states).take(3),
                    solid = states.count { it.status == Status.SOLID },
                    training = states.count { it.status == Status.TRAINING },
                )
            }
        }
    }

    private fun mixupCountByTarget(): Map<String, Int> =
        store.mixups().groupBy { it.target }.mapValues { (_, v) -> v.sumOf { it.count } }

    /** Blind Spots' pairs; null while they load off the main thread. */
    var blindSpots by mutableStateOf<List<Pair2>?>(null)
        private set

    fun openBlindSpots() {
        blindSpots = null
        screen = Screen.BlindSpots
        viewModelScope.launch { blindSpots = withContext(Dispatchers.IO) { pairs() } }
    }

    /** Mix-ups from the user's own answers first, then look-alikes found in their decks. */
    fun pairs(states: List<KanjiState> = store.allKanji()): List<Pair2> {
        val byKanji = states.associateBy { it.kanji }
        val seen = HashSet<Set<String>>()
        val out = ArrayList<Pair2>()
        for (m in store.mixups()) {
            val key = setOf(m.target, m.picked)
            if (!seen.add(key)) {
                val i = out.indexOfFirst { setOf(it.target, it.other) == key }
                if (i >= 0) out[i] = out[i].copy(mixups = out[i].mixups + m.count)
                continue
            }
            out += Pair2(m.target, m.picked, m.count, byKanji[m.target]?.stage ?: Stage.TRAINING)
        }
        for (s in states) {
            if (s.status != Status.TRAINING) continue
            for (other in ref.similar(s.kanji)) {
                if (!store.isKnown(other)) continue
                if (seen.add(setOf(s.kanji, other))) out += Pair2(s.kanji, other, 0, s.stage)
                break
            }
            if (out.size >= 30) break
        }
        return out.sortedWith(compareByDescending<Pair2> { it.mixups }.thenBy { it.stage.ordinal })
    }

    /** Where the current kanji sits in the session, for the progress bar. */
    fun position(): Pair<Int, Int> = run?.let { it.index to it.items.size } ?: (0 to 1)

    fun open(target: Screen) {
        screen = target
    }

    // endregion

    // region session

    fun startSession() {
        viewModelScope.launch {
            val (items, early, solid) = withContext(Dispatchers.IO) {
                val states = store.allKanji()
                Triple(
                    SessionPlanner.plan(states, mixupCountByTarget(), day(), prefs.minutes, store.recall(System.currentTimeMillis())) {
                        store.wordsWith(it, 1).isNotEmpty()
                    },
                    nextEarly(),
                    states.filter { it.status == Status.SOLID }.map { it.kanji },
                )
            }
            if (items.isEmpty() && early == null) {
                sprintBlindSpots()
                return@launch
            }
            run = Run(items).also { it.solidBefore += solid }
            if (early != null) screen = Screen.Early(early.first, early.second) else showCurrent()
        }
    }

    /**
     * The next new kanji to introduce, with the closest look-alike the user knows. The pair is
     * picked now, not at scan time: a row stored by an older scan may name a kanji that no
     * longer counts as a look-alike, and that row is dropped.
     */
    private fun nextEarly(): Pair<String, String>? {
        while (true) {
            val kanji = store.nextEarly()?.first ?: return null
            val known = ref.similar(kanji).firstOrNull(store::isKnown)
            if (known != null) return kanji to known
            store.markEarlyShown(kanji)
        }
    }

    fun earlyDone(newKanji: String, known: String, quickCheck: Boolean) {
        store.markEarlyShown(newKanji)
        if (quickCheck) {
            drill = Drill(newKanji, known, advance = false, mayWrite = false)
            screen = Screen.Sprint(SprintBuilder.build(newKanji, known, ref::listedSimilar).take(3))
            return
        }
        if (run?.items.isNullOrEmpty()) goToday() else showCurrent()
    }

    private fun showCurrent() {
        val r = run ?: return goToday()
        val item = r.items.getOrNull(r.index) ?: return finishSession()
        viewModelScope.launch {
            val word = withContext(Dispatchers.IO) { wordFor(item) }
            screen = Screen.CheckScreen(item, word, null)
            answerClock.shown()
        }
    }

    /**
     * The word shown with a kanji. A reading check prefers a word that uses one of the kanji's
     * on'yomi, so a miss can point at the sound family.
     */
    private fun wordFor(item: SessionItem): KaniStore.Word? {
        val words = store.wordsWith(item.kanji, 8)
        if (item.check == Check.MEANING) return words.firstOrNull()
        val sounds = ref.onReadings(item.kanji).map(::toHiragana)
        return words.firstOrNull { w -> w.expression.length > 1 && sounds.any { w.reading.contains(it) } } ?: words.firstOrNull()
    }

    /** The current check's answer time; see [AnswerClock]. Lives here so rotation keeps it. */
    private val answerClock = AnswerClock { android.os.SystemClock.elapsedRealtime() }

    fun answerStarted() = answerClock.firstKey()

    fun submit(text: String, gaveUp: Boolean) {
        val current = screen as? Screen.CheckScreen ?: return
        // Done on the keyboard and a tap on Check in the same frame: answer once.
        if (current.reveal != null) return
        val r = run ?: return
        val item = current.item
        val latencyMs = answerClock.latency()
        val state = store.kanji(item.kanji) ?: KanjiState.fresh(item.kanji, null, emptySet())
        val word = current.word
        val correct = !gaveUp && when (item.check) {
            Check.MEANING -> TypingAnswerMatcher.matches(ref.lookup, item.kanji, text, word?.takeIf { it.expression == item.kanji }?.meaning)
            Check.READING -> word != null && TypedReadingPolicy.matches(Romaji.finish(text), word.reading)
        }
        val day = day()
        var next = Progress.answer(state, day, correct, latencyMs)
        r.touched += item.kanji
        if (!gaveUp) r.latencies += latencyMs
        store.logAnswer(KaniStore.Answer(day, item.kanji, item.check, correct, latencyMs, System.currentTimeMillis()))
        viewModelScope.launch { memory.catchUp() }

        if (!correct && item.check == Check.MEANING && !gaveUp) {
            val picked = lookAlikeMatching(item.kanji, text)
            if (picked != null) {
                store.save(next)
                store.recordMixup(item.kanji, picked, day)
                screen = Screen.Contrast(item.kanji, picked)
                return
            }
        }
        if (!correct && item.check == Check.READING && word != null) {
            store.save(next)
            screen = Screen.ReadingFamily(item.kanji, word)
            return
        }
        if (!correct && item.check == Check.MEANING) {
            next = next.copy(meaningMisses = state.meaningMisses + 1)
        }
        store.save(next)
        val answer = when (item.check) {
            Check.MEANING -> ref.meanings(item.kanji).take(3).joinToString(", ").ifEmpty { word?.meaning.orEmpty() }
            Check.READING -> word?.reading.orEmpty()
        }
        screen = current.copy(reveal = Reveal(correct, answer, word, state.hook))
    }

    /** If the typed meaning belongs to a look-alike, that is the kanji the user saw. */
    private fun lookAlikeMatching(kanji: String, typed: String): String? =
        ref.listedSimilar(kanji).firstOrNull { TypingAnswerMatcher.matches(ref.lookup, it, typed, null) }

    /** After the reveal: a hook prompt if the meaning keeps slipping, else the next kanji. */
    fun afterReveal() {
        val current = screen as? Screen.CheckScreen
        val r = run ?: return goToday()
        if (current != null && current.reveal?.correct == false && current.item.check == Check.MEANING) {
            val state = store.kanji(current.item.kanji)
            if (state != null && state.hook == null && state.meaningMisses >= Escalation.HOOK_AFTER_MISSES) {
                screen = Screen.Hook(current.item.kanji)
                return
            }
        }
        advance()
    }

    fun saveHook(kanji: String, hook: String?) {
        if (screen === advancedFrom) return
        if (!hook.isNullOrBlank()) store.kanji(kanji)?.let { store.save(it.copy(hook = hook.trim())) }
        advance()
    }

    /** A pending sprint (and maybe writing) and where to go after it. */
    private data class Drill(
        val target: String,
        val other: String,
        val advance: Boolean,
        val mayWrite: Boolean,
        /** The user was sure of the wrong answer, so the pair reaches writing sooner. */
        val confident: Boolean = false,
    )

    private var drill: Drill? = null

    /** [sure] is the answer to "Were you sure?": null when the user skipped the question. */
    fun contrastTrain(target: String, picked: String, sure: Boolean?) {
        drill = Drill(target, picked, advance = true, mayWrite = true, confident = sure == true)
        screen = Screen.Sprint(SprintBuilder.build(target, picked, ref::listedSimilar))
    }

    fun contrastSkip() = advance()

    fun sprintBlindSpots() {
        viewModelScope.launch {
            val trials = withContext(Dispatchers.IO) {
                val top = pairs().take(3)
                if (top.isEmpty()) return@withContext emptyList()
                val per = (SprintBuilder.ROUNDS / top.size).coerceAtLeast(2)
                top.flatMap { SprintBuilder.build(it.target, it.other, ref::listedSimilar).take(per) }.shuffled()
            }
            if (trials.isEmpty()) return@launch goToday()
            drill = null
            screen = Screen.Sprint(trials)
        }
    }

    fun sprintAnswer(trial: SprintTrial, picked: String) {
        run?.let { r ->
            r.sprintTotal++
            if (picked == trial.shown) r.sprintRight++
        }
        if (picked != trial.shown) store.recordMixup(trial.shown, picked, day())
    }

    /** End of a sprint: a pair that keeps coming back moves to writing, else carry on. */
    fun sprintDone() {
        val d = drill
        if (d != null && d.mayWrite && prefs.handwriting && ref.strokes(d.target) != null && ref.strokes(d.other) != null) {
            val count = store.mixups().filter { setOf(it.target, it.picked) == setOf(d.target, d.other) }.sumOf { it.count }
            val threshold = if (d.confident) Escalation.WRITE_AFTER_CONFIDENT_MIXUPS else Escalation.WRITE_AFTER_MIXUPS
            if (count >= threshold) {
                screen = Screen.Write(d.target, d.other)
                return
            }
        }
        finishDrill()
    }

    fun writeDone() = finishDrill()

    /** Leaving a sprint early skips the writing step. */
    fun sprintClose() = finishDrill()

    private fun finishDrill() {
        val d = drill
        drill = null
        val r = run
        if (r == null || r.items.isEmpty()) return goToday()
        if (d?.advance == true) advance() else showCurrent()
    }

    fun readingRetry(word: KaniStore.Word, typed: String): Boolean = TypedReadingPolicy.matches(Romaji.finish(typed), word.reading)

    fun readingDone() = advance()

    fun endSession() {
        if (run?.touched.isNullOrEmpty()) goToday() else finishSession()
    }

    private fun finishSession() {
        val r = run ?: return goToday()
        // Tagging in AnkiDroid can take a while, and the last Next or Close stays tappable.
        if (r.finishing) return
        r.finishing = true
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val rows = r.touched.mapNotNull { store.kanji(it) }
                val solidNow = rows.filter { it.status == Status.SOLID && it.kanji !in r.solidBefore }
                val tagged = if (prefs.tagInAnki) tagPendingSolid() else 0
                val sorted = r.latencies.sorted()
                SessionSummary(
                    seconds = (System.currentTimeMillis() - r.startedAt) / 1000,
                    solidNow = solidNow.size,
                    rows = rows.sortedByDescending { it.goodDays.size },
                    medianMs = sorted.getOrNull(sorted.size / 2),
                    sprintRight = r.sprintRight,
                    sprintTotal = r.sprintTotal,
                    tagged = tagged,
                )
            }
            run = null
            dev.bee.kanjianki.kani.widget.PairWidget.refresh(getApplication())
            screen = Screen.Done(summary)
        }
    }

    /**
     * Tags the notes of every solid kanji not tagged yet, not just this session's: a kanji whose
     * tagging failed, or that went solid before tagging was switched on, is caught up here.
     * A kanji is marked tagged only when all of its notes now carry the tag. Returns the number
     * of notes tagged.
     */
    private fun tagPendingSolid(): Int {
        val pending = store.allKanji().filter { it.status == Status.SOLID && !it.tagged }
        if (pending.isEmpty()) return 0
        val done = runCatching { anki.tagSolid(pending.flatMap { it.noteIds }.toSet()) }.getOrDefault(emptySet())
        for (k in pending) {
            if (done.containsAll(k.noteIds)) store.save(k.copy(tagged = true))
        }
        return done.size
    }

    // endregion

    // region views

    /** Rows for the Words screen; loaded off the main thread when it opens. */
    var wordRows by mutableStateOf<List<dev.bee.kanjianki.kani.ui.WordRow>>(emptyList())
        private set

    fun openWords(from: Screen) {
        wordRows = emptyList()
        screen = Screen.Words(from)
        loadWordRows()
    }

    private fun loadWordRows() {
        viewModelScope.launch { wordRows = withContext(Dispatchers.IO) { buildWordRows() } }
    }

    private fun buildWordRows(): List<dev.bee.kanjianki.kani.ui.WordRow> {
        val states = store.allKanji().associateBy { it.kanji }
        val noteIds = states.values.filter { it.status != Status.SOLID || it.reason != null }.flatMap { it.noteIds }
        return store.words(noteIds).sortedBy { it.noteId }.distinctBy { it.expression }.map { w ->
            dev.bee.kanjianki.kani.ui.WordRow(w.expression, dev.bee.kanjianki.kani.core.AnkiText.kanjiIn(w.expression).map { it to states[it]?.status })
        }
    }

    fun checkAnyway(kanji: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.kanji(kanji)?.let { store.save(it.copy(status = Status.TRAINING)) } }
            loadWordRows()
        }
    }

    data class Family(
        val sound: String?,
        val members: List<dev.bee.kanjianki.kani.ui.FamilyMember>,
        val watchFor: List<Pair<String, String>>,
    )

    /** Look-alikes that share the kanji's sound in this word: its phonetic family. */
    fun family(kanji: String, word: KaniStore.Word): Family {
        val reading = word.reading
        val sound = ref.onReadings(kanji).firstOrNull { on -> reading.contains(toHiragana(on)) }
            ?: return Family(null, emptyList(), emptyList())
        val similar = ref.listedSimilar(kanji)
        val members = listOf(kanji) + similar.filter { sound in ref.onReadings(it) }
        val watch = similar.filter { sound !in ref.onReadings(it) && ref.onReadings(it).isNotEmpty() }
            .take(2).map { it to ref.onReadings(it).first() }
        return Family(
            sound,
            members.take(4).map { k ->
                dev.bee.kanjianki.kani.ui.FamilyMember(
                    k,
                    when {
                        k == kanji -> "this card"
                        store.isKnown(k) -> "you know"
                        else -> "new"
                    },
                )
            },
            watch,
        )
    }

    private fun toHiragana(s: String): String =
        s.map { ch -> if (ch in '\u30A1'..'\u30F6') (ch - 0x60) else ch }.joinToString("")

    // endregion

    // region update

    private suspend fun fetchUpdate() {
        update = withContext(Dispatchers.IO) {
            try {
                // About 6 MB per beta, and one ships with every main push: wait for Wi-Fi.
                if (updater.onMeteredNetwork()) return@withContext null
                updater.check()?.let { UpdateOffer(it.version, updater.download(it), installsOnLeave = updater.installsSilently()) }
            } catch (error: Exception) {
                android.util.Log.w("KaniUpdate", "Update check failed.", error)
                null
            }
        }
        // The user may have left while it downloaded.
        if (away) installOnLeave()
    }

    /** Kani went to the background: the moment to install a ready update. */
    fun onLeave() {
        away = true
        answerClock.leave()
        installOnLeave()
    }

    fun onReturn() {
        away = false
        answerClock.back()
    }

    /**
     * Installs a ready update on its own when Android allows it without a tap and the user left
     * from a resting screen, not a session, drill or setup. Android closes Kani to replace it,
     * so the next open is the new version.
     */
    private fun installOnLeave() {
        val offer = update ?: return
        if (autoInstallTried || offer.error != null || !screen.resting || !updater.installsSilently()) return
        autoInstallTried = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updater.install(offer.apk, auto = true)
            } catch (error: Exception) {
                android.util.Log.w("KaniUpdate", "Automatic update failed.", error)
            }
        }
    }

    /** Screens where closing Kani loses nothing. */
    private val Screen.resting: Boolean
        get() = this is Screen.Welcome || this is Screen.Today || this is Screen.Done ||
            this is Screen.BlindSpots || this is Screen.Words || this is Screen.Settings

    /** False when Android first needs the user to let Kani install apps. */
    fun installUpdate(): Boolean {
        val offer = update ?: return true
        if (!updater.canInstall()) return false
        viewModelScope.launch {
            update = withContext(Dispatchers.IO) {
                try {
                    updater.install(offer.apk)
                    offer.copy(error = null)
                } catch (error: Exception) {
                    offer.copy(error = error.message ?: "Could not start the update.")
                }
            }
        }
        return true
    }

    fun allowInstallsIntent() = updater.allowInstallsIntent()

    /** Picks up an install Android refused while Kani was in the background. */
    fun onResume() {
        val error = InstallReceiver.takeError(getApplication()) ?: return
        update = update?.copy(error = error)
    }

    // endregion

    // region settings

    var settings by mutableStateOf(readSettings())
        private set

    private fun readSettings() = dev.bee.kanjianki.kani.ui.SettingsState(
        theme = prefs.theme,
        minutes = prefs.minutes,
        reminder = prefs.reminder,
        handwriting = prefs.handwriting,
        tag = prefs.tagInAnki,
        speak = prefs.speak,
        version = dev.bee.kanjianki.BuildConfig.VERSION_NAME,
    )

    /** One line for Settings: whether the memory model is running and how much it has seen. */
    fun memoryLine(): String = when (val s = memory.status) {
        KaniMemory.Status.Idle -> "RWKV SM92 · starting"
        is KaniMemory.Status.Ready -> "RWKV SM92 · " + if (s.answers == 1L) "1 answer" else "${s.answers} answers"
        is KaniMemory.Status.Failed -> "RWKV SM92 unavailable (${s.message})"
    }

    fun updateSettings(change: KaniPrefs.() -> Unit) {
        prefs.change()
        themeMode = prefs.theme
        settings = readSettings()
        DailyReminder.sync(getApplication(), prefs)
    }

    fun changeCards() {
        screen = Screen.Cards(null)
        if (anki.state() == AnkiSource.State.READY) detect() else screen = Screen.Connect
    }

    /** Back gesture: leave the current screen for the one above it. */
    fun back(): Boolean = when (screen) {
        Screen.Today, Screen.Welcome, Screen.Loading -> false
        Screen.Connect, is Screen.Cards -> { screen = if (prefs.setupDone) Screen.Settings else Screen.Welcome; true }
        Screen.Daily -> { screen = Screen.Welcome; true }
        is Screen.Words -> { screen = (screen as Screen.Words).from; true }
        is Screen.Sprint -> { sprintClose(); true }
        is Screen.CheckScreen, is Screen.Contrast, is Screen.ReadingFamily, is Screen.Hook, is Screen.Write -> { endSession(); true }
        is Screen.Scanning -> { if (prefs.setupDone) goToday() else startSetup(); true }
        else -> { goToday(); true }
    }

    // endregion
}

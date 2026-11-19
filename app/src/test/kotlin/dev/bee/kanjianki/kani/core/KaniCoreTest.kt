package dev.bee.kanjianki.kani.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class KaniCoreTest {

    // region AnkiText

    @Test
    fun ankiTextStripsHtmlAndFurigana() {
        assertEquals("待つ", AnkiText.expression("<b>待[ま]つ</b>"))
        assertEquals("まつ", AnkiText.reading("待[ま]つ"))
        assertEquals("まちあわせ", AnkiText.reading("待[ま]ち 合[あ]わせ"))
        assertEquals("せいてん", AnkiText.reading("せいてん"))
        assertEquals("wait", AnkiText.shortMeaning("to wait; to await"))
        assertEquals(listOf("待", "合"), AnkiText.kanjiIn("待ち合わせ"))
        assertEquals("Body build", AnkiText.meaning("(Daijirin4 Plain) Body build"))
        assertEquals("one", AnkiText.meaning("∗∗ <span class=\"gloss\">one</span>"))
        assertEquals("one two", AnkiText.plain("<b>one</b> two <span cla"))
    }

    // endregion

    // region FieldPicker

    @Test
    fun picksKikuAndCoreFields() {
        val kiku = FieldPicker.pick(listOf("Expression", "ExpressionReading", "MainDefinition", "Sentence"))!!
        assertEquals("Expression", kiku.expression)
        assertEquals("ExpressionReading", kiku.reading)
        assertEquals("MainDefinition", kiku.meaning)

        val core = FieldPicker.pick(listOf("Vocab-Kanji", "Vocab-Kana", "Vocab-Meaning"))!!
        assertEquals("Vocab-Kanji", core.expression)
        assertEquals("Vocab-Kana", core.reading)
        assertEquals("Vocab-Meaning", core.meaning)
    }

    @Test
    fun noWordFieldMeansNoPick() {
        assertNull(FieldPicker.pick(listOf("Text", "Extra")))
    }

    // endregion

    // region Romaji

    @Test
    fun romajiTurnsIntoHiragana() {
        assertEquals("せいてん", Romaji.toKana("seitenn"))
        assertEquals("がっこう", Romaji.toKana("gakkou"))
        assertEquals("こんにちは", Romaji.toKana("konnichiha"))
        assertEquals("かんじ", Romaji.toKana("kanji"))
        assertEquals("みんな", Romaji.toKana("minna"))
        assertEquals("きょう", Romaji.toKana("kyou"))
        assertEquals("しゃしん", Romaji.toKana("shashinn"))
        assertEquals("まっちゃ", Romaji.toKana("matcha"))
    }

    @Test
    fun trailingNWaitsForTheNextKey() {
        assertEquals("せいてn", Romaji.toKana("seiten"))
        assertEquals("ほんや", Romaji.toKana("hon'ya"))
        assertEquals("まつ", Romaji.toKana("まつ"))
    }

    // endregion

    // region ScanAnalyzer

    private fun word(id: Long, expr: String, problem: ScanAnalyzer.Reason? = null, healthy: Boolean = false, isNew: Boolean = false) =
        ScanAnalyzer.Word(id, expr, problem, healthy, isNew)

    @Test
    fun failingWordOnlyFlagsTheKanjiYouMiss() {
        val words = listOf(
            word(1, "待ち合わせ", ScanAnalyzer.Reason.LEECH),
            word(2, "合う", healthy = true),
            word(3, "試合", healthy = true),
            word(4, "場合", healthy = true),
        )
        val r = ScanAnalyzer.analyze(words) { emptyList() }
        assertEquals(listOf("待"), r.flagged.map { it.kanji })
        assertEquals(listOf("合"), r.cleared.map { it.kanji })
        assertEquals(1, r.reasonCounts[ScanAnalyzer.Reason.LEECH])
    }

    @Test
    fun everyFailingWordYieldsAtLeastOneKanji() {
        val words = listOf(word(1, "合", ScanAnalyzer.Reason.FAILED)) + (2L..5L).map { word(it, "合う", healthy = true) }
        val r = ScanAnalyzer.analyze(words) { emptyList() }
        assertEquals(listOf("合"), r.flagged.map { it.kanji })
    }

    @Test
    fun newKanjiThatLooksLikeAKnownOneIsShownEarly() {
        val words = listOf(word(1, "待つ", healthy = true), word(2, "持つ", isNew = true))
        val r = ScanAnalyzer.analyze(words) { if (it == "持") listOf("待") else emptyList() }
        assertEquals(mapOf("持" to "待"), r.early)
        assertTrue("待" in r.known)
        assertFalse("持" in r.known)
    }

    // endregion

    // region Progress

    @Test
    fun solidAfterThreeSeparateFastDays() {
        var s = KanjiState.fresh("待", null, emptySet())
        s = Progress.answer(s, 10, true, 1200)
        s = Progress.answer(s, 10, true, 1200)
        assertEquals(listOf(10), s.goodDays)
        s = Progress.answer(s, 11, true, 1500)
        assertEquals(Stage.WATCHING, s.stage)
        s = Progress.answer(s, 13, true, 900)
        assertEquals(Status.SOLID, s.status)
        assertEquals(Stage.SOLID, s.stage)
    }

    @Test
    fun slowAnswersDoNotCountAndMissesReset() {
        var s = KanjiState.fresh("待", null, emptySet())
        s = Progress.answer(s, 10, true, Progress.FAST_MS + 1)
        assertTrue(s.goodDays.isEmpty())
        s = Progress.answer(s, 10, true, 500)
        s = Progress.answer(s, 11, false, 500)
        assertTrue(s.goodDays.isEmpty())
        // A right answer on the day of a miss doesn't count either.
        s = Progress.answer(s, 11, true, 500)
        assertTrue(s.goodDays.isEmpty())
    }

    @Test
    fun plannerSkipsWhatIsDoneTodayAndKeepsChainsGoing() {
        val fresh = KanjiState.fresh("末", null, emptySet())
        val chain = KanjiState.fresh("待", null, emptySet()).copy(goodDays = listOf(9))
        val doneToday = KanjiState.fresh("績", null, emptySet()).copy(goodDays = listOf(10))
        val solid = KanjiState.fresh("晴", null, emptySet()).copy(status = Status.SOLID)
        val plan = SessionPlanner.plan(listOf(fresh, chain, doneToday, solid), emptyMap(), 10, 3) { it == "待" }
        assertEquals(listOf("待", "末"), plan.map { it.kanji })
        assertEquals(Check.READING, plan[0].check)
        assertEquals(Check.MEANING, plan[1].check)
    }

    // endregion

    // region Sprint

    @Test
    fun sprintAlwaysOffersThePairAndTheShownKanji() {
        val trials = SprintBuilder.build("待", "持", { listOf("侍", "特") }, Random(1))
        assertEquals(SprintBuilder.ROUNDS, trials.size)
        for (t in trials) {
            assertTrue(t.shown in t.options)
            assertTrue("待" in t.options && "持" in t.options)
            assertEquals(t.options.distinct(), t.options)
        }
        assertEquals(3, trials.count { it.shown == "待" })
    }

    // endregion

    // region Strokes

    private val strokes: StrokeData by lazy {
        File("src/main/res/raw/kanji_strokes.tsv").bufferedReader().use { StrokeData.parse(it) }
    }

    @Test
    fun decidingStrokesAreTheLeftSideOf待vs持() {
        val wait = strokes.of("待")!!
        val hold = strokes.of("持")!!
        val deciding = StrokeDiff.decidingStrokes(wait, hold)
        // 彳 is the first three strokes of 待; 寺 is shared.
        assertEquals(setOf(0, 1, 2), deciding)
    }

    @Test
    fun sharedComponentIsFoundEvenWhenItMoves() {
        // 退 and 恨 share 艮, which sits higher and smaller in 退. All of 辶 (the last three
        // strokes) decides; 退's 艮 is drawn a little differently, so at most two of its strokes do.
        val retreat = strokes.of("退")!!
        val regret = strokes.of("恨")!!
        val deciding = StrokeDiff.decidingStrokes(retreat, regret)
        assertTrue(deciding.containsAll(listOf(6, 7, 8)))
        assertTrue("most of 艮 is shared: $deciding", deciding.size <= 5)
    }

    @Test
    fun nearIdenticalPairsStillGetADecidingStroke() {
        val end = strokes.of("末")!!
        val notYet = strokes.of("未")!!
        assertTrue(StrokeDiff.decidingStrokes(end, notYet).isNotEmpty())
    }

    @Test
    fun handwritingCheckAcceptsTheRightStrokesAndRejectsOthers() {
        val wait = strokes.of("待")!!
        val expected = listOf(wait[0], wait[1], wait[2])
        val wobbly = expected.map { s -> s.map { Pt(it.x + 0.02f, it.y - 0.015f) } }
        assertTrue(StrokeDiff.matchesDeciding(wobbly, expected))
        assertFalse(StrokeDiff.matchesDeciding(wobbly.take(2), expected))
        val elsewhere = expected.map { s -> s.map { Pt(it.x + 0.5f, it.y) } }
        assertFalse(StrokeDiff.matchesDeciding(elsewhere, expected))
    }

    // endregion
}

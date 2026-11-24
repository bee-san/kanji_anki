package dev.bee.kanjianki.kani.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * `res/raw/look_alike_scores.tsv` holds [StrokeDiff.likeness] for every bundled pair. Scoring them
 * all takes a few minutes, so the file is generated and these tests check it has not drifted.
 *
 * Scores count whole parts from `src/test/resources/kanji_parts.tsv` (see [StrokeDiff.likeness]),
 * generated with the stroke data by `tools/generate_kanjivg_strokes.py --parts-output`.
 *
 * Regenerate it after changing the pair list, the stroke or part data, or [StrokeDiff]:
 * `./gradlew :app:testDebugUnitTest --tests '*LookAlikesTest' -PupdateLookAlikeScores`
 */
class LookAlikesTest {

    @Test
    fun closestComesFirstAndLoosePairsAreLeftOut() {
        val scores = mapOf(LookAlikes.key("退", "眠") to 0.2f, LookAlikes.key("退", "限") to 0.67f, LookAlikes.key("恨", "退") to 0.44f)
        val lookAlikes = LookAlikes({ if (it == "退") listOf("恨", "眠", "限", "郎") else emptyList() }, scores)
        assertEquals(listOf("限", "恨"), lookAlikes.of("退"))
        // An unscored pair (郎) is listed last and never called a look-alike.
        assertEquals(listOf("限", "恨", "眠", "郎"), lookAlikes.listed("退"))
        assertEquals(0.2f, lookAlikes.likeness("眠", "退"), 0f)
    }

    @Test
    fun scanIntroducesANewKanjiThroughItsClosestKnownLookAlike() {
        val words = listOf(
            ScanAnalyzer.Word(1, "眠る", null, healthy = true, isNew = false),
            ScanAnalyzer.Word(2, "限る", null, healthy = true, isNew = false),
            ScanAnalyzer.Word(3, "退く", null, healthy = false, isNew = true),
        )
        assertEquals(mapOf("退" to "限"), ScanAnalyzer.analyze(words, bundled::of).early)
        // Knowing only 眠 is no reason to bring 退 up: they share no part.
        assertEquals(emptyMap<String, String>(), ScanAnalyzer.analyze(words.filter { it.noteId != 2L }, bundled::of).early)
    }

    @Test
    fun bundledScoresKeepRealLookAlikesAndDropLooseOnes() {
        assertEquals(setOf("限", "良", "根", "郎", "恨", "眼"), bundled.of("退").toSet())
        assertEquals("眠", bundled.listed("退").last())
        assertTrue("持" in bundled.of("待"))
        assertTrue("未" in bundled.of("末"))
        assertFalse("詞" in bundled.of("可"))
    }

    @Test
    fun sharingOnlyASideRadicalOrScrapsOfAPartIsNotALookAlike() {
        // 亻 plus a few strokes of 昔 against 共 or 咅: different parts, not look-alikes.
        assertFalse("供" in bundled.of("借"))
        assertFalse("倍" in bundled.of("借"))
        // Only the radical in common.
        assertFalse("如" in bundled.of("和"))
        assertFalse("姉" in bundled.of("妹"))
        // The same distinctive part with a different radical, or a near copy, still counts.
        assertTrue("惜" in bundled.of("借"))
        assertTrue("販" in bundled.of("仮"))
        assertTrue("輸" in bundled.of("輪"))
        assertTrue("閉" in bundled.of("問"))
        assertTrue("干" in bundled.of("千"))
    }

    @Test
    fun partsComeFromKanjiVg() {
        assertEquals(10, parts.getValue("借").strokes)
        assertEquals(listOf(0, 1), parts.getValue("借").strokesOf(0))
        assertTrue(parts.getValue("借").isSideRadical(0))
        // 艮 is 良's radical but most of it, so it is no side radical.
        assertFalse(parts.getValue("良").isSideRadical(1))
        // 囗 in 国 is split around 玉 but is one part.
        assertEquals(listOf(0, 1, 7), parts.getValue("国").strokesOf(0))
    }

    @Test
    fun everyBundledPairHasAScore() {
        val scored = scoresFile.readLines().filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("kanji_a") }
        assertEquals(pairs.map { (a, b) -> LookAlikes.key(a, b) }, scored.map { it.substringBeforeLast('\t') })
        assertEquals(pairs.size, scores.size)
    }

    @Test
    fun sampledScoresMatchTheStrokeDiff() {
        val sample = pairs.filterIndexed { i, _ -> i % 101 == 0 } + listOf("眠" to "退", "待" to "持", "借" to "供", "借" to "惜")
        for ((a, b) in sample) {
            assertEquals("$a/$b", likeness(a, b), bundled.likeness(a, b), 0.0006f)
        }
    }

    private val bundled: LookAlikes by lazy {
        val byKanji = HashMap<String, MutableList<String>>()
        for ((a, b) in pairs) {
            byKanji.getOrPut(a) { ArrayList() } += b
            byKanji.getOrPut(b) { ArrayList() } += a
        }
        LookAlikes({ byKanji[it].orEmpty() }, scores)
    }

    private val scores: Map<String, Float> by lazy { scoresFile.bufferedReader().use { LookAlikes.parseScores(it) } }

    companion object {
        private val raw = File("src/main/res/raw")
        private val scoresFile = File(raw, "look_alike_scores.tsv")

        private val strokes: StrokeData by lazy { File(raw, "kanji_strokes.tsv").bufferedReader().use { StrokeData.parse(it) } }

        private val parts: Map<String, KanjiParts> by lazy {
            File("src/test/resources/kanji_parts.tsv").bufferedReader().use { KanjiParts.parse(it) }
        }

        /** The bundled pairs in file order. */
        private val pairs: List<Pair<String, String>> by lazy {
            File(raw, "similar_kanji.tsv").readLines()
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("kanji_a") }
                .map { line -> line.split('\t').let { it[0] to it[1] } }
        }

        private fun likeness(a: String, b: String): Float {
            val sa = strokes.of(a) ?: return 0f
            val sb = strokes.of(b) ?: return 0f
            return StrokeDiff.likeness(sa, sb, parts[a], parts[b])
        }

        @BeforeClass
        @JvmStatic
        fun rewriteScoresWhenAsked() {
            if (System.getProperty("kani.updateLookAlikeScores") == null) return
            // One thread on purpose: the coverage agent's probes make parallel scoring far slower.
            val rows = pairs.map { (a, b) -> LookAlikes.key(a, b) + "\t" + String.format(Locale.ROOT, "%.3f", likeness(a, b)) }
            scoresFile.writeText(
                "# How alike each pair in similar_kanji.tsv looks, 0..1: StrokeDiff.likeness over kanji_strokes.tsv\n" +
                    "# and the KanjiVG parts in app/src/test/resources/kanji_parts.tsv.\n" +
                    "# Derived from Kiku's pair list and KanjiVG stroke data (CC BY-SA 3.0); see kanjivg_attribution.txt.\n" +
                    "# Generated by LookAlikesTest; do not edit. Regenerate with:\n" +
                    "# ./gradlew :app:testDebugUnitTest --tests '*LookAlikesTest' -PupdateLookAlikeScores\n" +
                    "kanji_a\tkanji_b\tlikeness\n" +
                    rows.joinToString("\n") + "\n",
            )
        }
    }
}

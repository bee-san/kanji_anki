package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KanjiReadingAlignerTest {
    // Fixture dictionary with the exact KANJIDIC-style readings for each kanji
    // used in the Goal 76 fixture table.
    private val dictionary: DictionaryLookup = DictionaryLookup.fromKanjiEntries(
        listOf(
            kanji("勉", on = listOf("ベン"), kun = listOf("つと.める")),
            kanji("強", on = listOf("キョウ", "ゴウ"), kun = listOf("つよ.い", "し.いる")),
            kanji("時", on = listOf("ジ"), kun = listOf("とき")),
            kanji("間", on = listOf("カン", "ケン"), kun = listOf("あいだ", "ま")),
            kanji("好", on = listOf("コウ"), kun = listOf("この.む", "す.く", "よ.い", "い.い")),
            kanji("学", on = listOf("ガク"), kun = listOf("まな.ぶ")),
            kanji("脱", on = listOf("ダツ"), kun = listOf("ぬ.ぐ", "ぬ.げる")),
            kanji("出", on = listOf("シュツ", "スイ"), kun = listOf("で.る", "だ.す")),
            kanji("心", on = listOf("シン"), kun = listOf("こころ")),
            kanji("配", on = listOf("ハイ"), kun = listOf("くば.る")),
            kanji("引", on = listOf("イン"), kun = listOf("ひ.く", "ひ.ける")),
            kanji("張", on = listOf("チョウ"), kun = listOf("は.る", "は.り")),
            kanji("人", on = listOf("ジン", "ニン"), kun = listOf("ひと")),
            kanji("毎", on = listOf("マイ"), kun = listOf("ごと")),
            kanji("日", on = listOf("ニチ", "ジツ"), kun = listOf("ひ", "び", "か")),
            kanji("見", on = listOf("ケン"), kun = listOf("み.る", "み.える", "み.せる")),
            kanji("中", on = listOf("チュウ"), kun = listOf("なか", "うち")),
            kanji("知", on = listOf("チ"), kun = listOf("し.る", "し.らせる")),
            kanji("合", on = listOf("ゴウ", "ガッ", "カッ"), kun = listOf("あ.う", "あ.わせる")),
            kanji("低", on = listOf("テイ"), kun = listOf("ひく.い", "ひく.める")),
            // Jukujikun fixtures — readings that do NOT decompose to the surface kana.
            kanji("今", on = listOf("コン", "キン"), kun = listOf("いま")),
            kanji("明", on = listOf("メイ", "ミョウ"), kun = listOf("あ.かり", "あか.るい")),
            kanji("大", on = listOf("ダイ", "タイ"), kun = listOf("おお", "おお.きい")),
            kanji("本", on = listOf("ホン"), kun = listOf("もと")),
        ),
    )

    @Test
    fun benkyou() = assertAlign("勉強", "べんきょう", listOf("勉" to "べん", "強" to "きょう"))

    @Test
    fun jikan() = assertAlign("時間", "じかん", listOf("時" to "じ", "間" to "かん"))

    @Test
    fun suki() = assertAlign("好き", "すき", listOf("好" to "す"))

    @Test
    fun manabu() = assertAlign("学ぶ", "まなぶ", listOf("学" to "まな"))

    @Test
    fun dasshutsuSokuonCanonicalized() =
        assertAlign("脱出", "だっしゅつ", listOf("脱" to "だつ", "出" to "しゅつ"))

    @Test
    fun shinpaiRendakuHtoPCanonicalized() =
        assertAlign("心配", "しんぱい", listOf("心" to "しん", "配" to "はい"))

    @Test
    fun hipparuCanonicalized() =
        assertAlign("引っ張る", "ひっぱる", listOf("引" to "ひ", "張" to "は"))

    @Test
    fun hitobitoIterationMarkPlusRendaku() =
        assertAlign("人々", "ひとびと", listOf("人" to "ひと", "人" to "ひと"))

    @Test
    fun mainichi() = assertAlign("毎日", "まいにち", listOf("毎" to "まい", "日" to "にち"))

    @Test
    fun miru() = assertAlign("見る", "みる", listOf("見" to "み"))

    @Test
    fun hikuiKunStem() = assertAlign("低い", "ひくい", listOf("低" to "ひく"))

    @Test
    fun katakanaReadingInputMatchesHiragana() =
        assertAlign("時間", "ジカン", listOf("時" to "じ", "間" to "かん"))

    @Test
    fun furiganaBracketForm() =
        assertAlignFurigana(
            "勉強中",
            "勉強中[べんきょうちゅう]",
            listOf("勉" to "べん", "強" to "きょう", "中" to "ちゅう"),
        )

    @Test
    fun multiSegmentFurigana() =
        assertAlignFurigana(
            "知り合い",
            "知[し]り 合[あ]い",
            listOf("知" to "し", "合" to "あ"),
        )

    @Test
    fun furiganaWithoutBracketDelegatesToPlain() =
        assertAlignFurigana("勉強", "べんきょう", listOf("勉" to "べん", "強" to "きょう"))

    @Test
    fun kyouJukujikunIsNull() = assertNull(align("今日", "きょう"))

    @Test
    fun ashitaJukujikunIsNull() = assertNull(align("明日", "あした"))

    @Test
    fun otonaJukujikunIsNull() = assertNull(align("大人", "おとな"))

    @Test
    fun nihonLexicalizedTruncationIsNull() = assertNull(align("日本", "にほん"))

    @Test
    fun deterministicAcrossRepeatedRuns() {
        // Longest-first tie-break must yield identical output every run.
        val first = align("脱出", "だっしゅつ")
        for (i in 0 until 20) {
            assertEquals(first, align("脱出", "だっしゅつ"))
        }
        // An ambiguous case: 好き could in principle match す or (with a longer
        // variant) fail; the aligner picks the deterministic longest-first parse.
        val ambiguous = align("好き", "すき")
        for (i in 0 until 20) {
            assertEquals(ambiguous, align("好き", "すき"))
        }
    }

    @Test
    fun emptyInputsReturnNull() {
        assertNull(align("", "すき"))
        assertNull(align("好き", ""))
    }

    // ---- helpers ----

    private fun align(expr: String, kana: String): List<KanjiReadingAligner.ReadingPair>? =
        KanjiReadingAligner.alignPlain(expr, kana, dictionary)

    private fun assertAlign(expr: String, kana: String, expected: List<Pair<String, String>>) {
        val result = align(expr, kana)
        assertNotNull("expected $expr/$kana to align", result)
        assertEquals(expected.toPairsString(), result!!.toResultString())
    }

    private fun assertAlignFurigana(expr: String, furigana: String, expected: List<Pair<String, String>>) {
        val result = KanjiReadingAligner.alignFurigana(expr, furigana, dictionary)
        assertNotNull("expected $expr/$furigana to align", result)
        assertEquals(expected.toPairsString(), result!!.toResultString())
    }

    private fun List<Pair<String, String>>.toPairsString(): String =
        joinToString(",") { "${it.first}=${it.second}" }

    private fun List<KanjiReadingAligner.ReadingPair>.toResultString(): String =
        joinToString(",") { "${it.kanji}=${it.canonicalReading}" }

    private fun kanji(
        literal: String,
        on: List<String>,
        kun: List<String>,
    ): DictionaryLookup.KanjiEntry = DictionaryLookup.KanjiEntry(
        DictionaryLookup.KanjiEntryFields(
            literal,
            listOf("meaning"),
            on,
            kun,
            emptyList(),
            12,
            3,
            61,
            1000,
            null,
        ),
    )
}

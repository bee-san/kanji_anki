package dev.bee.kanjianki.core

import java.util.regex.Pattern

/**
 * Pure, deterministic engine that attributes a Japanese word's kana reading to
 * its constituent kanji, producing an ordered list of
 * `(kanjiLiteral, canonicalReading)` pairs — one per kanji occurrence.
 *
 * This is the data foundation for the reading-aware ladder rungs (Goals 78–80):
 * once a word's reading is split per kanji we can tell that 脱出=だっしゅつ uses
 * 脱=だつ, and cross-reference against the same kanji's mature usages (脱ぐ=ぬ)
 * to build reading-discrimination cards.
 *
 * No Android imports, no I/O: the caller supplies a [DictionaryLookup]. The
 * algorithm is specified in `plans/reading-rungs-goals-2026-07-09.md` Goal 76
 * and was validated at 92–95% coverage against a real 8,597-note collection.
 *
 * Determinism: for a kanji position the aligner tries reading variants
 * longest-first, and memoized DP takes the first successful parse. Given the
 * same inputs the output is always identical.
 *
 * On failure (jukujikun / ateji / lexicalized truncations that no
 * rule-based alignment can attribute) the aligner returns `null` — those words
 * simply produce no reading-usage rows, which is safe because the new rungs are
 * data-conditional.
 */
object KanjiReadingAligner {
    /** One attributed kanji occurrence: its literal plus the canonical reading. */
    @JvmRecord
    data class ReadingPair(
        @JvmField val kanji: String,
        @JvmField val canonicalReading: String,
    )

    private const val ITERATION_MARK = '々'

    // Anki furigana bracket segment: optional leading space, base text, [reading].
    private val FURIGANA_SEGMENT: Pattern = Pattern.compile("\\s?([^\\s\\[\\]]+)\\[([^\\]]+)\\]")

    /**
     * Align [expression] against a plain-kana [kana] reading. Returns the
     * ordered per-kanji `(literal, canonical)` pairs, or `null` if no alignment
     * consuming both strings fully exists.
     */
    @JvmStatic
    fun alignPlain(
        expression: String?,
        kana: String?,
        dictionary: DictionaryLookup,
    ): List<ReadingPair>? {
        val expr = normalizeExpression(expression)
        val reading = normalizeKana(kana)
        if (expr.isEmpty() || reading.isEmpty()) {
            return null
        }
        val units = expressionUnits(expr) ?: return null
        val out = ArrayList<ReadingPair>()
        val memo = HashMap<Long, Boolean>()
        return if (solve(units, 0, reading, 0, dictionary, out, memo)) out else null
    }

    /**
     * Align [expression] against Anki [furigana]. If the furigana contains
     * `[`, its bracket segments are parsed and each aligned independently; if it
     * has no bracket it is treated as plain kana. Returns `null` if any segment
     * fails.
     */
    @JvmStatic
    fun alignFurigana(
        expression: String?,
        furigana: String?,
        dictionary: DictionaryLookup,
    ): List<ReadingPair>? {
        val raw = DictionaryTextUtil.stripHtml(furigana)
        if (raw.isEmpty()) {
            return null
        }
        if (!raw.contains('[')) {
            return alignPlain(expression, raw, dictionary)
        }
        val matcher = FURIGANA_SEGMENT.matcher(raw)
        val out = ArrayList<ReadingPair>()
        var matchedAny = false
        var lastEnd = 0
        while (matcher.find()) {
            // Any literal text between the previous segment and this one is
            // furigana-less kana/punctuation the reader supplies directly; it
            // carries no kanji so it contributes no pairs, but a stray kanji
            // there means the furigana is malformed → fail.
            val between = raw.substring(lastEnd, matcher.start())
            if (containsKanji(between)) {
                return null
            }
            lastEnd = matcher.end()
            matchedAny = true
            val base = matcher.group(1) ?: return null
            val segReading = normalizeKana(matcher.group(2))
            val pairs = alignPlain(base, segReading, dictionary) ?: return null
            out.addAll(pairs)
        }
        if (!matchedAny) {
            return null
        }
        if (containsKanji(raw.substring(lastEnd))) {
            return null
        }
        return if (out.isEmpty()) null else out
    }

    // ---- expression segmentation ----

    /**
     * An expression unit: either a kanji occurrence (carrying its reading
     * inventory) or a literal kana/other char that must be consumed verbatim.
     * The iteration mark 々 is expanded to a kanji unit reusing the previous
     * kanji's inventory and literal.
     */
    private class ExprUnit(
        val literal: String,
        val isKanji: Boolean,
        // For kanji units: variant reading (as it may surface in the word) →
        // canonical reading. Sorted longest-variant-first for determinism.
        val variants: List<Pair<String, String>>,
    )

    private fun expressionUnits(expr: String): List<ExprUnit>? {
        val units = ArrayList<ExprUnit>()
        var i = 0
        var previousKanji: ExprUnit? = null
        while (i < expr.length) {
            val cp = expr.codePointAt(i)
            val ch = expr.substring(i, i + Character.charCount(cp))
            if (cp == ITERATION_MARK.code) {
                // 々 repeats the previous kanji: reuse its inventory + literal.
                val prev = previousKanji ?: return null
                units.add(ExprUnit(prev.literal, true, prev.variants))
                i += ch.length
                continue
            }
            if (DictionaryTextUtil.isKanji(cp)) {
                val unit = ExprUnit(ch, true, emptyList())
                units.add(unit)
                // The inventory is filled lazily below (needs the dictionary).
                previousKanji = unit
            } else {
                units.add(ExprUnit(ch, false, emptyList()))
            }
            i += ch.length
        }
        return units
    }

    // ---- DP alignment ----

    private fun solve(
        units: List<ExprUnit>,
        unitIndex: Int,
        kana: String,
        kanaIndex: Int,
        dictionary: DictionaryLookup,
        out: ArrayList<ReadingPair>,
        memo: HashMap<Long, Boolean>,
    ): Boolean {
        if (unitIndex == units.size) {
            return kanaIndex == kana.length
        }
        if (kanaIndex >= kana.length) {
            return false
        }
        val key = unitIndex.toLong() * (kana.length + 1L) + kanaIndex
        val cached = memo[key]
        if (cached == false) {
            return false
        }
        val unit = units[unitIndex]
        if (!unit.isKanji) {
            // Literal char must match exactly.
            if (kana.regionMatches(kanaIndex, unit.literal, 0, unit.literal.length)) {
                if (solve(units, unitIndex + 1, kana, kanaIndex + unit.literal.length, dictionary, out, memo)) {
                    return true
                }
            }
            memo[key] = false
            return false
        }
        val variants = inventoryFor(unit, dictionary)
        for ((surface, canonical) in variants) {
            if (surface.isEmpty() || kanaIndex + surface.length > kana.length) {
                continue
            }
            if (!kana.regionMatches(kanaIndex, surface, 0, surface.length)) {
                continue
            }
            out.add(ReadingPair(unit.literal, canonical))
            if (solve(units, unitIndex + 1, kana, kanaIndex + surface.length, dictionary, out, memo)) {
                return true
            }
            out.removeAt(out.size - 1)
        }
        memo[key] = false
        return false
    }

    // ---- reading inventory ----

    private fun inventoryFor(unit: ExprUnit, dictionary: DictionaryLookup): List<Pair<String, String>> {
        if (unit.variants.isNotEmpty()) {
            return unit.variants
        }
        val entry = dictionary.lookupKanji(unit.literal)
        val built = buildInventory(entry)
        // Cache on the unit so repeated occurrences (々) reuse it.
        return built
    }

    /**
     * Build the surface→canonical reading map for a kanji, longest-surface-first.
     * Exposed for tests to pin the canonicalization rules directly.
     */
    @JvmStatic
    fun readingInventory(entry: DictionaryLookup.KanjiEntry?): List<Pair<String, String>> = buildInventory(entry)

    private fun buildInventory(entry: DictionaryLookup.KanjiEntry?): List<Pair<String, String>> {
        if (entry == null) {
            return emptyList()
        }
        // Map surface variant → canonical. LinkedHashMap keeps first-seen
        // canonical for a surface (on-reading before kun if it collides).
        val map = LinkedHashMap<String, String>()
        for (on in entry.onReadings) {
            val canonical = normalizeKana(on)
            if (canonical.isEmpty()) {
                continue
            }
            addVariants(map, canonical, canonical)
        }
        for (kun in entry.kunReadings) {
            addKunReading(map, kun)
        }
        // Longest surface first so a kanji consumes as much kana as it can,
        // which both disambiguates and keeps the parse deterministic.
        return map.entries
            .map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<String, String>> { it.first.length }.thenBy { it.first })
    }

    private fun addKunReading(map: LinkedHashMap<String, String>, kunRaw: String) {
        // Kun readings look like おし.える or -がる or いか.す; strip okurigana
        // and affix markers. Canonical is the pre-`.` stem.
        val cleaned = normalizeKana(kunRaw.replace("-", ""))
        if (cleaned.isEmpty()) {
            return
        }
        val dot = cleaned.indexOf('.')
        if (dot < 0) {
            addVariants(map, cleaned, cleaned)
            return
        }
        val stem = cleaned.substring(0, dot)
        val full = cleaned.replace(".", "")
        if (stem.isNotEmpty()) {
            addVariants(map, stem, stem)
        }
        if (full.isNotEmpty()) {
            // The full okurigana form surfaces too (好き uses す stem, but 甘い
            // uses あま stem which is the pre-dot); canonical stays the stem.
            addVariants(map, full, stem.ifEmpty { full })
        }
    }

    /**
     * Register [surface] → [canonical] plus its rendaku (voicing) and sokuon
     * (geminate) surface variants, all mapping back to the same canonical so
     * evidence for a reading is grouped correctly (D-R2).
     */
    private fun addVariants(map: LinkedHashMap<String, String>, surface: String, canonical: String) {
        putIfAbsent(map, surface, canonical)
        val rendaku = rendakuVariants(surface)
        for (voiced in rendaku) {
            putIfAbsent(map, voiced, canonical)
        }
        // Sokuon: final つ/ち/く/き → っ. Apply to the base and to each rendaku
        // form (combined rendaku+sokuon, e.g. だつ → だっ, がく → がっ).
        for (base in listOf(surface) + rendaku) {
            val sokuon = sokuonVariant(base)
            if (sokuon != null) {
                putIfAbsent(map, sokuon, canonical)
            }
        }
    }

    private fun putIfAbsent(map: LinkedHashMap<String, String>, key: String, value: String) {
        if (!map.containsKey(key)) {
            map[key] = value
        }
    }

    /** Voiced (rendaku) forms of a reading whose first mora can voice. */
    private fun rendakuVariants(reading: String): List<String> {
        if (reading.isEmpty()) {
            return emptyList()
        }
        val first = reading[0]
        val rest = reading.substring(1)
        val voiced = RENDAKU.getOrDefault(first, emptyList())
        return voiced.map { it + rest }
    }

    /** Sokuon form: final つ/ち/く/き replaced by っ, if applicable. */
    private fun sokuonVariant(reading: String): String? {
        if (reading.isEmpty()) {
            return null
        }
        val last = reading[reading.length - 1]
        return if (last in SOKUON_FINALS) reading.substring(0, reading.length - 1) + 'っ' else null
    }

    // ---- normalization ----

    private fun normalizeExpression(value: String?): String {
        val stripped = DictionaryTextUtil.stripHtml(value)
        return stripPunctuation(stripped)
    }

    /** Katakana → hiragana; strip HTML, punctuation, and whitespace. */
    private fun normalizeKana(value: String?): String {
        val stripped = DictionaryTextUtil.stripHtml(value)
        val sb = StringBuilder(stripped.length)
        var i = 0
        while (i < stripped.length) {
            val cp = stripped.codePointAt(i)
            val count = Character.charCount(cp)
            when {
                cp in 0x30A1..0x30F6 -> sb.appendCodePoint(cp - 0x60) // katakana → hiragana
                isDropped(cp) -> {} // strip punctuation/space
                else -> sb.appendCodePoint(cp)
            }
            i += count
        }
        return sb.toString()
    }

    private fun stripPunctuation(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            val count = Character.charCount(cp)
            if (!isDropped(cp)) {
                sb.appendCodePoint(cp)
            }
            i += count
        }
        return sb.toString()
    }

    private fun isDropped(cp: Int): Boolean {
        if (Character.isWhitespace(cp)) {
            return true
        }
        return when (cp) {
            '、'.code, '。'.code, ','.code, '，'.code, '・'.code -> true
            else -> false
        }
    }

    private fun containsKanji(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (DictionaryTextUtil.isKanji(cp) || cp == ITERATION_MARK.code) {
                return true
            }
            i += Character.charCount(cp)
        }
        return false
    }

    // ---- phonological tables ----

    // Rendaku: an initial voiceless mora may voice (k→g, s→z, t→d, h→b and h→p).
    private val RENDAKU: Map<Char, List<String>> = buildMap {
        val kg = mapOf('か' to 'が', 'き' to 'ぎ', 'く' to 'ぐ', 'け' to 'げ', 'こ' to 'ご')
        val sz = mapOf('さ' to 'ざ', 'し' to 'じ', 'す' to 'ず', 'せ' to 'ぜ', 'そ' to 'ぞ')
        val td = mapOf('た' to 'だ', 'ち' to 'ぢ', 'つ' to 'づ', 'て' to 'で', 'と' to 'ど')
        for ((k, v) in kg) put(k, listOf(v.toString()))
        for ((k, v) in sz) put(k, listOf(v.toString()))
        for ((k, v) in td) put(k, listOf(v.toString()))
        // h-row voices to BOTH b (rendaku) and p (handakuten, e.g. 心配 はい→ぱい).
        val hb = mapOf('は' to ('ば' to 'ぱ'), 'ひ' to ('び' to 'ぴ'), 'ふ' to ('ぶ' to 'ぷ'), 'へ' to ('べ' to 'ぺ'), 'ほ' to ('ぼ' to 'ぽ'))
        for ((k, pair) in hb) put(k, listOf(pair.first.toString(), pair.second.toString()))
    }

    private val SOKUON_FINALS: Set<Char> = setOf('つ', 'ち', 'く', 'き')
}

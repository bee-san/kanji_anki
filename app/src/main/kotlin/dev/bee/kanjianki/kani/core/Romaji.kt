package dev.bee.kanjianki.kani.core

/**
 * Live romaji → hiragana for the reading answer box.
 *
 * A trailing `n` stays as `n` until the next key decides between ん and な/に/…,
 * so typing `konnichiha` produces こんにちは and `kanji` produces かんじ.
 */
object Romaji {
    private val TABLE: Map<String, String> = buildMap {
        val rows = mapOf(
            "" to listOf("あ", "い", "う", "え", "お"),
            "k" to listOf("か", "き", "く", "け", "こ"),
            "g" to listOf("が", "ぎ", "ぐ", "げ", "ご"),
            "s" to listOf("さ", "し", "す", "せ", "そ"),
            "z" to listOf("ざ", "じ", "ず", "ぜ", "ぞ"),
            "t" to listOf("た", "ち", "つ", "て", "と"),
            "d" to listOf("だ", "ぢ", "づ", "で", "ど"),
            "n" to listOf("な", "に", "ぬ", "ね", "の"),
            "h" to listOf("は", "ひ", "ふ", "へ", "ほ"),
            "b" to listOf("ば", "び", "ぶ", "べ", "ぼ"),
            "p" to listOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ"),
            "m" to listOf("ま", "み", "む", "め", "も"),
            "r" to listOf("ら", "り", "る", "れ", "ろ"),
            "y" to listOf("や", "", "ゆ", "いぇ", "よ"),
            "w" to listOf("わ", "うぃ", "う", "うぇ", "を"),
            "f" to listOf("ふぁ", "ふぃ", "ふ", "ふぇ", "ふぉ"),
            "v" to listOf("ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ"),
            "l" to listOf("ぁ", "ぃ", "ぅ", "ぇ", "ぉ"),
            "x" to listOf("ぁ", "ぃ", "ぅ", "ぇ", "ぉ"),
        )
        val vowels = "aiueo"
        for ((c, kana) in rows) {
            for (i in vowels.indices) if (kana[i].isNotEmpty()) put(c + vowels[i], kana[i])
        }
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("fu", "ふ"); put("ji", "じ")
        put("wo", "を"); put("nn", "ん"); put("n'", "ん")
        put("ltsu", "っ"); put("xtsu", "っ"); put("ltu", "っ"); put("xtu", "っ")
        put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ"); put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
        put("-", "ー")
        val yoon = mapOf(
            "ky" to "き", "gy" to "ぎ", "ny" to "に", "hy" to "ひ", "by" to "び", "py" to "ぴ",
            "my" to "み", "ry" to "り", "jy" to "じ", "zy" to "じ", "dy" to "ぢ",
        )
        for ((c, base) in yoon) {
            put(c + "a", base + "ゃ"); put(c + "u", base + "ゅ"); put(c + "o", base + "ょ")
        }
        for ((c, base) in mapOf("sh" to "し", "ch" to "ち", "j" to "じ", "cy" to "ち")) {
            put(c + "a", base + "ゃ"); put(c + "u", base + "ゅ"); put(c + "o", base + "ょ"); put(c + "e", base + "ぇ")
        }
        put("ti", "ち")
        put("tu", "つ")
        put("si", "し")
        put("zi", "じ")
        put("hu", "ふ")
    }

    private const val VOWELS = "aiueo"

    fun toKana(input: String): String {
        val s = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (!c.isLetter() && c != '-' && c != '\'') {
                out.append(c)
                i++
                continue
            }
            // Double consonant → っ (but "nn" is ん).
            val doubled = i + 1 < s.length && c == s[i + 1] && c !in VOWELS && c != 'n' && c.isLetter()
            if (doubled || s.startsWith("tch", i)) {
                out.append('っ')
                i++
                continue
            }
            if (c == 'n') {
                val next = s.getOrNull(i + 1)
                when {
                    next == null -> { out.append('n'); i++; continue }
                    next == '\'' -> { out.append('ん'); i += 2; continue }
                    next == 'n' -> {
                        val after = s.getOrNull(i + 2)
                        // "nn" followed by a vowel or y is ん + な行 (e.g. "konnichiha").
                        if (after != null && (after in VOWELS || after == 'y')) {
                            out.append('ん'); i++; continue
                        }
                        out.append('ん'); i += 2; continue
                    }
                    next !in VOWELS && next != 'y' -> { out.append('ん'); i++; continue }
                }
            }
            var matched = false
            for (len in 4 downTo 1) {
                if (i + len > s.length) continue
                val kana = TABLE[s.substring(i, i + len)] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (!matched) {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /**
     * The submitted answer: [toKana], with a trailing `n` settled as ん, since no next key is
     * coming to decide it (`hon` → ほん).
     */
    fun finish(input: String): String {
        val kana = toKana(input.trimEnd())
        return if (kana.endsWith('n')) kana.dropLast(1) + "ん" else kana
    }
}

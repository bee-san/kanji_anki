package dev.bee.kanjianki.core

import java.util.Locale

object RepairedHandoffPolicy {
    const val ANKI_BROWSER_SEARCH: String = "tag:kani_repaired is:suspended"

    data class Card(
        val kanji: List<String>,
        val title: String,
        val body: String,
        val primaryLabel: String,
        val dismissLabel: String,
        val search: String = ANKI_BROWSER_SEARCH,
    )

    @JvmStatic
    fun card(kanji: List<String>?): Card? {
        val normalized = kanji.orEmpty()
            .map(TextUtil::normalizeSingleKanji)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
        if (normalized.isEmpty()) return null
        val count = normalized.size
        val list = normalized.joinToString(" ・ ")
        return Card(
            kanji = normalized,
            title = localizedText(
                "$count kanji repaired",
                "${count}件の漢字を修復しました",
            ),
            body = localizedText(
                "$list\nCopy the AnkiDroid search, paste it in the card browser, select all, then unsuspend.",
                "$list\nAnkiDroidのカードブラウザに検索を貼り付け、すべて選択して停止を解除します。",
            ),
            primaryLabel = localizedText("Copy AnkiDroid search", "AnkiDroid検索をコピー"),
            dismissLabel = localizedText("Dismiss", "閉じる"),
        )
    }

    @JvmStatic
    fun copiedToast(): String = localizedText(
        "Copied. Paste in AnkiDroid's card browser, select all, unsuspend.",
        "コピーしました。AnkiDroidのカードブラウザに貼り付け、すべて選択して停止を解除します。",
    )

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == "ja") japanese else english
}

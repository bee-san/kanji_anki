package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepairedHandoffPolicyTest {
    @Test
    fun cardRendersKanjiListAndExactAnkiSearch() {
        withLocale(Locale.ENGLISH) {
            val card = requireNotNull(RepairedHandoffPolicy.card(listOf("微", "徴", "微", "かな")))
            assertEquals(listOf("微", "徴"), card.kanji)
            assertEquals("2 kanji repaired", card.title)
            assertEquals("微 ・ 徴\nCopy the AnkiDroid search, paste it in the card browser, select all, then unsuspend.", card.body)
            assertEquals("tag:kani_repaired is:suspended", card.search)
            assertEquals("Copy AnkiDroid search", card.primaryLabel)
            assertEquals("Dismiss", card.dismissLabel)
            assertEquals(
                "Copied. Paste in AnkiDroid's card browser, select all, unsuspend.",
                RepairedHandoffPolicy.copiedToast(),
            )
        }
    }

    @Test
    fun emptyInputIsHiddenAndJapaneseCopyIsLocalized() {
        assertNull(RepairedHandoffPolicy.card(null))
        withLocale(Locale.JAPANESE) {
            val card = requireNotNull(RepairedHandoffPolicy.card(listOf("徴")))
            assertEquals("1件の漢字を修復しました", card.title)
            assertEquals("AnkiDroid検索をコピー", card.primaryLabel)
            assertEquals("閉じる", card.dismissLabel)
            assertEquals(
                "コピーしました。AnkiDroidのカードブラウザに貼り付け、すべて選択して停止を解除します。",
                RepairedHandoffPolicy.copiedToast(),
            )
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}

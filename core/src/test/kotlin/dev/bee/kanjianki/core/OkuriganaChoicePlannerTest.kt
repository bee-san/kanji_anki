package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkuriganaChoicePlannerTest {
    @Test
    fun buildsTwoDistinctOkuriganaForms() {
        val card = OkuriganaChoicePlanner.build(
            "教", "教える", "おしえる",
            listOf("おし.える", "おそ.わる")
        )
        assertNotNull(card)
        assertEquals("教＿＿", card!!.prompt)
        assertEquals("教える", card.correctAnswer)
        assertTrue(card.choices.contains("教える"))
        assertTrue(card.choices.contains("教わる"))
        assertEquals(2, card.choices.size)
    }

    @Test
    fun nullWhenOnlyOneOkuriganaForm() {
        val card = OkuriganaChoicePlanner.build(
            "食", "食べる", "たべる",
            listOf("た.べる")
        )
        assertNull(card)
    }

    @Test
    fun nullWhenNoDotsInReadings() {
        val card = OkuriganaChoicePlanner.build(
            "教", "教える", "おしえる",
            listOf("おしえる", "おそわる")
        )
        assertNull(card)
    }

    @Test
    fun nullWhenBlankKanji() {
        assertNull(OkuriganaChoicePlanner.build("", "教える", "おしえる", listOf("おし.える", "おそ.わる")))
    }

    @Test
    fun nullWhenBlankWord() {
        assertNull(OkuriganaChoicePlanner.build("教", "", "おしえる", listOf("おし.える", "おそ.わる")))
    }

    @Test
    fun nullWhenNullInputs() {
        assertNull(OkuriganaChoicePlanner.build(null, null, null, null))
    }

    @Test
    fun respectsMaxChoiceCount() {
        val card = OkuriganaChoicePlanner.build(
            "生", "生きる", "いきる",
            listOf("い.きる", "い.かす", "い.ける", "う.まれる", "は.える")
        )
        assertNotNull(card)
        assertTrue(card!!.choices.size <= OkuriganaChoicePlanner.MAX_CHOICE_COUNT)
    }

    @Test
    fun nonInflectedWordWithOnlyOneDotFormReturnsNull() {
        val card = OkuriganaChoicePlanner.build(
            "山", "山", "やま",
            listOf("やま")
        )
        assertNull(card)
    }

    @Test
    fun onReadingUsageWordDoesNotMatchOkurigana() {
        val card = OkuriganaChoicePlanner.build(
            "教", "教育", "きょういく",
            listOf("おし.える", "おそ.わる")
        )
        assertNull(card)
    }

    @Test
    fun determinism() {
        val r1 = OkuriganaChoicePlanner.build("教", "教える", "おしえる", listOf("おし.える", "おそ.わる"))
        val r2 = OkuriganaChoicePlanner.build("教", "教える", "おしえる", listOf("おし.える", "おそ.わる"))
        assertEquals(r1!!.choices, r2!!.choices)
        assertEquals(r1.correctAnswer, r2.correctAnswer)
    }
}

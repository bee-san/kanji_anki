package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedReadingPolicyTest {
    @Test
    fun normalizesWidthKatakanaPunctuationAndWhitespace() {
        assertEquals("だっしゅつ", TypedReadingPolicy.normalize("　ﾀﾞｯｼｭﾂ。 "))
        assertTrue(TypedReadingPolicy.matches("ダッシュツ", "だっしゅつ"))
    }

    @Test
    fun extractsSingleAndSegmentedBracketFurigana() {
        assertEquals("おとな", TypedReadingPolicy.normalize("大人[おとな]"))
        assertEquals("たべる", TypedReadingPolicy.normalize("食[た]べる"))
        assertEquals("おかあさん", TypedReadingPolicy.normalize("お母[かあ]さん"))
        assertEquals("とりあつかう", TypedReadingPolicy.normalize("取[と]り扱[あつか]う"))
        assertEquals("だっしゅつ", TypedReadingPolicy.normalize("脱出【だっしゅつ】"))
    }

    @Test
    fun preservesSmallKanaSokuonDakutenAndLongSoundMarkExactly() {
        assertFalse(TypedReadingPolicy.matches("きゃく", "きやく"))
        assertFalse(TypedReadingPolicy.matches("がっこう", "かつこう"))
        assertTrue(TypedReadingPolicy.matches("コーヒー", "こーひー"))
        assertFalse(TypedReadingPolicy.matches("こひー", "こーひー"))
    }

    @Test
    fun rejectsEmptyRomajiAndFuzzyAnswers() {
        assertFalse(TypedReadingPolicy.matches(null, "おとな"))
        assertFalse(TypedReadingPolicy.matches("  。", ""))
        assertFalse(TypedReadingPolicy.matches("otona", "おとな"))
        assertFalse(TypedReadingPolicy.matches("おう", "おお"))
    }
}

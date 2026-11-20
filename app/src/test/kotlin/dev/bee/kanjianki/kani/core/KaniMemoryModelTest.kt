package dev.bee.kanjianki.kani.core

import dev.bee.rwkv.RwkvAlgorithmInfo
import dev.bee.rwkv.RwkvEngine
import dev.bee.rwkv.RwkvModel
import dev.bee.rwkv.RwkvWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Kani's answer mapping run through the real SM92 weights. */
class KaniMemoryModelTest {
    companion object {
        private lateinit var engine: RwkvEngine
        private const val DAY = 86_400_000L

        @JvmStatic
        @BeforeClass
        fun loadWeights() {
            val path = requireNotNull(System.getProperty("kani.rwkv.weights")) { "kani.rwkv.weights is not set" }
            engine = RwkvEngine(RwkvModel.load(RwkvWeights.parse(File(path).readBytes(), RwkvAlgorithmInfo.WEIGHTS_SHA256)))
        }
    }

    private var nextId = 1L

    private fun answer(kanji: String, day: Int, correct: Boolean, check: Check = Check.MEANING, latency: Long = 1500) =
        LoggedAnswer(nextId++, kanji, check, MemoryRules.rating(correct, latency), latency, day * DAY + 12 * 3_600_000L, day)

    @Test
    fun curvesDecayAndRightAnswersHoldLongerThanMisses() {
        val model = MemoryModel(engine)
        // Three right days for 待; 持 is missed on the same days.
        for (day in listOf(100, 101, 103)) {
            model.feed(answer("待", day, correct = true))
            model.feed(answer("持", day, correct = false))
        }
        val right = model.history("待", Check.MEANING)!!
        val missed = model.history("持", Check.MEANING)!!
        val soon = 104 * DAY
        val later = 130 * DAY
        val p = right.recallAt(soon)
        assertTrue("recall is a probability: $p", p in 0f..1f)
        assertTrue("recall decays over time", right.recallAt(later) < right.recallAt(soon))
        assertTrue("right answers hold longer than misses", right.recallAt(later) > missed.recallAt(later))
        assertNull("an unanswered check has no curve", model.history("待", Check.READING))
        assertEquals(0, model.skipped)
    }

    @Test
    fun replayIsDeterministic() {
        val log = listOf(answer("末", 10, true), answer("未", 10, false), answer("末", 12, true, Check.READING), answer("未", 13, true))
        val a = MemoryModel(engine).apply { log.forEach(::feed) }
        val b = MemoryModel(engine).apply { log.forEach(::feed) }
        for (k in listOf("末", "未")) {
            assertTrue(a.history(k, Check.MEANING)!!.contentEquals(b.history(k, Check.MEANING)!!))
        }
        assertEquals(log.last().id, a.fedThrough)
    }

    @Test(expected = IllegalArgumentException::class)
    fun answersMustArriveInLogOrder() {
        val model = MemoryModel(engine)
        val first = answer("末", 10, true)
        val second = answer("末", 11, true)
        model.feed(second)
        model.feed(first)
    }

    @Test
    fun cardsAndNotesHaveStableDistinctIds() {
        assertEquals(MemoryRules.cardId("末", Check.MEANING), MemoryRules.cardId("末", Check.MEANING))
        assertNotEquals(MemoryRules.cardId("末", Check.MEANING), MemoryRules.cardId("末", Check.READING))
        assertNotEquals(MemoryRules.cardId("末", Check.MEANING), MemoryRules.cardId("未", Check.MEANING))
        assertEquals(MemoryRules.ids("末", Check.MEANING).note, MemoryRules.ids("末", Check.READING).note)
        assertTrue(MemoryRules.cardId("末", Check.MEANING) >= 0)
    }
}

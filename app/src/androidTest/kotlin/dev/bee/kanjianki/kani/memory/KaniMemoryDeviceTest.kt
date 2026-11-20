package dev.bee.kanjianki.kani.memory

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.kani.core.Check
import dev.bee.kanjianki.kani.core.MemoryRules
import dev.bee.kanjianki.kani.data.KaniStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The RWKV memory model on a device: the weights come from the installed APK's assets and the
 * curves go through the real `kani1.db`. Wipes that database, so run it on a test device.
 */
@RunWith(AndroidJUnit4::class)
class KaniMemoryDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: KaniStore

    @Before
    fun open() {
        context.deleteDatabase(KaniStore.NAME)
        store = KaniStore(context)
    }

    @After
    fun close() {
        store.close()
        context.deleteDatabase(KaniStore.NAME)
    }

    private fun log(kanji: String, day: Int, correct: Boolean, check: Check = Check.MEANING, latency: Long = 1500) =
        store.logAnswer(KaniStore.Answer(day, kanji, check, correct, latency, day * DAY + 12 * HOUR))

    @Test
    fun weightsLoadFromTheApkAndAnswersMoveTheCurve() = runBlocking {
        val memory = KaniMemory(context, store)
        log("待", 20_000, true)
        log("待", 20_001, true)
        log("待", 20_003, true)
        memory.catchUp()
        assertEquals(KaniMemory.Status.Ready(3), memory.status)
        assertEquals(MemoryRules.IDENTITY, store.memoryMeta().identity)

        val soon = store.recall(20_004 * DAY).of("待", Check.MEANING)
        val later = store.recall(20_040 * DAY).of("待", Check.MEANING)
        assertNotNull(soon)
        assertNotNull(later)
        assertTrue("recall is a probability: $soon", soon!! in 0f..1f)
        assertTrue("recall decays: $soon → $later", later!! < soon)

        log("待", 20_004, false)
        memory.catchUp()
        val afterMiss = store.recall(20_005 * DAY).of("待", Check.MEANING)!!
        val beforeMiss = soon
        Log.i(TAG, "待 recall: day+1 $soon, day+37 $later, after a miss $afterMiss")
        assertTrue("a miss lowers recall ($beforeMiss → $afterMiss)", afterMiss < beforeMiss)
    }

    /** A year of daily sessions: 12 answers a day over 60 kanji. */
    @Test
    fun aYearOfAnswersReplaysAndLiveAnswersStayFast() = runBlocking {
        val kanji = "一二三四五六七八九十百千万円口目耳手足力上下左右中大小月日年早草花虫犬人名女男子学校先生字文本休玉金雨赤青白天田町村森林竹糸貝石音".map { it.toString() }
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            var i = 0
            for (day in 20_000 until 20_365) {
                repeat(12) {
                    val k = kanji[i % kanji.size]
                    log(k, day, correct = i % 5 != 0, check = if (i % 3 == 0) Check.READING else Check.MEANING, latency = 800L + (i % 7) * 600)
                    i++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val total = store.answerCount()

        val memory = KaniMemory(context, store)
        val replayStart = SystemClock.elapsedRealtime()
        memory.catchUp()
        val replayMs = SystemClock.elapsedRealtime() - replayStart
        assertEquals(KaniMemory.Status.Ready(total.toLong()), memory.status)

        log("一", 20_365, true)
        val liveStart = SystemClock.elapsedRealtime()
        memory.catchUp()
        val liveMs = SystemClock.elapsedRealtime() - liveStart

        val recall = store.recall(20_366 * DAY)
        val seen = kanji.count { recall.of(it, Check.MEANING) != null }
        Log.i(TAG, "replayed $total answers in $replayMs ms (${"%.2f".format(replayMs.toDouble() / total)} ms each); live answer $liveMs ms; $seen/${kanji.size} kanji have curves")
        assertEquals(kanji.size, seen)
        assertTrue("a live answer took $liveMs ms", liveMs < 2_000)
    }

    private companion object {
        const val TAG = "KaniMemoryDeviceTest"
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}

package dev.bee.kanjianki.memory

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.RwkvStreamKeys
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.RwkvStateStore
import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvCollectionState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/**
 * On-device cost of the RWKV memory model: a full rebuild over a synthetic
 * 50,000-review history, live answers, and publishing the state to SQLite (in
 * a scratch database, never the app's). Results are logged under the
 * `KaniRwkvBenchmark` tag and reported as instrumentation status.
 */
@RunWith(AndroidJUnit4::class)
class RwkvDeviceBenchmarkInstrumentedTest {
    @Test
    fun rebuildAndAnswerCostOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val loadStarted = SystemClock.elapsedRealtime()
        val engine = RwkvRuntime.engine(context)
        val loadMillis = SystemClock.elapsedRealtime() - loadStarted

        val history = syntheticHistory()
        val state = RwkvCollectionState()
        val replayStarted = SystemClock.elapsedRealtime()
        for ((ids, answer) in history) {
            engine.replay(state, ids, answer)
        }
        val replayMillis = SystemClock.elapsedRealtime() - replayStarted

        val answerTimes = LongArray(ANSWER_SAMPLES)
        val lastDay = history.last().second
        for (index in 0 until ANSWER_SAMPLES) {
            val ids = RwkvStreamKeys.idsFor("k${index % CARDS}", CoreSkill.RECOGNITION, "")
            val at = lastDay.reviewedAtMillis + DAY + index * 20_000L
            val started = SystemClock.elapsedRealtimeNanos()
            engine.answer(state, ids, RwkvAnswer(3, 5_000L, at, lastDay.dayOffset + 1, 2), 0.9f, 36_500)
            answerTimes[index] = SystemClock.elapsedRealtimeNanos() - started
        }
        answerTimes.sort()

        val dbFile = File(context.cacheDir, "rwkv-benchmark.db").also { it.delete() }
        val publishMillis: Long
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(LocalStoreBase.REVIEW_LOG_TABLE_SQL)
            RwkvStateStore.createTables(db)
            RwkvStateStore.initializeFresh(db)
            val started = SystemClock.elapsedRealtime()
            db.beginTransaction()
            try {
                check(RwkvStateStore().replaceCollection(db, state, 0L))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            publishMillis = SystemClock.elapsedRealtime() - started
        }
        val dbBytes = dbFile.length()
        dbFile.delete()

        val p50 = answerTimes[ANSWER_SAMPLES / 2] / 1e6
        val p95 = answerTimes[ANSWER_SAMPLES * 95 / 100] / 1e6
        val replayPerReview = replayMillis.toDouble() * 1000.0 / history.size
        val report = "weights load ${loadMillis}ms; rebuild ${history.size} reviews over ${state.histories.size} cards " +
            "in ${replayMillis}ms (%.0f µs/review); answer p50 %.1fms p95 %.1fms; publish ${publishMillis}ms; state db ${dbBytes / 1024}KB"
                .format(replayPerReview, p50, p95)
        Log.i(TAG, report)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$TAG: $report\n") })

        assertTrue("live answer p95 ${p95}ms", p95 < ANSWER_BUDGET_MILLIS)
    }

    private fun syntheticHistory(): List<Pair<dev.bee.rwkv.RwkvStreamIds, RwkvAnswer>> {
        val random = Random(11)
        val out = ArrayList<Pair<dev.bee.rwkv.RwkvStreamIds, RwkvAnswer>>(REVIEWS)
        val due = LongArray(CARDS) { Long.MAX_VALUE }
        val interval = IntArray(CARDS) { 1 }
        var day = 0
        var introduced = 0
        while (out.size < REVIEWS) {
            val dayStart = START + day * DAY
            repeat(8) {
                if (introduced < CARDS) {
                    due[introduced] = dayStart
                    introduced++
                }
            }
            var slot = 0
            for (card in 0 until introduced) {
                if (due[card] > dayStart || out.size >= REVIEWS) {
                    continue
                }
                val rating = if (random.nextDouble() < 0.12) 1 else 3
                val core = if (card % 2 == 0) CoreSkill.RECOGNITION else CoreSkill.CONTEXTUAL_READING
                val ids = RwkvStreamKeys.idsFor("k${card / 2}", core, "")
                out += ids to RwkvAnswer(rating, 2_000L + random.nextInt(9_000), dayStart + slot * 15_000L, START_DAY + day, 2)
                interval[card] = if (rating == 1) 1 else minOf(interval[card] * 2 + 1, 400)
                due[card] = dayStart + interval[card] * DAY
                slot++
            }
            day++
        }
        return out
    }

    private companion object {
        const val TAG = "KaniRwkvBenchmark"
        const val REVIEWS = 50_000
        const val CARDS = 6_000
        const val ANSWER_SAMPLES = 200
        const val ANSWER_BUDGET_MILLIS = 50.0
        const val DAY = 24L * 60L * 60L * 1000L
        const val START = 1_700_000_000_000L
        const val START_DAY = 19_675L
    }
}

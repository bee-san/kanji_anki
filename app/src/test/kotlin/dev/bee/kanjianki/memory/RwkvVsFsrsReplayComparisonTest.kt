package dev.bee.kanjianki.memory

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RwkvHistoryBackfill
import dev.bee.rwkv.RwkvCollectionState
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

/**
 * Informational, not a gate: how well RWKV (SM92) and the FSRS-6 model Kani
 * shipped before predicted recall on a real Kani review history.
 *
 * Run with an exported database (Settings › Automation › Backup & restore,
 * gunzipped):
 * `./gradlew :app:testDebugUnitTest --tests '*RwkvVsFsrsReplayComparisonTest' -PrwkvCompareDb=/path/to/kanji_anki_simple.db`
 *
 * For every fed answer after a card's first, RWKV's prediction is its curve
 * from the card's previous answer read at the answer time (replaying the
 * history in order), and FSRS-6's is the default-weights forgetting curve at
 * the stability FSRS had stored just before the answer (`memory_before`).
 * Only answers both models have a prediction for are scored; the label is
 * "not Again". Personalized FSRS weights, if any were in use, are not
 * reproduced (the default decay is used).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RwkvVsFsrsReplayComparisonTest {
    @Test
    fun compareOnExportedHistory() {
        val path: String = System.getProperty("kani.rwkvCompareDb").orEmpty()
        assumeTrue("set -PrwkvCompareDb to run the comparison", path.isNotBlank() && File(path).isFile)
        val engine = RwkvRuntime.engine(ApplicationProvider.getApplicationContext())
        val state = RwkvCollectionState()
        val backfill = RwkvHistoryBackfill()
        val all = Scores()
        val crossDay = Scores()
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT kanji, answer_signature, task_type, rating, reviewed_at, review_day_start, core_skill, " +
                    "scheduler_state_before_json, memory_before FROM review_log ORDER BY id",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = RwkvHistoryBackfill.Row(
                        kanji = cursor.getString(0).orEmpty(),
                        answerSignature = cursor.getString(1).orEmpty(),
                        taskType = cursor.getString(2).orEmpty(),
                        rating = cursor.getString(3).orEmpty(),
                        reviewedAtMillis = cursor.getLong(4),
                        reviewDayStartMillis = cursor.getLong(5),
                        coreSkill = cursor.getString(6).orEmpty(),
                        schedulerStateBeforeJson = cursor.getString(7).orEmpty(),
                        memoryBefore = cursor.getString(8).orEmpty(),
                        activeElapsedMillis = null,
                    )
                    val fed = backfill.classify(row) ?: continue
                    if (fed.warmFirst.isNotEmpty()) {
                        engine.warmCard(state, fed.ids, fed.warmFirst)
                    }
                    val history = state.histories[fed.ids.card]
                    val memory = RecordsStudyModels.TaskMemory.decode(row.memoryBefore, null)
                    val fsrsElapsedDays = if (memory.lastReviewedAtMillis > 0L) {
                        (row.reviewedAtMillis - memory.lastReviewedAtMillis).coerceAtLeast(0L) / DAY.toDouble()
                    } else {
                        null
                    }
                    if (history != null && fsrsElapsedDays != null && memory.stability > 0.0 && row.memoryBefore.isNotEmpty()) {
                        val label = if (fed.answer.rating > 1) 1.0 else 0.0
                        val rwkv = history.recallAt(row.reviewedAtMillis).toDouble()
                        val fsrs = fsrsRecall(fsrsElapsedDays, memory.stability)
                        all.add(label, rwkv, fsrs)
                        if (fsrsElapsedDays >= 1.0) {
                            crossDay.add(label, rwkv, fsrs)
                        }
                    }
                    engine.replay(state, fed.ids, fed.answer)
                }
            }
        }
        println("RWKV vs FSRS-6 on ${File(path).name}")
        println("  all repeat answers:   ${all.report()}")
        println("  cross-day answers:    ${crossDay.report()}")
    }

    private fun fsrsRecall(elapsedDays: Double, stability: Double): Double {
        val factor = 0.9.pow(-1.0 / FSRS_DECAY) - 1.0
        return (1.0 + factor * elapsedDays / stability).pow(-FSRS_DECAY)
    }

    private class Scores {
        private var n = 0
        private var passes = 0
        private var rwkvLogLoss = 0.0
        private var fsrsLogLoss = 0.0
        private var rwkvBrier = 0.0
        private var fsrsBrier = 0.0
        private var rwkvMean = 0.0
        private var fsrsMean = 0.0

        fun add(label: Double, rwkv: Double, fsrs: Double) {
            n++
            passes += label.toInt()
            rwkvLogLoss += logLoss(label, rwkv)
            fsrsLogLoss += logLoss(label, fsrs)
            rwkvBrier += (label - rwkv) * (label - rwkv)
            fsrsBrier += (label - fsrs) * (label - fsrs)
            rwkvMean += rwkv
            fsrsMean += fsrs
        }

        fun report(): String =
            if (n == 0) {
                "no comparable answers"
            } else {
                "n=$n pass-rate=%.3f | log-loss RWKV %.4f vs FSRS-6 %.4f | Brier RWKV %.4f vs FSRS-6 %.4f | mean prediction RWKV %.3f vs FSRS-6 %.3f"
                    .format(passes.toDouble() / n, rwkvLogLoss / n, fsrsLogLoss / n, rwkvBrier / n, fsrsBrier / n, rwkvMean / n, fsrsMean / n)
            }

        private fun logLoss(label: Double, p: Double): Double {
            val clamped = p.coerceIn(1e-5, 1.0 - 1e-5)
            return -(label * ln(clamped) + (1.0 - label) * ln(1.0 - clamped))
        }
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L

        /** FSRS-6 default w20 (Kani's shipped default decay). */
        const val FSRS_DECAY = 0.1542
    }
}

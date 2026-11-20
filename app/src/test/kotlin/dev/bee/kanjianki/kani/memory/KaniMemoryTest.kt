package dev.bee.kanjianki.kani.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.kani.core.Check
import dev.bee.kanjianki.kani.core.MemoryRules
import dev.bee.kanjianki.kani.data.KaniStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The memory model end to end: APK asset, answer log, cached curves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniMemoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: KaniStore

    @Before
    fun open() {
        context.deleteDatabase(KaniStore.NAME)
        store = KaniStore(context)
    }

    @After
    fun close() = store.close()

    private fun log(kanji: String, day: Int, correct: Boolean, check: Check = Check.MEANING) =
        store.logAnswer(KaniStore.Answer(day, kanji, check, correct, 1500, day * DAY + 12 * HOUR))

    @Test
    fun answersAreFedAndCurvesCached() = runBlocking {
        val memory = KaniMemory(context, store)
        log("待", 20_000, true)
        log("待", 20_001, true)
        log("持", 20_001, false, Check.READING)
        memory.catchUp()

        assertEquals(KaniMemory.Status.Ready(3), memory.status)
        val meta = store.memoryMeta()
        assertEquals(MemoryRules.IDENTITY, meta.identity)
        assertEquals(store.lastAnswerId(), meta.fedThrough)
        val recall = store.recall((20_002 * DAY))
        assertNotNull(recall.of("待", Check.MEANING))
        assertNotNull(recall.of("持", Check.READING))
        assertNull(recall.of("持", Check.MEANING))

        // A new answer is fed incrementally and moves the curve.
        val before = recall.of("待", Check.MEANING)!!
        log("待", 20_002, false)
        memory.catchUp()
        assertEquals(KaniMemory.Status.Ready(4), memory.status)
        val after = store.recall(20_003 * DAY).of("待", Check.MEANING)!!
        assertTrue("a miss lowers recall ($before → $after)", after < before)
    }

    @Test
    fun aNewProcessReplaysTheLogToTheSameCurves() = runBlocking {
        log("末", 20_000, true)
        log("未", 20_000, false)
        log("末", 20_003, true, Check.READING)
        KaniMemory(context, store).catchUp()
        val first = store.recall(20_010 * DAY)

        // Wipe the cache's identity, as a model change would: the next process replays everything.
        store.saveMemory(emptyMap(), KaniStore.MemoryMeta("old-model", 0), replaceAll = true)
        assertNull(store.recall(20_010 * DAY).of("末", Check.MEANING))
        val memory = KaniMemory(context, store)
        memory.catchUp()
        val replayed = store.recall(20_010 * DAY)
        for ((k, c) in listOf("末" to Check.MEANING, "未" to Check.MEANING, "末" to Check.READING)) {
            assertEquals(first.of(k, c)!!, replayed.of(k, c)!!, 0f)
        }
    }

    @Test
    fun anUpToDateCacheNeedsNoModel() = runBlocking {
        log("末", 20_000, true)
        KaniMemory(context, store).catchUp()
        val memory = KaniMemory(context, store)
        memory.catchUp()
        assertEquals(KaniMemory.Status.Ready(1), memory.status)
    }

    @Test
    fun version1AnswerLogsUpgradeAndAreFed() = runBlocking {
        store.close()
        context.deleteDatabase(KaniStore.NAME)
        // A Kani 1.0 database: the answer log has no check, time or rating, and no memory tables.
        val path = context.getDatabasePath(KaniStore.NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE kanji (kanji TEXT PRIMARY KEY, status TEXT NOT NULL, reason TEXT, note_ids TEXT NOT NULL DEFAULT '', good_days TEXT NOT NULL DEFAULT '', last_miss_day INTEGER, last_seen_day INTEGER, meaning_misses INTEGER NOT NULL DEFAULT 0, hook TEXT, tagged INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE answer (id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER NOT NULL, kanji TEXT NOT NULL, correct INTEGER NOT NULL, latency_ms INTEGER NOT NULL)")
            db.execSQL("INSERT INTO answer (day, kanji, correct, latency_ms) VALUES (20000, '待', 1, 1200), (20001, '待', 0, 4000)")
            db.version = 1
        }
        store = KaniStore(context)
        val answers = store.answersAfter(0)
        assertEquals(listOf(3, 1), answers.map { it.rating })
        assertEquals(Check.MEANING, answers.first().check)
        assertEquals(20_000 * DAY + DAY / 2, answers.first().atMillis)

        KaniMemory(context, store).catchUp()
        assertNotNull(store.recall(20_002 * DAY).of("待", Check.MEANING))
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}

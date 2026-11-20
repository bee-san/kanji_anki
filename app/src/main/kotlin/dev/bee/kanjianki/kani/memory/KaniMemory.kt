package dev.bee.kanjianki.kani.memory

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.bee.kanjianki.kani.core.Check
import dev.bee.kanjianki.kani.core.MemoryModel
import dev.bee.kanjianki.kani.core.MemoryRules
import dev.bee.kanjianki.kani.data.KaniStore
import dev.bee.rwkv.RwkvAlgorithmInfo
import dev.bee.rwkv.RwkvCardHistory
import dev.bee.rwkv.RwkvEngine
import dev.bee.rwkv.RwkvModel
import dev.bee.rwkv.RwkvWeights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs the RWKV memory model over Kani's answer log and keeps the cached curves in
 * [KaniStore] current.
 *
 * The answer log is the source of truth. The model state lives in memory for this process:
 * the first update after launch replays the whole log, later ones feed only new answers.
 * Readers (the planner, Today, the reminder) only read the cached curves, so they never wait
 * for the model and fall back to the plain rules if it is unavailable.
 */
class KaniMemory(context: Context, private val store: KaniStore) {
    private val appContext = context.applicationContext
    private val lock = Mutex()
    private var model: MemoryModel? = null

    sealed interface Status {
        data object Idle : Status
        data class Ready(val answers: Long) : Status
        data class Failed(val message: String) : Status
    }

    @Volatile
    var status: Status = Status.Idle
        private set

    /** Feeds any answers the cached curves do not include yet. Safe to call after every answer. */
    suspend fun catchUp() {
        lock.withLock {
            withContext(Dispatchers.Default) {
                runCatching { update() }
                    .onSuccess { status = Status.Ready(it) }
                    .onFailure {
                        Log.e(TAG, "RWKV memory update failed", it)
                        status = Status.Failed(it.message ?: it.javaClass.simpleName)
                    }
            }
        }
    }

    /** Returns how many answers the cached curves include. */
    private fun update(): Long {
        val latest = store.lastAnswerId()
        val meta = store.memoryMeta()
        val current = model
        // The cache is already up to date and nothing new needs feeding: no need to load the model.
        if (current == null && meta.identity == MemoryRules.IDENTITY && meta.fedThrough == latest) return store.answerCount().toLong()
        val fresh = current == null
        val loadStart = SystemClock.elapsedRealtime()
        val m = current ?: MemoryModel(engine()).also { model = it }
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val touched = LinkedHashSet<Pair<String, Check>>()
        val feedStart = SystemClock.elapsedRealtime()
        val answers = store.answersAfter(m.fedThrough)
        for (answer in answers) {
            m.feed(answer)
            touched += answer.kanji to answer.check
        }
        Log.i(
            TAG,
            "${RwkvAlgorithmInfo.MODEL_FAMILY}: fed ${answers.size} answers in ${SystemClock.elapsedRealtime() - feedStart} ms" +
                (if (fresh) " (full replay; weights loaded in $loadMs ms)" else "") +
                (if (m.skipped > 0) ", ${m.skipped} skipped" else ""),
        )
        val curves = HashMap<Pair<String, Check>, RwkvCardHistory>()
        for (key in touched) m.history(key.first, key.second)?.let { curves[key] = it }
        store.saveMemory(curves, KaniStore.MemoryMeta(MemoryRules.IDENTITY, m.fedThrough), replaceAll = fresh)
        return store.answerCount().toLong()
    }

    private fun engine(): RwkvEngine {
        val bytes = appContext.assets.open(WEIGHTS_ASSET).use { it.readBytes() }
        return RwkvEngine(RwkvModel.load(RwkvWeights.parse(bytes, RwkvAlgorithmInfo.WEIGHTS_SHA256)))
    }

    companion object {
        private const val TAG = "KaniMemory"

        /** `rwkv/weights` is an asset source dir (see app/build.gradle.kts). */
        const val WEIGHTS_ASSET = "sm92/weights.f32.bin"
    }
}

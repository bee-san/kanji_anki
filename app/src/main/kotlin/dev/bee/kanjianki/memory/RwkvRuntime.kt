package dev.bee.kanjianki.memory

import android.content.Context
import dev.bee.kanjianki.AppDebugLog
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RwkvDays
import dev.bee.kanjianki.core.RwkvMemoryModel
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.MemoryModelSimulations
import dev.bee.rwkv.RwkvAlgorithmInfo
import dev.bee.rwkv.RwkvEngine
import dev.bee.rwkv.RwkvModel
import dev.bee.rwkv.RwkvOverlayView
import dev.bee.rwkv.RwkvWeights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide owner of the RWKV memory model: the bundled SM92 weights, and
 * whether the persisted model state is current ([Status.Ready]).
 *
 * Reviews schedule only from a ready state. After the v34 upgrade, a model
 * change, an undo the journal could not reverse, or a manual rebuild, the
 * state is rebuilt from the review history on a background thread while
 * Study answers are held back.
 */
internal object RwkvRuntime {
    sealed class Status {
        object Unchecked : Status()

        object Checking : Status()

        object Ready : Status()

        data class Rebuilding(val done: Int, val total: Int) : Status()

        data class Failed(val message: String) : Status()
    }

    const val WEIGHTS_ASSET: String = "rwkv/sm92/weights.f32.bin"

    private val mutableStatus = MutableStateFlow<Status>(Status.Unchecked)
    val status: StateFlow<Status> = mutableStatus

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kani-rwkv").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val running = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var loadedEngine: RwkvEngine? = null

    /** Called from `Application.onCreate`; no I/O. */
    fun attach(context: Context) {
        appContext = context.applicationContext
        MemoryModelSimulations.factory = ::simulationModel
    }

    fun isReady(): Boolean = status.value == Status.Ready

    /** The engine, loading and verifying the bundled weights on first use (off the main thread). */
    fun engine(context: Context? = appContext): RwkvEngine {
        loadedEngine?.let { return it }
        synchronized(this) {
            loadedEngine?.let { return it }
            val source = requireNotNull(context ?: appContext) { "RwkvRuntime is not attached" }
            val bytes = source.applicationContext.assets.open(WEIGHTS_ASSET).use { it.readBytes() }
            val engine = RwkvEngine(RwkvModel.load(RwkvWeights.parse(bytes, RwkvAlgorithmInfo.WEIGHTS_SHA256)))
            loadedEngine = engine
            return engine
        }
    }

    /** The model day of a review, matching the `review_day_start` the review log records. */
    fun dayOffsetOf(millis: Long): Long = RwkvDays.fromDayStart(LocalDayPolicy.localDayStart(millis))

    /**
     * True when reviews can schedule now. Call from a background thread: when
     * the state has not been checked yet this checks it synchronously (a
     * metadata query, plus loading the weights once), so the first answer
     * after start-up is not dropped just because the background check has not
     * run. A state that needs rebuilding starts the rebuild and returns false.
     */
    fun readyNow(context: Context, store: LocalStore): Boolean {
        if (isReady()) {
            return true
        }
        attach(context)
        if (!running.get() && status.value !is Status.Failed) {
            val ready = runCatching {
                engine()
                store.rwkvStateIsReady()
            }.getOrDefault(false)
            if (ready && !running.get()) {
                mutableStatus.compareAndSet(status.value, Status.Ready)
                if (isReady()) {
                    return true
                }
            }
        }
        ensureReady(context, store)
        return false
    }

    /** Test hook: forget the process-wide status (each test opens a fresh database). */
    internal fun resetForTests() {
        mutableStatus.value = Status.Unchecked
    }

    /**
     * Checks the persisted state and, if it is not current, rebuilds it in the
     * background. Cheap and idempotent once ready.
     */
    fun ensureReady(context: Context, store: LocalStore) {
        attach(context)
        if (isReady() || !running.compareAndSet(false, true)) {
            return
        }
        executor.execute {
            try {
                mutableStatus.value = Status.Checking
                val started = android.os.SystemClock.elapsedRealtime()
                val engine = engine()
                var rebuiltEvents = -1
                if (!store.rwkvStateIsReady()) {
                    mutableStatus.value = Status.Rebuilding(0, 0)
                    RwkvStateRebuilder(store, engine).rebuild { done, total ->
                        mutableStatus.value = Status.Rebuilding(done, total)
                        rebuiltEvents = total
                    }
                }
                mutableStatus.value = Status.Ready
                logcat(android.util.Log.INFO) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - started
                    if (rebuiltEvents >= 0) "memory model ready: rebuilt $rebuiltEvents history events in ${elapsed}ms" else "memory model ready (${elapsed}ms)"
                }
            } catch (error: Throwable) {
                AppDebugLog.logError("RWKV memory model preparation failed", error)
                logcat(android.util.Log.WARN) { "memory model preparation failed: ${error.javaClass.simpleName}" }
                mutableStatus.value = Status.Failed(error.message ?: error.javaClass.simpleName)
                if (error is Error) {
                    throw error
                }
            } finally {
                running.set(false)
            }
        }
    }

    /** One sanitized logcat line (no user data); absent where android.util.Log is unavailable. */
    private inline fun logcat(priority: Int, message: () -> String) {
        runCatching { android.util.Log.println(priority, LOG_TAG, message()) }
    }

    private const val LOG_TAG = "KaniRwkv"

    /** Re-checks after something may have invalidated the state (undo, stale commit). */
    fun recheck(context: Context, store: LocalStore) {
        if (status.value == Status.Ready) {
            mutableStatus.value = Status.Unchecked
        }
        ensureReady(context, store)
    }

    /** Throws the state away and rebuilds it from the review history. */
    fun requestRebuild(context: Context, store: LocalStore) {
        attach(context)
        executor.execute {
            runCatching { store.markRwkvStateForRebuild() }
                .onFailure { AppDebugLog.logError("RWKV rebuild request failed", it) }
        }
        executor.execute { mutableStatus.value = Status.Unchecked }
        executor.execute { ensureReady(context, store) }
    }

    /**
     * A memory model for long simulations (the completion forecast): single
     * pass per answer over a copy-on-write view of the persisted state, so
     * nothing is written. Null while the state is not ready.
     */
    fun simulationModel(store: LocalStore): RwkvMemoryModel? {
        if (!isReady() || appContext == null) {
            return null
        }
        // Lazily loaded: a forecast reads only the cards it simulates.
        val overlay = RwkvOverlayView(store.lazyRwkvView())
        return RwkvMemoryModel(engine(), overlay, ::dayOffsetOf, singlePass = true)
    }
}

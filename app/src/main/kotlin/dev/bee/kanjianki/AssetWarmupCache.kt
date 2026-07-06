package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.data.DictionaryAssets
import java.lang.ref.WeakReference

/**
 * Process-wide cache for the two heavy warmup assets: the parsed 9.5 MB stroke-guide
 * TSV and the installed dictionary lookup. These used to be cached per activity, so
 * every activity recreation (rotation, theme change, process-kept relaunch) re-parsed
 * the full stroke asset and re-verified the dictionary install. Caching per process
 * makes recreation free; entries are keyed by application context identity (held
 * weakly to satisfy lint - the application context lives as long as the process
 * anyway) so Robolectric tests, which create a fresh application per test, never
 * observe another test's cache.
 *
 * Separate locks per asset keep a slow dictionary install from blocking stroke-guide
 * readers and vice versa.
 */
internal object AssetWarmupCache {
    private val strokeGuideLock = Any()
    private val dictionaryLock = Any()

    @Volatile
    private var strokeGuideContext: WeakReference<Context>? = null

    @Volatile
    private var cachedStrokeGuides: Map<String, StrokeGuide>? = null

    @Volatile
    private var dictionaryContext: WeakReference<Context>? = null

    @Volatile
    private var cachedDictionaryLookup: DictionaryLookup? = null

    fun strokeGuides(context: Context): Map<String, StrokeGuide> {
        val app = context.applicationContext
        cachedStrokeGuides?.takeIf { strokeGuideContext?.get() === app }?.let { return it }
        synchronized(strokeGuideLock) {
            cachedStrokeGuides?.takeIf { strokeGuideContext?.get() === app }?.let { return it }
            val loaded = StrokeGuideAssets.load(app)
            strokeGuideContext = WeakReference(app)
            cachedStrokeGuides = loaded
            return loaded
        }
    }

    fun dictionaryLookup(context: Context): DictionaryLookup {
        val app = context.applicationContext
        cachedDictionaryLookup?.takeIf { dictionaryContext?.get() === app }?.let { return it }
        synchronized(dictionaryLock) {
            cachedDictionaryLookup?.takeIf { dictionaryContext?.get() === app }?.let { return it }
            val loaded = DictionaryAssets.load(app)
            dictionaryContext = WeakReference(app)
            cachedDictionaryLookup = loaded
            return loaded
        }
    }
}

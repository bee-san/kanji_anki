package dev.bee.kanjianki

import android.os.SystemClock
import android.util.Log
import dev.bee.kanjianki.core.ReadingExposureModels
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream

internal class ReadingExposureMediaReader(
    private val mediaDirs: List<File> = defaultCollectionMediaDirs(),
) {
    fun read(): ReadingExposureModels.ExposureIndex {
        // read() is called repeatedly on the study-load hot path (multiple adaptivePlan calls
        // per renderStudy, plus home and queued-entries paths). The backing file is a
        // multi-megabyte gzip on real collections, so re-reading + gunzipping + JSON-parsing it
        // every time is pure wasted main-thread work. Memoize by file identity (path + mtime +
        // size) so we only re-parse when the media file actually changes.
        var source = "uncached"
        return readingExposurePhase(
            phase = "total",
            details = { "source=$source" },
        ) {
            val fingerprint = readingExposurePhase("fingerprint") { fingerprint(mediaDirs) }
            val cached = synchronized(CACHE_LOCK) {
                if (cachedFingerprint != null && cachedFingerprint == fingerprint) {
                    cachedIndex
                } else {
                    null
                }
            }
            if (cached != null) {
                source = "cache"
                return@readingExposurePhase cached
            }
            val index = readingExposurePhase("uncached") { readUncached() }
            synchronized(CACHE_LOCK) {
                cachedFingerprint = fingerprint
                cachedIndex = index
            }
            index
        }
    }

    private fun readUncached(): ReadingExposureModels.ExposureIndex {
        for (dir in mediaDirs) {
            val index = readFromMediaDir(dir)
            if (index != null) {
                return index
            }
        }
        return ReadingExposureModels.ExposureIndex.EMPTY
    }

    private fun fingerprint(dirs: List<File>): String {
        val builder = StringBuilder()
        for (dir in dirs) {
            for (candidate in MANIFEST_CANDIDATES) {
                val manifest = File(dir, candidate.manifestFile)
                if (manifest.isFile) {
                    builder.append(manifest.path)
                        .append(':').append(manifest.lastModified())
                        .append(':').append(manifest.length())
                    // Fingerprint the actual manifest-declared stats file, not just the default,
                    // so a custom kanjiFile that changes without the manifest still busts the
                    // cache (matches readFromMediaDir's resolution).
                    val kanjiFile = File(dir, resolveKanjiFileName(manifest, candidate))
                    if (kanjiFile.isFile) {
                        builder.append('|').append(kanjiFile.name)
                            .append(':').append(kanjiFile.lastModified())
                            .append(':').append(kanjiFile.length())
                    }
                    builder.append(';')
                }
            }
        }
        return builder.toString()
    }

    private fun resolveKanjiFileName(manifest: File, candidate: ManifestCandidate): String {
        return runCatching {
            JSONObject(manifest.readText(Charsets.UTF_8)).optString(KANJI_FILE_KEY, candidate.defaultKanjiFile)
        }.getOrDefault(candidate.defaultKanjiFile)
    }

    private fun readFromMediaDir(dir: File): ReadingExposureModels.ExposureIndex? {
        for (candidate in MANIFEST_CANDIDATES) {
            val manifest = File(dir, candidate.manifestFile)
            if (!manifest.isFile) {
                continue
            }
            try {
                val manifestJson = readingExposurePhase("manifest-read") {
                    JSONObject(manifest.readText(Charsets.UTF_8))
                }
                val kanjiFile = manifestJson.optString(KANJI_FILE_KEY, candidate.defaultKanjiFile)
                val statsJson = readStatsText(File(dir, kanjiFile))
                val stats = readingExposurePhase(
                    phase = "parse",
                    details = { parsed -> "rows=${parsed.size}" },
                ) {
                    parseKanjiStats(statsJson)
                }
                return ReadingExposureModels.ExposureIndex(stats)
            } catch (error: IOException) {
                Log.w(TAG, "Could not read optional reading exposure media.", error)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not parse optional reading exposure media.", error)
            }
        }
        return null
    }

    private fun readStatsText(file: File): String {
        val phase = if (file.name.endsWith(".gz")) "gzip-read" else "plain-read"
        return readingExposurePhase(
            phase = phase,
            details = { text -> "source_bytes=${file.length()} decoded_chars=${text.length}" },
        ) {
            if (file.name.endsWith(".gz")) {
                GZIPInputStream(file.inputStream()).use { stream ->
                    String(stream.readBytes(), StandardCharsets.UTF_8)
                }
            } else {
                file.readText(Charsets.UTF_8)
            }
        }
    }

    companion object {
        const val MANIFEST_FILE: String = "_reading_exposure_manifest.json"
        const val DEFAULT_KANJI_FILE: String = "_reading_exposure_kanji.json.gz"
        const val LEGACY_KANI_MANIFEST_FILE: String = "_kani_reading_exposure_manifest.json"
        const val LEGACY_KANI_KANJI_FILE: String = "_kani_reading_exposure_kanji.json.gz"
        private const val KANJI_FILE_KEY = "kanjiFile"
        private const val TAG = "ReadingExposure"

        private val CACHE_LOCK = Any()
        @Volatile
        private var cachedFingerprint: String? = null
        @Volatile
        private var cachedIndex: ReadingExposureModels.ExposureIndex? = null
        private val MANIFEST_CANDIDATES = listOf(
            ManifestCandidate(MANIFEST_FILE, DEFAULT_KANJI_FILE),
            ManifestCandidate(LEGACY_KANI_MANIFEST_FILE, LEGACY_KANI_KANJI_FILE),
        )

        @JvmStatic
        fun defaultCollectionMediaDirs(): List<File> {
            return listOf(
                File("/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media"),
                File("/storage/emulated/0/AnkiDroid/collection.media"),
            )
        }

        @JvmStatic
        fun parseKanjiStats(text: String): List<ReadingExposureModels.KanjiStats> {
            val root = JSONObject(text)
            val rows = root.optJSONArray("kanji") ?: JSONArray()
            val out = ArrayList<ReadingExposureModels.KanjiStats>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val kanji = row.optString("kanji", "")
                if (kanji.isEmpty()) {
                    continue
                }
                out.add(
                    ReadingExposureModels.KanjiStats(
                        kanji,
                        intField(row, "totalCount", "total"),
                        intField(row, "last7DaysCount", "last7"),
                        intField(row, "last14DaysCount", "last14"),
                        intField(row, "last31DaysCount", "last31"),
                        longField(row, "lastSeenAtMillis", "lastSeen"),
                    ),
                )
            }
            return out
        }

        private fun intField(row: JSONObject, primary: String, fallback: String): Int {
            return if (row.has(primary)) row.optInt(primary, 0) else row.optInt(fallback, 0)
        }

        private fun longField(row: JSONObject, primary: String, fallback: String): Long {
            return if (row.has(primary)) row.optLong(primary, 0L) else row.optLong(fallback, 0L)
        }
    }

    private class ManifestCandidate(
        val manifestFile: String,
        val defaultKanjiFile: String,
    )
}

/** Capture-gated release timings for external-media fingerprint/read/parse work. */
internal fun <T> readingExposurePhase(
    phase: String,
    details: (T) -> String = { "" },
    action: () -> T,
): T {
    if (!AppDebugLog.isCapturing()) {
        return action()
    }
    val startedAtNanos = readingExposureMonotonicNanos()
    return try {
        val result = action()
        val detail = details(result).trim()
        AppDebugLog.log(
            String.format(
                Locale.US,
                "reading-exposure phase=%s duration_ms=%.2f%s",
                traceToken(phase),
                (readingExposureMonotonicNanos() - startedAtNanos) / 1_000_000.0,
                if (detail.isEmpty()) "" else " $detail",
            ),
        )
        result
    } catch (error: Throwable) {
        // Optional-media parser exceptions can embed the source JSON or filesystem path in their
        // message. The debug log is user-shareable, so record only the phase and exception type;
        // do not serialize the message or stack on this measured route thread.
        AppDebugLog.log(
            String.format(
                Locale.US,
                "reading-exposure phase=%s failed duration_ms=%.2f error_type=%s",
                traceToken(phase),
                (readingExposureMonotonicNanos() - startedAtNanos) / 1_000_000.0,
                traceToken(error.javaClass.simpleName),
            ),
        )
        throw error
    }
}

private fun readingExposureMonotonicNanos(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}

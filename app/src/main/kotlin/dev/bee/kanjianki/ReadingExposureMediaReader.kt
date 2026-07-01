package dev.bee.kanjianki

import android.util.Log
import dev.bee.kanjianki.core.ReadingExposureModels
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
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
        val fingerprint = fingerprint(mediaDirs)
        synchronized(CACHE_LOCK) {
            if (cachedFingerprint != null && cachedFingerprint == fingerprint) {
                cachedIndex?.let { return it }
            }
        }
        val index = readUncached()
        synchronized(CACHE_LOCK) {
            cachedFingerprint = fingerprint
            cachedIndex = index
        }
        return index
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
                val manifestJson = JSONObject(manifest.readText(Charsets.UTF_8))
                val kanjiFile = manifestJson.optString(KANJI_FILE_KEY, candidate.defaultKanjiFile)
                val statsJson = readStatsText(File(dir, kanjiFile))
                return ReadingExposureModels.ExposureIndex(parseKanjiStats(statsJson))
            } catch (error: IOException) {
                Log.w(TAG, "Could not read optional reading exposure media.", error)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not parse optional reading exposure media.", error)
            }
        }
        return null
    }

    private fun readStatsText(file: File): String {
        if (file.name.endsWith(".gz")) {
            return GZIPInputStream(file.inputStream()).use { stream ->
                String(stream.readBytes(), StandardCharsets.UTF_8)
            }
        }
        return file.readText(Charsets.UTF_8)
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

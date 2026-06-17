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
        for (dir in mediaDirs) {
            val index = readFromMediaDir(dir)
            if (index != null) {
                return index
            }
        }
        return ReadingExposureModels.ExposureIndex.EMPTY
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

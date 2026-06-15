package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.core.AttributionCopy
import dev.bee.kanjianki.data.DictionaryStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal object AttributionTexts {
    @JvmStatic
    fun kanjiVg(context: Context?): String {
        val text = rawResourceText(context, R.raw.kanjivg_attribution).trim()
        return text.ifEmpty { AttributionCopy.kanjiVgFallback() }
    }

    @JvmStatic
    fun dictionarySources(context: Context?): String {
        return try {
            dictionarySourcesFromManifestText(DictionaryStore.activeManifestText(context!!))
        } catch (_: Exception) {
            AttributionCopy.dictionaryFallback()
        }
    }

    @JvmStatic
    fun dictionarySourcesFromManifestText(manifestText: String?): String {
        return try {
            dictionarySourcesFromManifest(JSONObject(manifestText ?: throw NullPointerException()))
        } catch (_: Exception) {
            AttributionCopy.dictionaryFallback()
        }
    }

    @JvmStatic
    fun dictionarySourcesFromManifest(manifest: JSONObject?): String {
        return try {
            val sources = manifest?.optJSONArray("sources") ?: return AttributionCopy.dictionaryFallback()
            if (sources.length() == 0) {
                return AttributionCopy.dictionarySources("", emptyList(), emptyList())
            }
            AttributionCopy.dictionarySources(
                manifest.optString("generated_at"),
                sourcesFromJson(sources),
                notesFromJson(manifest.optJSONArray("notes")),
            )
        } catch (_: Exception) {
            AttributionCopy.dictionaryFallback()
        }
    }

    @JvmStatic
    fun rawResourceText(context: Context?, resourceId: Int): String {
        return try {
            context!!.resources.openRawResource(resourceId).use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read == -1) {
                            break
                        }
                        out.append(buffer, 0, read)
                    }
                    out.toString().trim()
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    @Throws(Exception::class)
    private fun sourcesFromJson(sources: JSONArray): List<AttributionCopy.Source?> {
        val parsed = ArrayList<AttributionCopy.Source?>()
        for (i in 0 until sources.length()) {
            parsed.add(sourceFromJson(sources.getJSONObject(i)))
        }
        return parsed
    }

    private fun sourceFromJson(source: JSONObject?): AttributionCopy.Source? {
        if (source == null) {
            return null
        }
        return AttributionCopy.Source(
            source.optString("id"),
            source.optString("name"),
            source.optString("license"),
            source.optString("upstream_url"),
            source.optString("source_path"),
            source.optString("fetch_date"),
            source.optString("database_version"),
            source.optString("version"),
            source.optString("date_of_creation"),
            source.optString("source_sha256"),
        )
    }

    private fun notesFromJson(notes: JSONArray?): List<String> {
        if (notes == null || notes.length() == 0) {
            return emptyList()
        }
        val parsed = ArrayList<String>()
        for (i in 0 until notes.length()) {
            parsed.add(notes.optString(i))
        }
        return parsed
    }
}

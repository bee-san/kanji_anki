package dev.bee.kanjianki.kani.data

import android.content.Context
import androidx.core.content.edit
import dev.bee.kanjianki.kani.ui.ThemeMode

/** Small settings that are not training data. */
class KaniPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kani1", Context.MODE_PRIVATE)

    data class Source(
        val modelNames: List<String>,
        val expressionField: Map<String, String>,
        val readingField: Map<String, String>,
        val meaningField: Map<String, String>,
    )

    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(value) = prefs.edit { putBoolean("setup_done", value) }

    var minutes: Int
        get() = prefs.getInt("minutes", 5)
        set(value) = prefs.edit { putInt("minutes", value) }

    var reminder: Boolean
        get() = prefs.getBoolean("reminder", true)
        set(value) = prefs.edit { putBoolean("reminder", value) }

    var reminderMinuteOfDay: Int
        get() = prefs.getInt("reminder_minute", 19 * 60)
        set(value) = prefs.edit { putInt("reminder_minute", value) }

    var handwriting: Boolean
        get() = prefs.getBoolean("handwriting", true)
        set(value) = prefs.edit { putBoolean("handwriting", value) }

    var tagInAnki: Boolean
        get() = prefs.getBoolean("tag_in_anki", false)
        set(value) = prefs.edit { putBoolean("tag_in_anki", value) }

    var speak: Boolean
        get() = prefs.getBoolean("speak", true)
        set(value) = prefs.edit { putBoolean("speak", value) }

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit { putString("theme", value.name) }

    var lastScanAt: Long
        get() = prefs.getLong("last_scan_at", 0L)
        set(value) = prefs.edit { putLong("last_scan_at", value) }

    var source: Source?
        get() {
            val models = prefs.getString("source_models", null)?.split(SEP)?.filter { it.isNotEmpty() } ?: return null
            if (models.isEmpty()) return null
            fun map(key: String) = prefs.getString(key, "").orEmpty().split(SEP).mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            return Source(models, map("source_expr"), map("source_reading"), map("source_meaning"))
        }
        set(value) {
            prefs.edit {
                if (value == null) {
                    remove("source_models")
                } else {
                    fun enc(m: Map<String, String>) = m.entries.joinToString(SEP) { it.key + "=" + it.value }
                    putString("source_models", value.modelNames.joinToString(SEP))
                    putString("source_expr", enc(value.expressionField))
                    putString("source_reading", enc(value.readingField))
                    putString("source_meaning", enc(value.meaningField))
                }
            }
        }

    private companion object {
        const val SEP = "\u001e"
    }
}

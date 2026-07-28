package dev.bee.kanjianki.anki

import dev.bee.kanjianki.core.TextUtil
import java.util.regex.Pattern

/**
 * Removes Anki-only markup before field text reaches the aggregate inventory.
 */
object AnkiFieldTextNormalizer {
    private val soundMarker = Pattern.compile(
        "\\[sound:[^\\]\\r\\n]*]",
        Pattern.CASE_INSENSITIVE,
    )

    @JvmStatic
    fun normalize(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return TextUtil.stripHtml(soundMarker.matcher(value).replaceAll(" "))
    }
}

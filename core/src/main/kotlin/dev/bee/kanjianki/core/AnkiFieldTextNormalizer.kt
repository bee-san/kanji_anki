package dev.bee.kanjianki.core

import java.util.regex.Pattern

/**
 * Removes Anki-only markup before field text reaches the aggregate inventory.
 *
 * This lives in `:core` rather than in a provider because both providers must
 * apply the *same* rule: an inventory scanned over AnkiConnect and one scanned
 * over AnkiDroid's provider have to agree on which kanji a collection contains,
 * and a second hand-copied sound-marker/HTML rule would diverge silently.
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

package dev.bee.kanjianki.presentation

/**
 * Text a portable screen wants displayed, without deciding how it is looked up.
 *
 * Android screen models currently carry `Int` resource ids, which cannot cross to
 * a desktop host, and raw `String`s, which cannot be localized or pluralized.
 * A [UiText] is neither: it is either a value the presentation layer computed
 * ([Literal]) or a name the host resolves ([Key]/[Quantity]). Resolution lives in
 * the host through [UiTextResolver], so common code never needs a resource
 * lookup that only one platform can answer.
 */
sealed interface UiText {
    /**
     * A value already in its final form — a kanji, a user's own query, a count.
     *
     * Not everything on screen is translatable copy. Wrapping the user's own
     * data in a lookup key would be wrong, so this variant exists to keep it
     * out of the resource table.
     */
    data class Literal(val text: String) : UiText

    /** Copy the host resolves by name, with any nested arguments. */
    data class Key(
        val key: String,
        val arguments: List<UiText> = emptyList(),
    ) : UiText {
        init {
            require(key.isNotBlank()) { "UiText key must not be blank" }
        }
    }

    /**
     * Copy whose wording depends on [count].
     *
     * Kept distinct from [Key] because plural selection is a host rule: the
     * languages Kani ships do not agree on how many plural forms exist, so
     * common code must pass the number rather than pick the form.
     */
    data class Quantity(
        val key: String,
        val count: Int,
        val arguments: List<UiText> = emptyList(),
    ) : UiText {
        init {
            require(key.isNotBlank()) { "UiText key must not be blank" }
        }
    }

    companion object {
        /** Convenience for the common "no text at all" case. */
        val EMPTY: UiText = Literal("")
    }
}

/**
 * Host-side resolution of a [UiText] into a displayable string.
 *
 * Implemented once per host (Android resources, Compose Multiplatform
 * resources). Common code takes this as a parameter rather than reaching for a
 * platform lookup, which is what keeps `:presentation-api` free of every
 * project dependency.
 */
fun interface UiTextResolver {
    fun resolve(text: UiText): String
}

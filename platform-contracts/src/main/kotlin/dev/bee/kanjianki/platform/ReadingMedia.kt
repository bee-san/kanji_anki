package dev.bee.kanjianki.platform

data class ReadingMediaMetadata(
    val name: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
) {
    init {
        require(isSafeMediaName(name)) { "reading media name must be a safe file name" }
        require(sizeBytes >= 0L) { "reading media size must not be negative" }
        require(modifiedAtMillis >= 0L) { "reading media modification time must not be negative" }
    }
}

interface ReadingMediaSource {
    /**
     * Stable opaque identity used only to invalidate process-local parse
     * caches when a host switches media roots.
     */
    fun cacheIdentity(): String? = null

    fun metadata(name: String): ReadingMediaMetadata?

    fun read(name: String, maximumBytes: Int): ByteArray?
}

/**
 * The one media-name rule. Public because a [ReadingMediaSource] has to be able
 * to *reject* a name without constructing [ReadingMediaMetadata] first — that
 * constructor throws, and a media lookup for an unusable name is a null result,
 * not a crash. Every implementation must screen with this same predicate so a
 * traversal attempt cannot be safe on one host and unsafe on another.
 */
fun isSafeMediaName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name &&
        '\u0000' !in name

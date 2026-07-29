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

private fun isSafeMediaName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name &&
        '\u0000' !in name

package dev.bee.kanjianki.core

object WhatsNewCardPolicy {
    @JvmStatic
    fun shouldShow(
        currentVersion: String?,
        storedNotesVersion: String?,
        notesBlank: Boolean,
        seenVersion: String?,
    ): Boolean {
        val version = currentVersion.orEmpty()
        if (version.isEmpty()) return false
        if (storedNotesVersion.orEmpty() != version) return false
        if (notesBlank) return false
        if (seenVersion.orEmpty() == version) return false
        return true
    }
}

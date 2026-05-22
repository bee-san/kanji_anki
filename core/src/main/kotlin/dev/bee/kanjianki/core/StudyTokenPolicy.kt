package dev.bee.kanjianki.core

import java.util.UUID

object StudyTokenPolicy {
    @JvmStatic
    fun studyItem(kanji: String?, activeToken: String?): String {
        return existingOrNew(activeToken, "$kanji-")
    }

    private fun existingOrNew(activeToken: String?, prefix: String): String {
        if (!activeToken.isNullOrEmpty()) {
            return activeToken
        }
        return prefix + UUID.randomUUID()
    }
}

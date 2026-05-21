package dev.bee.kanjianki

import dev.bee.kanjianki.core.StudyTokenPolicy

internal object StudyTokenFactory {
    @JvmStatic
    fun studyItem(kanji: String?, activeToken: String?): String {
        return StudyTokenPolicy.studyItem(kanji, activeToken)
    }
}

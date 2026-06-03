package dev.bee.kanjianki.core

object StudyLeechPolicy {
    const val DEFAULT_LEECH_LAPSE_THRESHOLD: Int = 8
    const val LEECH_TAG: String = "leech"

    @JvmStatic
    fun lapseCount(item: RecordsStudyModels.StudyItem?): Int {
        return lapseCount(item, null)
    }

    @JvmStatic
    fun lapseCount(item: RecordsStudyModels.StudyItem?, taskType: String?): Int {
        if (item == null) {
            return 0
        }
        val taskLapses = item.memoryForTaskType(taskType).lapses
        return maxOf(item.lapses, taskLapses)
    }

    @JvmStatic
    fun isLeech(item: RecordsStudyModels.StudyItem?): Boolean {
        return isLeech(item, DEFAULT_LEECH_LAPSE_THRESHOLD)
    }

    @JvmStatic
    fun isLeech(item: RecordsStudyModels.StudyItem?, taskType: String?): Boolean {
        return isLeech(item, taskType, DEFAULT_LEECH_LAPSE_THRESHOLD)
    }

    @JvmStatic
    fun isLeech(item: RecordsStudyModels.StudyItem?, threshold: Int): Boolean {
        return isLeech(item, null, threshold)
    }

    @JvmStatic
    fun isLeech(item: RecordsStudyModels.StudyItem?, taskType: String?, threshold: Int): Boolean {
        return lapseCount(item, taskType) >= threshold.coerceAtLeast(1)
    }

    @JvmStatic
    fun tagFor(item: RecordsStudyModels.StudyItem?): String {
        return if (isLeech(item)) LEECH_TAG else ""
    }

    @JvmStatic
    fun tagFor(item: RecordsStudyModels.StudyItem?, taskType: String?): String {
        return if (isLeech(item, taskType)) LEECH_TAG else ""
    }
}

package dev.bee.kanjianki.core

object StudyExampleSelector {
    private const val SOURCE_ACTIVE = "active"
    private const val SOURCE_SUSPENDED = "suspended"

    @JvmStatic
    fun firstExample(row: RecordsImportModels.DashboardRow?): RecordsImportModels.Example? {
        if (row == null || row.examples.isEmpty()) {
            return null
        }
        for (example in row.examples) {
            if (SOURCE_ACTIVE == example.sourceType) {
                return example
            }
        }
        return row.examples[0]
    }

    @JvmStatic
    fun wordReadingExample(row: RecordsImportModels.DashboardRow?): RecordsImportModels.Example? {
        if (row == null || row.examples.isEmpty()) {
            return null
        }
        var active: RecordsImportModels.Example? = null
        for (example in row.examples) {
            if (SOURCE_SUSPENDED == example.sourceType) {
                return example
            }
            if (active == null && SOURCE_ACTIVE == example.sourceType) {
                active = example
            }
        }
        return active ?: row.examples[0]
    }

    @JvmStatic
    fun exampleForSession(session: RecordsSchedulerModels.StudySession?): RecordsImportModels.Example? {
        if (session != null && StudyTaskTypes.WORD_READING == session.taskType) {
            return wordReadingExample(session.row)
        }
        return firstExample(if (session == null) null else session.row)
    }
}

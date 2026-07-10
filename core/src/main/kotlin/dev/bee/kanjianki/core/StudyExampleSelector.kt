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

    /**
     * Example for the sentence_reading rung (Goal 80): prefer an example that
     * has BOTH a sentence and a reading (the card front is the sentence, the
     * back the reading), and among those prefer suspended then active — the
     * same trust ordering as word_reading. Returns null when no example carries
     * both fields (the rung is then unavailable for the card).
     */
    @JvmStatic
    fun sentenceReadingExample(row: RecordsImportModels.DashboardRow?): RecordsImportModels.Example? {
        if (row == null || row.examples.isEmpty()) {
            return null
        }
        var active: RecordsImportModels.Example? = null
        for (example in row.examples) {
            if (!hasSentenceAndReading(example)) {
                continue
            }
            if (SOURCE_SUSPENDED == example.sourceType) {
                return example
            }
            if (active == null && SOURCE_ACTIVE == example.sourceType) {
                active = example
            }
        }
        if (active != null) {
            return active
        }
        // No active/suspended qualifier matched; fall back to any example with
        // both fields (e.g. an untyped source).
        return row.examples.firstOrNull { hasSentenceAndReading(it) }
    }

    private fun hasSentenceAndReading(example: RecordsImportModels.Example): Boolean {
        return example.sentence.isNotBlank() && example.reading.isNotBlank()
    }

    @JvmStatic
    fun exampleForSession(session: RecordsSchedulerModels.StudySession?): RecordsImportModels.Example? {
        if (session != null && StudyTaskTypes.SENTENCE_READING == session.taskType) {
            return sentenceReadingExample(session.row)
        }
        if (session != null && StudyTaskTypes.WORD_READING == session.taskType) {
            return wordReadingExample(session.row)
        }
        return firstExample(if (session == null) null else session.row)
    }
}

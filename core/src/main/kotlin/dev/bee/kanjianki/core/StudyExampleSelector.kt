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
        if (session != null && StudyTaskTypes.TYPE_READING == session.taskType) {
            return typeReadingExample(session)
        }
        if (session != null &&
            StudyTaskTypes.WORD_READING == session.taskType
        ) {
            return wordReadingExample(session.row)
        }
        return firstExample(if (session == null) null else session.row)
    }

    /**
     * A typed-reading repair must repeat the exact word and full reading that
     * failed, not whichever generic example currently wins the row preference.
     * The evidence is persisted in the adaptive route, so it also survives a
     * process restart. Missing evidence falls back to the normal word selector.
     */
    private fun typeReadingExample(
        session: RecordsSchedulerModels.StudySession,
    ): RecordsImportModels.Example? {
        val fallback = wordReadingExample(session.row)
        val evidence = AdaptiveStudyItemPolicy.routeState(session.item)?.answerEvidence ?: return fallback
        if (evidence.renderedExpression.isBlank() && evidence.renderedReading.isBlank()) {
            return fallback
        }
        val expression = evidence.renderedExpression.ifBlank { fallback?.expression.orEmpty() }
        val reading = evidence.renderedReading.ifBlank { fallback?.reading.orEmpty() }
        session.row?.examples?.firstOrNull {
            it.expression == expression && it.reading == reading
        }?.let { return it }
        val matchingExpression = fallback?.takeIf { it.expression == expression }
        return RecordsImportModels.Example(
            "adaptive_evidence",
            matchingExpression?.cardId ?: 0L,
            matchingExpression?.noteId ?: 0L,
            expression,
            reading,
            matchingExpression?.meaning.orEmpty(),
            matchingExpression?.sentence.orEmpty(),
            matchingExpression?.mature ?: false,
            matchingExpression?.lapses ?: 0,
            matchingExpression?.intervalDays ?: 0,
            matchingExpression?.reps ?: 0,
            matchingExpression?.fsrsStability,
            matchingExpression?.fsrsDifficulty,
            matchingExpression?.fsrsRetrievability,
        )
    }
}

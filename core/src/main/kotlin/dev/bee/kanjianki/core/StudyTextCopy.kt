package dev.bee.kanjianki.core

object StudyTextCopy {
    const val SIMILAR_REPAIR_REASON: String = "Why: similar-kanji miss \u00b7 writing repair \u00b7 practice-only"

    @JvmStatic
    fun countText(count: Int, singular: String, plural: String): String {
        return "$count " + if (count == 1) singular else plural
    }

    @JvmStatic
    fun rowMeaning(row: RecordsImportModels.DashboardRow?): String {
        return cleanLearnerText(row?.primaryMeaning, row?.reasonCode, 72)
    }

    @JvmStatic
    fun sessionClue(
        dictionaryLookup: DictionaryLookup?,
        session: RecordsSchedulerModels.StudySession?,
    ): String {
        val raw = sessionClueRawText(session)
        val kanji = if (session == null || session.item == null) "" else session.item.kanji
        return canonicalKanjiMeaning(dictionaryLookup, kanji, raw, 96)
    }

    @JvmStatic
    fun canonicalKanjiMeaning(
        dictionaryLookup: DictionaryLookup?,
        kanji: String?,
        fallback: String?,
        maxChars: Int,
    ): String {
        val lookup = dictionaryLookup ?: DictionaryLookup.empty()
        val entry = lookup.lookupKanji(kanji)
        if (entry != null) {
            val meaning = StudyCueFormatter.displayGlosses(entry.meanings, 2)
            if (meaning.isNotEmpty()) {
                return compact(meaning, maxChars)
            }
        }
        return cleanLearnerText(fallback, "Collection clue", maxChars)
    }

    @JvmStatic
    fun wordPrompt(session: RecordsSchedulerModels.StudySession?): String {
        val example = if (session == null) null else StudyExampleSelector.wordReadingExample(session.row)
        if (example != null && example.expression.isNotEmpty()) {
            return example.expression
        }
        return if (session == null || session.item == null) "" else session.item.kanji
    }

    @JvmStatic
    fun heroQuestion(session: RecordsSchedulerModels.StudySession?): String {
        if (session != null && StudyTaskTypes.WORD_READING == session.taskType) {
            return "What is the reading?"
        }
        return "What does this kanji mean?"
    }

    @JvmStatic
    fun collectionMeaningForSession(session: RecordsSchedulerModels.StudySession?): String {
        if (session?.row == null) {
            return ""
        }
        val example = StudyExampleSelector.exampleForSession(session)
        if (example != null && example.meaning.isNotEmpty()) {
            return example.meaning
        }
        return session.row.primaryMeaning
    }

    @JvmStatic
    fun meaningKanjiChoiceQuestion(card: RecordsImportModels.MeaningKanjiChoiceCard?, prompt: String?): String {
        return "Which kanji means " + cleanLearnerText(card?.primaryMeaning, prompt, 96) + "?"
    }

    @JvmStatic
    fun meaningKanjiChoiceResult(
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        correct: Boolean,
    ): String {
        val targetKanji = card?.targetKanji ?: ""
        val meaning = cleanLearnerText(card?.primaryMeaning, prompt, 72)
        if (correct) {
            return "Correct. $targetKanji means $meaning."
        }
        return "Answer: $targetKanji \u00b7 $meaning"
    }

    @JvmStatic
    fun typingAnswerAcceptedToast(): String {
        return "Typing answer accepted."
    }

    @JvmStatic
    fun studyDoneTitle(): String {
        return "Today's focus done"
    }

    @JvmStatic
    fun adaptiveFocusDoneBody(): String {
        return "Kani finished today's adaptive focus. You can stop here, or keep going through all current problem kanji."
    }

    @JvmStatic
    fun studyRunDoneBody(): String {
        return "Kani finished the Study now set. You can stop here, or explicitly continue through all current problem kanji."
    }

    @JvmStatic
    fun adaptiveFocusDoneSummary(target: Int): String {
        return "Today's focus: 0 items left / $target"
    }

    @JvmStatic
    fun movedForwardSummary(count: Int): String {
        return countText(count, "kanji moved forward this session", "kanji moved forward this session")
    }

    @JvmStatic
    fun missedSummary(count: Int): String {
        return countText(count, "missed and will come back", "missed and will come back")
    }

    @JvmStatic
    fun completedTaskSummary(count: Int): String {
        return countText(count, "task completed", "tasks completed")
    }

    @JvmStatic
    fun similarWritingRepairSavedToast(passed: Boolean): String {
        return if (passed) "Repair saved." else "Saved. Try that repair again."
    }

    @JvmStatic
    fun similarRepairPrompt(repair: RecordsImportModels.SimilarKanjiWritingRepair): String {
        return buildString {
            append("Repair the shape mix-up")
            if (repair.promptMeaning.isNotEmpty()) {
                append(" for ").append(repair.promptMeaning)
            }
            if (repair.wrongSelection.isNotEmpty()) {
                append(". You picked ").append(repair.wrongSelection).append("; write ").append(repair.repairKanji).append(".")
            } else {
                append(". Write ").append(repair.repairKanji).append(".")
            }
        }
    }

    @JvmStatic
    fun studyReasonLine(
        similarRepairActive: Boolean,
        session: RecordsSchedulerModels.StudySession?,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): String {
        if (similarRepairActive) {
            return SIMILAR_REPAIR_REASON
        }
        if (session?.row == null) {
            return ""
        }
        return FocusQueueCopy.focusReasonLine(session.row, session.item, nowMillis, matureSupportThreshold)
    }

    @JvmStatic
    fun cleanLearnerText(raw: String?, fallback: String?, maxChars: Int): String {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars)
    }

    @JvmStatic
    fun compact(value: String?, maxChars: Int): String {
        return StudyCueFormatter.compact(value, maxChars)
    }

    private fun sessionClueRawText(session: RecordsSchedulerModels.StudySession?): String {
        if (session == null) {
            return ""
        }
        if (session.row == null || session.row.primaryMeaning.isEmpty()) {
            return session.prompt
        }
        return session.row.primaryMeaning
    }
}

package dev.bee.kanjianki.core

object StudyTextCopy {
    @JvmStatic
    fun countText(count: Int, singular: String?, plural: String?): String {
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
        val kanji = session?.item?.kanji ?: ""
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
        return session?.item?.kanji ?: ""
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
        return meaningKanjiChoiceQuestion(null, card, prompt)
    }

    @JvmStatic
    fun meaningKanjiChoiceQuestion(
        dictionaryLookup: DictionaryLookup?,
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
    ): String {
        return "Which kanji means " + meaningKanjiChoiceMeaning(dictionaryLookup, card, prompt, 96) + "?"
    }

    @JvmStatic
    fun meaningKanjiChoiceResult(
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        correct: Boolean,
    ): String {
        return meaningKanjiChoiceResult(null, card, prompt, correct)
    }

    @JvmStatic
    fun meaningKanjiChoiceResult(
        dictionaryLookup: DictionaryLookup?,
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        correct: Boolean,
    ): String {
        val targetKanji = card?.targetKanji ?: ""
        val meaning = meaningKanjiChoiceMeaning(dictionaryLookup, card, prompt, 72)
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
            if (repair.wrongSelection.isNotEmpty()) {
                append("You picked ").append(repair.wrongSelection).append(" — write ").append(repair.repairKanji).append(".")
            } else {
                append("Write ").append(repair.repairKanji).append(".")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun studyReasonLine(
        similarRepairActive: Boolean,
        session: RecordsSchedulerModels.StudySession?,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): String {
        return ""
    }

    @JvmStatic
    fun cleanLearnerText(raw: String?, fallback: String?, maxChars: Int): String {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars)
    }

    @JvmStatic
    fun compact(value: String?, maxChars: Int): String {
        return StudyCueFormatter.compact(value, maxChars)
    }

    private fun meaningKanjiChoiceMeaning(
        dictionaryLookup: DictionaryLookup?,
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        maxChars: Int,
    ): String {
        val testedMeaning = StudyCueFormatter.cleanMeaningText(card?.primaryMeaning ?: prompt)
        if (testedMeaning.isNotEmpty()) {
            return cleanLearnerText(testedMeaning, "", maxChars)
        }
        return canonicalKanjiMeaning(dictionaryLookup, card?.targetKanji, prompt, maxChars)
    }

    private fun sessionClueRawText(session: RecordsSchedulerModels.StudySession?): String? {
        if (session == null) {
            return ""
        }
        if (session.row == null || session.row.primaryMeaning.isEmpty()) {
            return session.prompt
        }
        return session.row.primaryMeaning
    }
}

package dev.bee.kanjianki.domain.model.study

private val multiWhitespace = "\\s+".toRegex()

enum class StudyItemState(val wireName: String) {
    NEW("new"),
    LEARNING("learning"),
    REVIEW("review"),
    RETIRED("retired");

    companion object {
        fun fromWireName(wireName: String): StudyItemState =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown study item state: $wireName")
    }
}

data class StudyExample @JvmOverloads constructor(
    val sourceType: String,
    val expression: String,
    val reading: String,
    val meaning: String,
    val fsrsDifficulty: Double? = null,
    val fsrsRetrievability: Double? = null,
    val mature: Boolean = false,
    val lapses: Int = 0,
    val intervalDays: Int = 0,
    val reps: Int = 0,
    val fsrsStability: Double? = null,
    val cardId: Long = 0,
    val noteId: Long = 0,
    val sentence: String = "",
)

data class StudyDashboardRow(
    val kanji: String,
    val jitenRank: Int?,
    val primaryMeaning: String,
    val reading: String,
    val browserSearch: String,
    val weaknessScore: Int,
    val reasonCode: String,
    val reasonText: String,
    val activeExampleCount: Int,
    val suspendedExampleCount: Int,
    val matureSupportCount: Int,
    val examples: List<StudyExample> = emptyList(),
) {
    fun answerSignature(): String {
        val example = selectedSignatureExample()
        val expression = example?.expression.orEmpty()
        val signatureReading = example?.reading ?: reading
        val meaning = example?.meaning ?: primaryMeaning
        return listOf(kanji, expression, signatureReading, meaning)
            .joinToString(separator = "|") { it.normalizedSignaturePart() }
    }

    fun familyKey(): String = StudyQueueFamilyKey.of(kanji, answerSignature())

    private fun selectedSignatureExample(): StudyExample? {
        var activeExample: StudyExample? = null
        for (example in examples) {
            if (example.sourceType == "suspended") {
                return example
            }
            if (activeExample == null && example.sourceType == "active") {
                activeExample = example
            }
        }
        return activeExample ?: examples.firstOrNull()
    }
}

data class StudyQueueItem @JvmOverloads constructor(
    val kanji: String,
    val state: StudyItemState,
    val dueAtMillis: Long,
    val stability: Double,
    val difficulty: Double,
    val totalReviews: Int,
    val lapses: Int,
    val learningStep: Int,
    val writingLevel: Int,
    val matureIntervalDays: Int = 0,
    val answerSignature: String = "",
    val rung: StudyRung = StudyRung.KANJI_MEANING,
    val phase: StudyPhase = StudyPhase.NEW_LEARNING,
    val realPassStreak: Int = 0,
    val realAgainStreak: Int = 0,
    val lastRealReviewDueAtMillis: Long = 0L,
    val suppressedByTaskType: String = "",
    val hasSimilarKanji: Boolean = false,
    val activeToken: String? = null,
    val memories: TaskMemoryBank = TaskMemoryBank(),
    val createdAtMillis: Long = 0L,
    val suppressedAtMillis: Long = 0L,
) {
    init {
        require(kanji.isNotBlank()) { "kanji must not be blank" }
        require(dueAtMillis >= 0L) { "dueAtMillis must not be negative" }
        require(totalReviews >= 0) { "totalReviews must not be negative" }
        require(lapses >= 0) { "lapses must not be negative" }
        require(learningStep >= 0) { "learningStep must not be negative" }
        require(writingLevel >= 0) { "writingLevel must not be negative" }
        require(matureIntervalDays >= 0) { "matureIntervalDays must not be negative" }
        require(realPassStreak >= 0) { "realPassStreak must not be negative" }
        require(realAgainStreak >= 0) { "realAgainStreak must not be negative" }
        require(lastRealReviewDueAtMillis >= 0L) {
            "lastRealReviewDueAtMillis must not be negative"
        }
        require(createdAtMillis >= 0L) { "createdAtMillis must not be negative" }
        require(suppressedAtMillis >= 0L) { "suppressedAtMillis must not be negative" }
    }

    val familyKey: String
        get() = StudyQueueFamilyKey.of(kanji, answerSignature)

    val isRetired: Boolean
        get() = state == StudyItemState.RETIRED

    val isSuppressed: Boolean
        get() = suppressedByTaskType.isNotEmpty()
}

object StudyQueueFamilyKey {
    fun of(
        kanji: String,
        answerSignature: String?,
    ): String = kanji + "\u0000" + answerSignature.orEmpty()
}

private fun String.normalizedSignaturePart(): String =
    multiWhitespace.replace(trim(), " ")

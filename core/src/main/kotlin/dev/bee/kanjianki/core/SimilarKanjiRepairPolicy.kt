package dev.bee.kanjianki.core

object SimilarKanjiRepairPolicy {
    @JvmField
    val STATUS_PENDING: String = "pending"

    @JvmField
    val STATUS_COMPLETE: String = "complete"

    @JvmField
    val STATUS_SKIPPED: String = "skipped"

    @JvmStatic
    fun newRepair(
        card: RecordsImportModels.SimilarKanjiChoiceCard?,
        repairKanji: String?,
        wrongSelection: String?,
        nowMillis: Long,
    ): RepairDraft? {
        if (card == null) {
            return null
        }
        val normalized = TextUtil.normalizeSingleKanji(repairKanji)
        if (normalized.isEmpty()) {
            return null
        }
        return RepairDraft(
            card.targetKanji,
            normalized,
            card.choiceSignature,
            wrongSelection ?: "",
            card.primaryMeaning,
            STATUS_PENDING,
            nowMillis,
            "",
            0,
            nowMillis,
            nowMillis,
            0L,
        )
    }

    @JvmStatic
    fun finishUpdate(
        current: RecordsImportModels.SimilarKanjiWritingRepair?,
        passed: Boolean,
        nowMillis: Long,
    ): FinishUpdate {
        if (passed) {
            return FinishUpdate("", nowMillis, STATUS_COMPLETE, nowMillis, null, null)
        }
        val attempts = if (current == null) 1 else current.attempts + 1
        return FinishUpdate("", nowMillis, null, null, attempts, nowMillis)
    }

    @JvmStatic
    fun skipUpdate(
        @Suppress("UNUSED_PARAMETER") current: RecordsImportModels.SimilarKanjiWritingRepair?,
        nowMillis: Long,
    ): FinishUpdate {
        return FinishUpdate("", nowMillis, STATUS_SKIPPED, nowMillis, null, null)
    }

    class FinishUpdate(
        private val activeToken: String?,
        private val updatedAtMillis: Long,
        private val status: String?,
        private val completedAtMillis: Long?,
        private val attempts: Int?,
        private val dueAtMillis: Long?,
    ) {
        fun activeToken(): String? = activeToken

        fun updatedAtMillis(): Long = updatedAtMillis

        fun status(): String? = status

        fun completedAtMillis(): Long? = completedAtMillis

        fun attempts(): Int? = attempts

        fun dueAtMillis(): Long? = dueAtMillis

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is FinishUpdate) {
                return false
            }
            return activeToken == other.activeToken &&
                updatedAtMillis == other.updatedAtMillis &&
                status == other.status &&
                completedAtMillis == other.completedAtMillis &&
                attempts == other.attempts &&
                dueAtMillis == other.dueAtMillis
        }

        override fun hashCode(): Int {
            var result = activeToken?.hashCode() ?: 0
            result = 31 * result + updatedAtMillis.hashCode()
            result = 31 * result + (status?.hashCode() ?: 0)
            result = 31 * result + (completedAtMillis?.hashCode() ?: 0)
            result = 31 * result + (attempts ?: 0)
            result = 31 * result + (dueAtMillis?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "FinishUpdate[" +
                "activeToken=$activeToken, " +
                "updatedAtMillis=$updatedAtMillis, " +
                "status=$status, " +
                "completedAtMillis=$completedAtMillis, " +
                "attempts=$attempts, " +
                "dueAtMillis=$dueAtMillis]"
        }
    }

    class RepairDraft(
        private val targetKanji: String?,
        private val repairKanji: String?,
        private val choiceSignature: String?,
        private val wrongSelection: String?,
        private val promptMeaning: String?,
        private val status: String?,
        private val dueAtMillis: Long,
        private val activeToken: String?,
        private val attempts: Int,
        private val createdAtMillis: Long,
        private val updatedAtMillis: Long,
        private val completedAtMillis: Long,
    ) {
        fun targetKanji(): String? = targetKanji

        fun repairKanji(): String? = repairKanji

        fun choiceSignature(): String? = choiceSignature

        fun wrongSelection(): String? = wrongSelection

        fun promptMeaning(): String? = promptMeaning

        fun status(): String? = status

        fun dueAtMillis(): Long = dueAtMillis

        fun activeToken(): String? = activeToken

        fun attempts(): Int = attempts

        fun createdAtMillis(): Long = createdAtMillis

        fun updatedAtMillis(): Long = updatedAtMillis

        fun completedAtMillis(): Long = completedAtMillis

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is RepairDraft) {
                return false
            }
            return targetKanji == other.targetKanji &&
                repairKanji == other.repairKanji &&
                choiceSignature == other.choiceSignature &&
                wrongSelection == other.wrongSelection &&
                promptMeaning == other.promptMeaning &&
                status == other.status &&
                dueAtMillis == other.dueAtMillis &&
                activeToken == other.activeToken &&
                attempts == other.attempts &&
                createdAtMillis == other.createdAtMillis &&
                updatedAtMillis == other.updatedAtMillis &&
                completedAtMillis == other.completedAtMillis
        }

        override fun hashCode(): Int {
            var result = targetKanji?.hashCode() ?: 0
            result = 31 * result + (repairKanji?.hashCode() ?: 0)
            result = 31 * result + (choiceSignature?.hashCode() ?: 0)
            result = 31 * result + (wrongSelection?.hashCode() ?: 0)
            result = 31 * result + (promptMeaning?.hashCode() ?: 0)
            result = 31 * result + (status?.hashCode() ?: 0)
            result = 31 * result + dueAtMillis.hashCode()
            result = 31 * result + (activeToken?.hashCode() ?: 0)
            result = 31 * result + attempts
            result = 31 * result + createdAtMillis.hashCode()
            result = 31 * result + updatedAtMillis.hashCode()
            result = 31 * result + completedAtMillis.hashCode()
            return result
        }

        override fun toString(): String {
            return "RepairDraft[" +
                "targetKanji=$targetKanji, " +
                "repairKanji=$repairKanji, " +
                "choiceSignature=$choiceSignature, " +
                "wrongSelection=$wrongSelection, " +
                "promptMeaning=$promptMeaning, " +
                "status=$status, " +
                "dueAtMillis=$dueAtMillis, " +
                "activeToken=$activeToken, " +
                "attempts=$attempts, " +
                "createdAtMillis=$createdAtMillis, " +
                "updatedAtMillis=$updatedAtMillis, " +
                "completedAtMillis=$completedAtMillis]"
        }
    }
}

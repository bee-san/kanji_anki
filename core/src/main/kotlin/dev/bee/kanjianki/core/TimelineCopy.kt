package dev.bee.kanjianki.core

import java.util.Locale

object TimelineCopy {
    const val EVENT_MANUAL_OVERRIDE = "manual_override"
    const val EVENT_REVIEW_FAILED = "review_failed"
    const val EVENT_REVIEW_PASSED = "review_passed"
    const val EVENT_REOPENED = "reopened"
    const val EVENT_REPAIR_TAGGED = "repair_tagged"

    private const val JAPANESE_LANGUAGE = "ja"
    private const val RATING_AGAIN = "again"

    @JvmStatic
    fun statusText(timeline: RecordsStudyModels.KanjiRecoveryTimeline, nowMillis: Long): String {
        val item = timeline.currentStudyItem
        if (item == null) {
            return localizedText("No active repair", "修復なし")
        }
        if (StudyLadderRules.STATE_RETIRED == item.state) {
            return localizedText("Retired by Anki support", "Ankiの支えで修了")
        }
        if (item.dueAtMillis > nowMillis) {
            return localizedText("Resting until review", "復習まで休止中")
        }
        return localizedText("Active repair", "修復中")
    }

    @JvmStatic
    fun statusTone(timeline: RecordsStudyModels.KanjiRecoveryTimeline, nowMillis: Long): Tone {
        val item = timeline.currentStudyItem
        if (item == null) {
            return Tone.NEUTRAL
        }
        if (StudyLadderRules.STATE_RETIRED == item.state) {
            return Tone.POSITIVE
        }
        if (item.dueAtMillis > nowMillis) {
            return Tone.NEUTRAL
        }
        return Tone.WARNING
    }

    @JvmStatic
    fun eventTone(eventType: String): Tone {
        if (EVENT_REVIEW_FAILED == eventType || "support_dropped" == eventType || EVENT_REOPENED == eventType) {
            return Tone.WARNING
        }
        if (
            EVENT_REVIEW_PASSED == eventType ||
            "support_improved" == eventType ||
            StudyLadderRules.STATE_RETIRED == eventType ||
            EVENT_REPAIR_TAGGED == eventType
        ) {
            return Tone.POSITIVE
        }
        return Tone.NEUTRAL
    }

    @JvmStatic
    fun sourceLine(event: RecordsImportModels.KanjiTimelineEvent): String {
        if (event.sourceExpression.isEmpty()) {
            return ""
        }
        val prefix = localizedText("Source: ", "出典: ")
        if (event.sourceReading.isEmpty()) {
            return prefix + event.sourceExpression
        }
        return prefix + event.sourceExpression + "  " + event.sourceReading
    }

    @JvmStatic
    fun suspendedImportedTitle(): String = localizedText("Imported from suspended Anki", "保留中のAnkiからインポート")

    @JvmStatic
    fun suspendedImportedDetail(): String = localizedText(
        "Kani recovered this kanji from a suspended AnkiDroid card.",
        "KaniはAnkiDroidの保留カードからこの漢字を復旧しました。",
    )

    @JvmStatic
    fun firstSeenTitle(): String = localizedText("Kani started watching", "Kaniが見守り開始")

    @JvmStatic
    fun firstSeenAnkiEvidenceDetail(): String = localizedText(
        "This kanji entered Kani from local AnkiDroid evidence.",
        "この漢字はローカルAnkiDroidの証拠からKaniに入りました。",
    )

    @JvmStatic
    fun firstSeenHistoricalStudyDetail(): String = localizedText(
        "This kanji has historical Kani study state.",
        "この漢字には過去のKani学習状態があります。",
    )

    @JvmStatic
    fun weakSupportSeenTitle(): String = localizedText("Weak support seen", "弱いサポートを検出")

    @JvmStatic
    fun retiredByAnkiSupportTitle(): String = localizedText("Retired by Anki support", "Ankiの支えで修了")

    @JvmStatic
    fun repairTaggedTitle(): String = localizedText("Marked repaired in AnkiDroid", "AnkiDroidで修復済みに設定")

    @JvmStatic
    fun repairTaggedDetail(): String = localizedText(
        "Kani added tag:kani_repaired; use the AnkiDroid card browser to unsuspend when ready.",
        "Kaniがtag:kani_repairedを追加しました。準備ができたらAnkiDroidのカードブラウザで停止を解除できます。",
    )

    @JvmStatic
    fun historicalRetiredDetail(): String = localizedText(
        "Kani had already retired this repair before timeline tracking was added.",
        "タイムライン記録が追加される前に、Kaniはすでにこの修復を完了していました。",
    )

    @JvmStatic
    fun supportImprovedTitle(): String = localizedText("Anki support improved", "Ankiサポートが改善")

    @JvmStatic
    fun supportImprovedDetail(previous: Int, current: Int): String {
        return if (isJapaneseLocale()) {
            "成熟サポートが" + previous + "から" + current + "に増えました。"
        } else {
            "Mature support rose from " + previous + " to " + current + "."
        }
    }

    @JvmStatic
    fun supportDroppedTitle(): String = localizedText("Anki support dropped", "Ankiサポートが低下")

    @JvmStatic
    fun supportDroppedDetail(previous: Int, current: Int): String {
        return if (isJapaneseLocale()) {
            "成熟サポートが" + previous + "から" + current + "に減りました。"
        } else {
            "Mature support fell from " + previous + " to " + current + "."
        }
    }

    @JvmStatic
    fun repairReopenedTitle(): String = localizedText("Repair reopened", "修復を再開")

    @JvmStatic
    fun studyStateDetail(retired: Boolean, matureSupportCount: Int?, target: Int): String {
        if (retired) {
            return if (matureSupportCount == null) {
                localizedText(
                    "No weak Anki evidence remained after sync, so Kani retired this repair.",
                    "同期後に弱いAnki証拠が残っていなかったため、この修復を完了しました。",
                )
            } else {
                supportDetail("Mature Anki support met the target", matureSupportCount, target)
            }
        }
        return if (matureSupportCount == null) {
            localizedText(
                "Kani reopened this kanji after sync found weak evidence again.",
                "同期で弱い証拠が再び見つかったため、この漢字の修復を再開しました。",
            )
        } else {
            supportDetail("Mature Anki support fell below target", matureSupportCount, target)
        }
    }

    @JvmStatic
    fun reviewEvent(request: RecordsSchedulerModels.ReviewRequest, appliedRating: String?): ReviewEvent {
        val safeRating = appliedRating ?: RATING_AGAIN
        val eventType: String
        val title: String
        if (request.manualOverride) {
            eventType = EVENT_MANUAL_OVERRIDE
            title = localizedText("Manual override", "手動上書き")
        } else if (RATING_AGAIN == safeRating || (request.writingRequired && !request.writingPassed)) {
            eventType = EVENT_REVIEW_FAILED
            title = localizedText("Review missed", "復習ミス")
        } else {
            eventType = EVENT_REVIEW_PASSED
            title = localizedText("Review passed", "復習成功")
        }
        return ReviewEvent(eventType, title, reviewDetail(request, safeRating))
    }

    @JvmStatic
    fun reviewDetail(request: RecordsSchedulerModels.ReviewRequest, appliedRating: String?): String {
        val safeRating = appliedRating ?: RATING_AGAIN
        if (request.manualOverride) {
            return localizedText(
                "Saved as " + safeRating + " after manual confirmation.",
                "手動確認後に「" + ratingText(safeRating) + "」として保存しました。",
            )
        }
        if (RATING_AGAIN == safeRating) {
            return if (request.writingRequired) {
                localizedText(
                    "Writing missed; Kani will show it again.",
                    "書き取りをミスしたため、Kaniがもう一度出題します。",
                )
            } else {
                localizedText(
                    "Recall missed; Kani will show it again.",
                    "思い出せなかったため、Kaniがもう一度出題します。",
                )
            }
        }
        if (request.writingRequired) {
            return if (request.writingPassed) {
                localizedText(
                    "Writing passed and was rated " + safeRating + ".",
                    "書き取りに成功し、「" + ratingText(safeRating) + "」と評価されました。",
                )
            } else {
                localizedText(
                    "Writing was not passed and was rated " + safeRating + ".",
                    "書き取りは不合格で、「" + ratingText(safeRating) + "」と評価されました。",
                )
            }
        }
        return localizedText(
            "Recall review was rated " + safeRating + ".",
            "思い出し復習は「" + ratingText(safeRating) + "」と評価されました。",
        )
    }

    @JvmStatic
    fun supportDetail(prefix: String, matureSupportCount: Int, target: Int): String {
        return if (isJapaneseLocale()) {
            supportDetailPrefix(prefix) + ": 成熟サポート " + matureSupportCount + " / 目標 " + target + "。"
        } else {
            prefix + ": mature support " + matureSupportCount + " / target " + target + "."
        }
    }

    private fun supportDetailPrefix(prefix: String): String {
        return when (prefix) {
            "Anki evidence still needs repair" -> "Ankiの証拠はまだ修復が必要"
            "Mature Anki support met the target" -> "成熟したAnkiの支えが目標に到達"
            "Mature Anki support fell below target" -> "成熟したAnkiの支えが目標を下回りました"
            else -> prefix
        }
    }

    private fun ratingText(rating: String): String {
        if (!isJapaneseLocale()) {
            return rating
        }
        return when (rating) {
            StudyRatings.AGAIN -> "再挑戦"
            StudyRatings.HARD -> "難しい"
            StudyRatings.GOOD -> "良い"
            StudyRatings.EASY -> "簡単"
            else -> rating
        }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    enum class Tone {
        POSITIVE,
        NEUTRAL,
        WARNING
    }

    class ReviewEvent(
        private val eventTypeValue: String,
        private val titleValue: String,
        private val detailValue: String,
    ) {
        fun eventType(): String = eventTypeValue

        fun title(): String = titleValue

        fun detail(): String = detailValue
    }
}

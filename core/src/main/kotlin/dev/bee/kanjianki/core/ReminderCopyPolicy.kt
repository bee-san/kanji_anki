package dev.bee.kanjianki.core

import java.util.Locale

object ReminderCopyPolicy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun forPlan(request: AdaptiveLoadPlanner.PlanRequest?): ReminderCopy {
        val safeRequest = request ?: return syncCopy()
        val rows = safeRows(safeRequest)
        if (rows.isEmpty()) {
            return syncCopy()
        }
        val plan = AdaptiveLoadPlanner().plan(safeRequest)
        val due = currentDueCount(rows, safeItems(safeRequest), safeRequest.nowMillis())
        val studiedToday = safeRequest.studiedToday?.isNotEmpty() == true
        return when {
            !studiedToday -> streakCopy(maxOf(plan.remaining, due), safeRequest.currentStreakDays)
            due > 0 -> reviewCopy(due)
            plan.remaining > 0 -> focusCopy(plan.remaining)
            else -> caughtUpCopy()
        }
    }

    @JvmStatic
    fun forCounts(focusRemaining: Int, due: Int): ReminderCopy {
        if (focusRemaining > 0) {
            return if (isJapaneseLocale()) {
                ReminderCopy(
                    "Kaniフォーカスの準備ができました",
                    String.format(
                        Locale.ROOT,
                        "%d件のフォーカス漢字が待っています。Kaniを開いて復習しましょう。",
                        focusRemaining,
                    ),
                )
            } else {
                ReminderCopy(
                    "Kani focus is ready",
                    String.format(
                        Locale.ROOT,
                        "%d focus kanji %s waiting. Open Kani to review %s.",
                        focusRemaining,
                        if (focusRemaining == 1) "is" else "are",
                        if (focusRemaining == 1) "it" else "them",
                    ),
                )
            }
        }
        if (due > 0) {
            return if (isJapaneseLocale()) {
                ReminderCopy(
                    "さらに漢字を復習しましょう",
                    String.format(
                        Locale.ROOT,
                        "%d件の漢字が今復習できます。Kaniを開いて復習しましょう。",
                        due,
                    ),
                )
            } else {
                ReminderCopy(
                    "Kani recovery is due",
                    String.format(
                        Locale.ROOT,
                        "%d problem kanji %s due. Open Kani to review %s now.",
                        due,
                        if (due == 1) "is" else "are",
                        if (due == 1) "it" else "them",
                    ),
                )
            }
        }
        return if (isJapaneseLocale()) {
            ReminderCopy(
                "Kaniは今日の分まで完了しています",
                "今は復習対象の漢字はありません。必要ならKaniで追加練習しましょう。",
            )
        } else {
            ReminderCopy("Kani is caught up", "No problem kanji are due. Open Kani for extra practice if you want.")
        }
    }

    private fun focusCopy(focusRemaining: Int): ReminderCopy {
        return forCounts(focusRemaining, 0)
    }

    private fun streakCopy(waiting: Int, currentStreakDays: Int): ReminderCopy {
        return if (isJapaneseLocale()) {
            val streakLabel = if (currentStreakDays > 0) {
                "${currentStreakDays}日連続の記録"
            } else {
                "連続記録"
            }
            val message = buildString {
                if (waiting > 0) {
                    append(
                        String.format(
                            Locale.ROOT,
                            "%d件の漢字が待っています。",
                            waiting,
                        ),
                    )
                }
                append("Kaniを開いて")
                append(streakLabel)
                append("を続けましょう。")
            }
            ReminderCopy("Kaniの連続記録リマインダー", message)
        } else {
            val streakLabel = if (currentStreakDays > 0) {
                "your ${currentStreakDays}-day streak"
            } else {
                "your streak"
            }
            val waitingMessage = if (waiting > 0) {
                String.format(
                    Locale.ROOT,
                    "%d kanji %s waiting. ",
                    waiting,
                    if (waiting == 1) "is" else "are",
                )
            } else {
                ""
            }
            ReminderCopy(
                "Kani streak reminder",
                waitingMessage + "Open Kani to keep $streakLabel alive.",
            )
        }
    }

    private fun caughtUpCopy(): ReminderCopy {
        return if (isJapaneseLocale()) {
            ReminderCopy(
                "Kaniは今日の分まで完了しています",
                "今日はもう学習済みです。もっと漢字が戻ってきたらKaniを開いてください。",
            )
        } else {
            ReminderCopy("Kani is caught up", "You've already studied today. Open Kani later when more kanji come back.")
        }
    }

    @JvmStatic
    fun reviewCopy(due: Int): ReminderCopy {
        return ReminderCopy(
            "You have more Kanji to review",
            String.format(
                Locale.ROOT,
                "%d kanji %s ready now. Open Kani to review %s.",
                due,
                if (due == 1) "is" else "are",
                if (due == 1) "it" else "them",
            ),
        )
    }

    private fun syncCopy(): ReminderCopy {
        return if (isJapaneseLocale()) {
            ReminderCopy("Kaniを同期", "Kaniを開いて同期してください。")
        } else {
            ReminderCopy("Sync Kani", "Open Kani and tap Sync.")
        }
    }

    @JvmStatic
    fun notificationChannelName(): String {
        return localizedText("Study reminders", "Kaniの学習リマインダー")
    }

    @JvmStatic
    fun notificationChannelDescription(): String {
        return localizedText("Friendly Kani review reminders.", "Kaniのやさしい学習リマインダー。")
    }

    private fun currentDueCount(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): Int {
        return BridgeScheduler().dueCount(items, rows, now)
    }

    private fun safeRows(request: AdaptiveLoadPlanner.PlanRequest): List<RecordsImportModels.DashboardRow> {
        return request.rows() ?: emptyList()
    }

    private fun safeItems(request: AdaptiveLoadPlanner.PlanRequest): List<RecordsStudyModels.StudyItem> {
        return request.items() ?: emptyList()
    }

    class ReminderCopy(
        @JvmField val title: String?,
        @JvmField val message: String?,
    )

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}

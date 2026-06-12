package dev.bee.kanjianki.core

import kotlin.math.ceil
import kotlin.math.min


data class DailyStudyPlan(
    val dateLocalDay: Long,
    val dueNow: Int,
    val dueLater: Int,
    val newProblemKanjiAvailable: Int,
    val streakStatus: StreakStatus,
    val estimatedMinutes: Int,
    val recommendedAction: RecommendedAction,
    val nextUsefulReminderAtMillis: Long,
    val dueLookahead: DueLookaheadWindow,
    val syncStatus: SyncStatus,
    val reasons: List<String>,
)

data class DueLookaheadWindow(
    val dueNow: Int,
    val dueSoon: Int,
    val nextClusterAtMillis: Long,
    val clusterSize: Int,
    val recommendedReminderAtMillis: Long,
)

enum class StreakStatus {
    SAFE,
    NEEDS_ONE_REVIEW,
    NOT_STARTED,
    NO_STREAK_ACTIVE,
}

enum class RecommendedAction {
    STUDY_NOW,
    STUDY_ONCE_FOR_STREAK,
    WAIT_UNTIL_LATER,
    SYNC_FIRST,
    NOTHING_USEFUL_NOW,
}

enum class SyncStatus {
    CURRENT,
    SYNC_NEEDED_TO_JUDGE_PROGRESS,
    NO_MANUAL_SYNC_YET,
    UNKNOWN,
}

data class DailyStudyPlanRequest(
    val nowMillis: Long = 0L,
    val dueAtMillis: List<Long> = emptyList(),
    val studiedToday: Boolean? = null,
    val streak: StudyStreakPolicy.Streak? = null,
    val newProblemKanjiAvailable: Int = 0,
    val lastSuccessfulSyncAtMillis: Long? = null,
    val syncFreshnessMillis: Long? = 24L * 60L * 60L * 1000L,
    val dueLaterLookaheadMillis: Long = 0L,
    val estimatedSecondsPerItem: Int = 30,
)

object DailyStudyPlanPolicy {
    private const val DEFAULT_SECONDS_PER_ITEM = 30

    @JvmStatic
    fun plan(request: DailyStudyPlanRequest?): DailyStudyPlan {
        val safeRequest = request ?: DailyStudyPlanRequest()
        val nowMillis = safeRequest.nowMillis.coerceAtLeast(0L)
        val dueLaterCutoffMillis = dueLaterCutoffMillis(nowMillis, safeRequest.dueLaterLookaheadMillis)
        val dueTimes = safeRequest.dueAtMillis.filter { it > 0L }
        val dueNow = dueTimes.count { it <= nowMillis }
        val dueLaterTimes = dueTimes.filter { it > nowMillis && it < dueLaterCutoffMillis }
        val dueLater = dueLaterTimes.size
        val newProblemKanjiAvailable = safeRequest.newProblemKanjiAvailable.coerceAtLeast(0)
        val streak = safeRequest.streak
        val studiedToday = safeRequest.studiedToday ?: streak?.studiedToday ?: false
        val currentDays = streak?.currentDays ?: 0
        val bestDays = streak?.bestDays ?: 0
        val hasUsefulEvidence = dueNow > 0 || dueLater > 0 || newProblemKanjiAvailable > 0 || studiedToday || currentDays > 0 || bestDays > 0
        val syncStatus = syncStatus(nowMillis, safeRequest.lastSuccessfulSyncAtMillis, safeRequest.syncFreshnessMillis, hasUsefulEvidence)
        val recommendedAction = recommendedAction(
            dueNow = dueNow,
            dueLater = dueLater,
            newProblemKanjiAvailable = newProblemKanjiAvailable,
            studiedToday = studiedToday,
            currentDays = currentDays,
            bestDays = bestDays,
            syncStatus = syncStatus,
            hasUsefulEvidence = hasUsefulEvidence,
        )
        val nextUsefulReminderAtMillis = nextUsefulReminderAtMillis(
            nowMillis = nowMillis,
            recommendedAction = recommendedAction,
            dueLaterTimes = dueLaterTimes,
        )
        val estimatedMinutes = estimateMinutes(
            dueNow = dueNow,
            newProblemKanjiAvailable = newProblemKanjiAvailable,
            secondsPerItem = safeRequest.estimatedSecondsPerItem,
        )
        val reasons = reasons(
            action = recommendedAction,
            dueNow = dueNow,
            dueLater = dueLater,
            newProblemKanjiAvailable = newProblemKanjiAvailable,
            syncStatus = syncStatus,
        )
        return DailyStudyPlan(
            dateLocalDay = LocalDayPolicy.localDayStart(nowMillis),
            dueNow = dueNow,
            dueLater = dueLater,
            newProblemKanjiAvailable = newProblemKanjiAvailable,
            streakStatus = streakStatus(streak, studiedToday),
            estimatedMinutes = estimatedMinutes,
            recommendedAction = recommendedAction,
            nextUsefulReminderAtMillis = nextUsefulReminderAtMillis,
            dueLookahead = DueLookaheadWindow(
                dueNow = dueNow,
                dueSoon = dueLater,
                nextClusterAtMillis = dueLaterTimes.minOrNull() ?: 0L,
                clusterSize = dueLater,
                recommendedReminderAtMillis = nextUsefulReminderAtMillis,
            ),
            syncStatus = syncStatus,
            reasons = reasons,
        )
    }

    private fun streakStatus(streak: StudyStreakPolicy.Streak?, studiedToday: Boolean): StreakStatus {
        val currentDays = streak?.currentDays ?: 0
        val bestDays = streak?.bestDays ?: 0
        return when {
            currentDays > 0 && studiedToday -> StreakStatus.SAFE
            currentDays > 0 -> StreakStatus.NEEDS_ONE_REVIEW
            bestDays > 0 -> StreakStatus.NO_STREAK_ACTIVE
            else -> StreakStatus.NOT_STARTED
        }
    }

    private fun recommendedAction(
        dueNow: Int,
        dueLater: Int,
        newProblemKanjiAvailable: Int,
        studiedToday: Boolean,
        currentDays: Int,
        bestDays: Int,
        syncStatus: SyncStatus,
        hasUsefulEvidence: Boolean,
    ): RecommendedAction {
        val shouldSyncFirst = syncStatus == SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS || (syncStatus == SyncStatus.UNKNOWN && !hasUsefulEvidence)
        return when {
            shouldSyncFirst -> RecommendedAction.SYNC_FIRST
            dueNow > 0 || newProblemKanjiAvailable > 0 -> RecommendedAction.STUDY_NOW
            currentDays > 0 && !studiedToday -> RecommendedAction.STUDY_ONCE_FOR_STREAK
            dueLater > 0 -> RecommendedAction.WAIT_UNTIL_LATER
            studiedToday -> RecommendedAction.NOTHING_USEFUL_NOW
            bestDays > 0 -> RecommendedAction.NOTHING_USEFUL_NOW
            else -> RecommendedAction.NOTHING_USEFUL_NOW
        }
    }

    private fun syncStatus(
        nowMillis: Long,
        lastSuccessfulSyncAtMillis: Long?,
        syncFreshnessMillis: Long?,
        hasUsefulEvidence: Boolean,
    ): SyncStatus {
        val lastSync = lastSuccessfulSyncAtMillis?.takeIf { it > 0L }
        if (lastSync == null) {
            return if (hasUsefulEvidence) SyncStatus.NO_MANUAL_SYNC_YET else SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS
        }
        val freshnessMillis = syncFreshnessMillis?.takeIf { it > 0L } ?: return SyncStatus.UNKNOWN
        return if (nowMillis - lastSync <= freshnessMillis) {
            SyncStatus.CURRENT
        } else {
            SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS
        }
    }

    private fun nextUsefulReminderAtMillis(
        nowMillis: Long,
        recommendedAction: RecommendedAction,
        dueLaterTimes: List<Long>,
    ): Long {
        return when (recommendedAction) {
            RecommendedAction.STUDY_NOW,
            RecommendedAction.STUDY_ONCE_FOR_STREAK,
            RecommendedAction.SYNC_FIRST -> nowMillis
            RecommendedAction.WAIT_UNTIL_LATER -> dueLaterTimes.minOrNull() ?: 0L
            RecommendedAction.NOTHING_USEFUL_NOW -> 0L
        }
    }

    private fun estimateMinutes(dueNow: Int, newProblemKanjiAvailable: Int, secondsPerItem: Int): Int {
        val totalLoad = (dueNow + newProblemKanjiAvailable).coerceAtLeast(0)
        if (totalLoad <= 0) {
            return 0
        }
        val safeSecondsPerItem = if (secondsPerItem > 0) secondsPerItem else DEFAULT_SECONDS_PER_ITEM
        return maxOf(1, ceil(totalLoad * safeSecondsPerItem / 60.0).toInt())
    }

    private fun reasons(
        action: RecommendedAction,
        dueNow: Int,
        dueLater: Int,
        newProblemKanjiAvailable: Int,
        syncStatus: SyncStatus,
    ): List<String> {
        val reasons = mutableListOf<String>()
        when (action) {
            RecommendedAction.STUDY_NOW -> {
                if (dueNow > 0) {
                    reasons += countPhrase(dueNow, "due now")
                }
                if (newProblemKanjiAvailable > 0) {
                    reasons += countPhrase(newProblemKanjiAvailable, "new problem kanji available")
                }
            }
            RecommendedAction.STUDY_ONCE_FOR_STREAK -> reasons += "streak needs one review"
            RecommendedAction.WAIT_UNTIL_LATER -> {
                if (dueLater > 0) {
                    reasons += countPhrase(dueLater, "learning repeat later", "learning repeats later")
                }
            }
            RecommendedAction.SYNC_FIRST -> reasons += syncReason(syncStatus)
            RecommendedAction.NOTHING_USEFUL_NOW -> reasons += "nothing useful now"
        }
        if (reasons.isEmpty()) {
            reasons += "nothing useful now"
        }
        return reasons.toList()
    }

    private fun syncReason(syncStatus: SyncStatus): String {
        return when (syncStatus) {
            SyncStatus.CURRENT -> "sync is current"
            SyncStatus.NO_MANUAL_SYNC_YET -> "no manual sync yet"
            SyncStatus.UNKNOWN -> "sync freshness unknown"
            SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS -> "sync needed before Kani can judge progress"
        }
    }

    private fun countPhrase(count: Int, singular: String, plural: String = singular): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }

    private fun dueLaterCutoffMillis(nowMillis: Long, dueLaterLookaheadMillis: Long): Long {
        val localDayEndMillis = LocalDayPolicy.nextLocalDayStart(nowMillis)
        val lookaheadMillis = dueLaterLookaheadMillis.coerceAtLeast(0L)
        if (lookaheadMillis <= 0L) {
            return localDayEndMillis
        }
        val lookaheadEndMillis = nowMillis + lookaheadMillis
        return min(localDayEndMillis, lookaheadEndMillis)
    }
}

package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import kotlin.math.abs

class SchedulerTimelineSnapshot(
    kanji: String?,
    taskType: String?,
    @JvmField val rung: RecordsBase.LadderRung,
    @JvmField val phase: RecordsBase.SchedulerPhase,
    state: String?,
    @JvmField val dueAtMillis: Long,
    @JvmField val dueOffsetMillis: Long,
    @JvmField val matureIntervalDays: Int,
    @JvmField val realPassStreak: Int,
    @JvmField val realAgainStreak: Int,
    suppressedByTaskType: String?,
) {
    @JvmField val kanji: String = kanji ?: ""
    @JvmField val taskType: String = taskType ?: ""
    @JvmField val state: String = state ?: ""
    @JvmField val suppressedByTaskType: String = suppressedByTaskType ?: ""
}

class SchedulerTimelineEvent internal constructor(
    kind: String?,
    @JvmField val offsetMillis: Long,
    @JvmField val trace: SchedulerDecisionTrace,
    @JvmField val snapshot: SchedulerTimelineSnapshot?,
    @JvmField val beforeSnapshot: SchedulerTimelineSnapshot?,
) {
    @JvmField val kind: String = kind ?: ""
}

class SchedulerTimelineSimulator(
    private val scheduler: BridgeScheduler,
    rows: List<RecordsImportModels.DashboardRow>,
    startingItems: List<RecordsStudyModels.StudyItem>,
    private val startMillis: Long,
    private val settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults(),
    private val parameters: RecordsSchedulerModels.SchedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
    private val learningSettings: RecordsSchedulerModels.LearningStepSettings = RecordsSchedulerModels.LearningStepSettings.defaults(),
    private val ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults(),
) {
    private val rows: List<RecordsImportModels.DashboardRow> = Collections.unmodifiableList(ArrayList(rows))
    private val consumedTokens: MutableSet<String> = HashSet()
    private val events: MutableList<SchedulerTimelineEvent> = ArrayList()
    private var items: List<RecordsStudyModels.StudyItem> = ArrayList(startingItems)
    private var nowMillis: Long = startMillis
    private var activeSession: RecordsSchedulerModels.StudySession? = null

    fun seedQueue(): SchedulerTimelineEvent {
        val beforeKeys = items.map { itemKey(it) }.toHashSet()
        items = scheduler.seedQueue(rows, items, settings, nowMillis, startMillis, ladder)
        val admitted = items
            .filter { !beforeKeys.contains(itemKey(it)) }
            .sortedWith(compareBy({ it.kanji }, { StudyTaskTypes.forRung(it.rung) }))
        val first = admitted.firstOrNull()
        val trace = SchedulerDecisionTrace(
            "seed",
            nowMillis,
            first?.let { traceCandidate(it, listOf("new_admitted")) },
            admitted.map { traceCandidate(it, listOf("new_admitted")) },
            emptyList(),
            null,
            emptyList(),
        )
        val event = SchedulerTimelineEvent("seed", nowMillis - startMillis, trace, first?.let { snapshot(it) }, null)
        events.add(event)
        return event
    }

    fun nextSession(): SchedulerTimelineEvent {
        val trace = scheduler.debugTraceNextSession(items, rows, nowMillis, 0L, null, settings, ladder)
        activeSession = scheduler.nextSession(items, rows, nowMillis, 0L, null, settings, ladder)
        val event = SchedulerTimelineEvent("next", nowMillis - startMillis, trace, activeSession?.item?.let { snapshot(it) }, null)
        events.add(event)
        return event
    }

    fun answer(rating: String): SchedulerTimelineEvent {
        val session = activeSession ?: throw IllegalStateException("Call nextSession() before answer().")
        val before = session.item ?: throw IllegalStateException("The active session has no study item.")
        val request = RecordsSchedulerModels.ReviewRequest(
            before.kanji,
            session.token,
            rating,
            session.writingRequired,
            !session.writingRequired,
            !session.writingRequired,
            0,
        )
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(before, request, consumedTokens, nowMillis)
                .parameters(parameters)
                .settings(settings)
                .learningSettings(learningSettings)
                .ladder(ladder)
                .build()
        )
        items = replaceReviewedItem(items, before, traced.result.item)
        activeSession = null
        val event = SchedulerTimelineEvent("answer", nowMillis - startMillis, traced.trace, snapshot(traced.result.item), snapshot(before))
        events.add(event)
        return event
    }

    fun advanceBy(millis: Long): SchedulerTimelineEvent {
        nowMillis += millis.coerceAtLeast(0L)
        val trace = SchedulerDecisionTrace("advance", nowMillis, null, emptyList(), emptyList(), null, emptyList())
        val event = SchedulerTimelineEvent("advance", nowMillis - startMillis, trace, null, null)
        events.add(event)
        return event
    }

    fun advanceTo(targetMillis: Long): SchedulerTimelineEvent {
        nowMillis = maxOf(nowMillis, targetMillis)
        val trace = SchedulerDecisionTrace("advance", nowMillis, null, emptyList(), emptyList(), null, emptyList())
        val event = SchedulerTimelineEvent("advance", nowMillis - startMillis, trace, null, null)
        events.add(event)
        return event
    }

    fun currentItems(): List<RecordsStudyModels.StudyItem> {
        return Collections.unmodifiableList(ArrayList(items))
    }

    fun events(): List<SchedulerTimelineEvent> {
        return Collections.unmodifiableList(ArrayList(events))
    }

    fun renderText(): String {
        return events.flatMap { renderEvent(it) }.joinToString("\n") + "\n"
    }

    private fun renderEvent(event: SchedulerTimelineEvent): List<String> {
        return when (event.kind) {
            "seed" -> renderSeed(event)
            "next" -> renderNext(event)
            "answer" -> renderAnswer(event)
            "advance" -> listOf("${offsetText(event.offsetMillis)} advance now=${offsetText(event.offsetMillis)}")
            else -> listOf("${offsetText(event.offsetMillis)} ${event.kind}")
        }
    }

    private fun renderSeed(event: SchedulerTimelineEvent): List<String> {
        val selected = event.snapshot ?: return listOf("${offsetText(event.offsetMillis)} seed admitted=none")
        return listOf(
            "${offsetText(event.offsetMillis)} seed admitted=${selected.kanji} " +
                "task=${selected.taskType} rung=${selected.rung.name} phase=${selected.phase.name} " +
                "due=${absoluteOffsetText(selected.dueAtMillis)} reasons=[new_admitted]"
        )
    }

    private fun renderNext(event: SchedulerTimelineEvent): List<String> {
        val out = ArrayList<String>()
        val selected = event.trace.selected
        if (selected == null) {
            out.add("${offsetText(event.offsetMillis)} next selected=none")
        } else {
            out.add(
                "${offsetText(event.offsetMillis)} next selected=${selected.kanji} " +
                    "task=${selected.taskType} rung=${selected.rung.name} phase=${selected.phase.name} " +
                    "due=${absoluteOffsetText(selected.dueAtMillis)} reasons=${reasonText(selected.reasonCodes)}"
            )
        }
        for (candidate in event.trace.skipped) {
            out.add(
                "${offsetText(event.offsetMillis)} hidden selected_family=${selected?.kanji ?: candidate.familyKey} " +
                    "task=${candidate.taskType} rung=${candidate.rung.name} phase=${candidate.phase.name} " +
                    "due=${absoluteOffsetText(candidate.dueAtMillis)} reasons=${reasonText(candidate.reasonCodes)}"
            )
        }
        return out
    }

    private fun renderAnswer(event: SchedulerTimelineEvent): List<String> {
        val before = event.beforeSnapshot ?: return listOf("${offsetText(event.offsetMillis)} answer rating=unknown")
        val after = event.snapshot ?: return listOf("${offsetText(event.offsetMillis)} answer rating=unknown")
        val transition = event.trace.transition
        val rating = transition?.rating ?: ""
        val reasons = transition?.reasonCodes ?: emptyList()
        return listOf(
            "${offsetText(event.offsetMillis)} answer rating=$rating task=${before.taskType} " +
                "rung=${before.rung.name}->${after.rung.name} phase=${before.phase.name}->${after.phase.name} " +
                "due=${absoluteOffsetText(after.dueAtMillis)} reasons=${reasonText(reasons)} " +
                "fsrs=${fsrsText(event.trace.fsrsCalls)}"
        )
    }

    private fun snapshot(item: RecordsStudyModels.StudyItem): SchedulerTimelineSnapshot {
        return SchedulerTimelineSnapshot(
            item.kanji,
            StudyTaskTypes.forRung(item.rung),
            item.rung,
            item.phase,
            item.state,
            item.dueAtMillis,
            item.dueAtMillis - startMillis,
            item.matureIntervalDays,
            item.realPassStreak,
            item.realAgainStreak,
            item.suppressedByTaskType,
        )
    }

    private fun traceCandidate(item: RecordsStudyModels.StudyItem, reasons: List<String>): SchedulerDecisionTraceCandidate {
        return SchedulerDecisionTraceCandidate(
            item.kanji,
            StudyTaskTypes.forRung(item.rung),
            item.rung,
            item.phase,
            item.dueAtMillis,
            reasons,
            StudyQueueSeeder.familyKey(item),
            rowWeakness(item.kanji),
        )
    }

    private fun rowWeakness(kanji: String): Int {
        return rows.firstOrNull { it.kanji == kanji }?.weaknessScore ?: 0
    }

    private fun replaceReviewedItem(
        current: List<RecordsStudyModels.StudyItem>,
        reviewed: RecordsStudyModels.StudyItem,
        updated: RecordsStudyModels.StudyItem,
    ): List<RecordsStudyModels.StudyItem> {
        val out = ArrayList<RecordsStudyModels.StudyItem>()
        var replaced = false
        for (item in current) {
            if (!replaced && sameTimelineItem(item, reviewed)) {
                out.add(updated)
                replaced = true
            } else {
                out.add(item)
            }
        }
        if (!replaced) {
            out.add(updated)
        }
        return out
    }

    private fun sameTimelineItem(left: RecordsStudyModels.StudyItem, right: RecordsStudyModels.StudyItem): Boolean {
        return left.kanji == right.kanji &&
            left.rung == right.rung &&
            left.phase == right.phase &&
            left.dueAtMillis == right.dueAtMillis &&
            left.answerSignature == right.answerSignature
    }

    private fun itemKey(item: RecordsStudyModels.StudyItem): String {
        return item.kanji + "\u0000" + item.rung.wireName() + "\u0000" + item.phase.wireName() + "\u0000" + item.answerSignature
    }

    private fun absoluteOffsetText(absoluteMillis: Long): String {
        return offsetText(absoluteMillis - startMillis)
    }

    private fun offsetText(offsetMillis: Long): String {
        val prefix = if (offsetMillis < 0) "T-" else "T+"
        val positive = abs(offsetMillis)
        if (positive == 0L) {
            return "T+00:00"
        }
        if (positive % BridgeScheduler.DAY == 0L) {
            return prefix + positive / BridgeScheduler.DAY + "d"
        }
        val totalMinutes = positive / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return prefix + hours.toString().padStart(2, '0') + ":" + minutes.toString().padStart(2, '0')
    }

    private fun reasonText(reasons: List<String>): String {
        return reasons.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    private fun fsrsText(calls: List<SchedulerFsrsCallTrace>): String {
        return calls.joinToString(prefix = "[", postfix = "]") { it.callType + ":" + it.rating + ":" + it.outputIntervalDays }
    }
}

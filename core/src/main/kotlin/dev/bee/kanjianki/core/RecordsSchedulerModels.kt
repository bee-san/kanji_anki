package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.Objects
import java.util.regex.Pattern

private fun Array<out Any?>.toSchedulerArgsArray(): Array<Any?> = Array(size) { index -> this[index] }

abstract class RecordsSchedulerModels protected constructor() : RecordsStudyModels() {
    class StudySession(
        item: StudyItem,
        @JvmField val row: DashboardRow?,
        token: String?,
        taskType: String?,
        @JvmField val writingRequired: Boolean,
        prompt: String?,
    ) {
        @JvmField val item: StudyItem = item
        @JvmField val token: String = nullToEmpty(token)
        @JvmField val taskType: String = nullToEmpty(taskType)
        @JvmField val prompt: String = nullToEmpty(prompt)
    }

    class LearningStepSettings(
        newStepsMinutes: List<Int?>?,
        reviewStepsMinutes: List<Int?>?,
    ) {
        @JvmField val newStepsMinutes: List<Int> = Collections.unmodifiableList(normalizeSteps(newStepsMinutes, defaultNewSteps()))
        @JvmField val reviewStepsMinutes: List<Int> = Collections.unmodifiableList(normalizeSteps(reviewStepsMinutes, defaultReviewSteps()))

        fun newStepsText(): String = formatSteps(newStepsMinutes)

        fun reviewStepsText(): String = formatSteps(reviewStepsMinutes)

        companion object {
            @JvmField
            protected val STEP_SEPARATOR: Pattern = Pattern.compile("[,\\s]+")

            @JvmStatic
            fun defaults(): LearningStepSettings = LearningStepSettings(defaultNewSteps(), defaultReviewSteps())

            @JvmStatic
            fun parseSteps(value: String?, fallback: List<Int?>?): List<Int> {
                val parsed = tryParseSteps(value)
                return if (parsed.isEmpty()) normalizeSteps(fallback, defaultNewSteps()) else parsed
            }

            @JvmStatic
            fun tryParseSteps(value: String?): List<Int> {
                if (value == null || value.trim().isEmpty()) {
                    return Collections.emptyList()
                }
                val parsed = ArrayList<Int>()
                for (part in STEP_SEPARATOR.split(value.trim())) {
                    val minutes = parseStepMinutes(part)
                    if (minutes == null || minutes <= 0) {
                        return Collections.emptyList()
                    }
                    parsed.add(minutes)
                }
                return if (parsed.isEmpty()) Collections.emptyList() else normalizeSteps(parsed, defaultNewSteps())
            }

            @JvmStatic
            fun formatSteps(steps: List<Int?>?): String {
                val normalized = normalizeSteps(steps, defaultNewSteps())
                val parts = ArrayList<String>()
                for (minutes in normalized) {
                    if (minutes >= 60 && minutes % 60 == 0) {
                        parts.add((minutes / 60).toString() + "h")
                    } else {
                        parts.add(minutes.toString() + "m")
                    }
                }
                return parts.joinToString(", ")
            }

            @JvmStatic
            fun defaultNewSteps(): List<Int> {
                val out = ArrayList<Int>()
                out.add(1)
                out.add(10)
                return out
            }

            @JvmStatic
            fun defaultReviewSteps(): List<Int> {
                val out = ArrayList<Int>()
                out.add(10)
                return out
            }

            @JvmStatic
            protected fun parseStepMinutes(raw: String): Int? {
                var value = raw.trim().lowercase()
                var multiplier = 1
                if (value.endsWith("m")) {
                    value = value.substring(0, value.length - 1)
                } else if (value.endsWith("h")) {
                    value = value.substring(0, value.length - 1)
                    multiplier = 60
                }
                if (value.isEmpty()) {
                    return null
                }
                return try {
                    Math.multiplyExact(value.toInt(), multiplier)
                } catch (_: ArithmeticException) {
                    null
                } catch (_: NumberFormatException) {
                    null
                }
            }

            @JvmStatic
            protected fun normalizeSteps(steps: List<Int?>?, fallback: List<Int>): List<Int> {
                val out = ArrayList<Int>()
                if (steps != null) {
                    for (step in steps) {
                        if (step == null || step <= 0) {
                            out.clear()
                            break
                        }
                        out.add(step)
                    }
                }
                if (out.isNotEmpty()) {
                    return out
                }
                return ArrayList(fallback)
            }
        }
    }

    class LearningRepeat(
        kanji: String?,
        answerSignature: String?,
        taskType: String?,
        repeatType: String?,
        vararg rest: Any?,
    ) {
        @JvmField val kanji: String = nullToEmpty(kanji)
        @JvmField val answerSignature: String = nullToEmpty(answerSignature)
        @JvmField val taskType: String = nullToEmpty(taskType)
        @JvmField val repeatType: String
        @JvmField val stepIndex: Int
        @JvmField val dueAtMillis: Long
        @JvmField val activeToken: String
        @JvmField val createdAtMillis: Long
        @JvmField val updatedAtMillis: Long

        init {
            val args = rest.toSchedulerArgsArray()
            requireArgCount(CONTEXT_LEARNING_REPEAT, args, 5)
            this.repeatType = if (LEARNING_REPEAT_REVIEW == repeatType) LEARNING_REPEAT_REVIEW else LEARNING_REPEAT_NEW
            this.stepIndex = intArg(args, 0, CONTEXT_LEARNING_REPEAT).coerceAtLeast(0)
            this.dueAtMillis = longArg(args, 1, CONTEXT_LEARNING_REPEAT).coerceAtLeast(0L)
            this.activeToken = nullToEmpty(stringArg(args, 2, CONTEXT_LEARNING_REPEAT))
            this.createdAtMillis = longArg(args, 3, CONTEXT_LEARNING_REPEAT).coerceAtLeast(0L)
            this.updatedAtMillis = longArg(args, 4, CONTEXT_LEARNING_REPEAT).coerceAtLeast(0L)
        }

        fun withToken(token: String?, updatedAtMillis: Long): LearningRepeat {
            return LearningRepeat(kanji, answerSignature, taskType, repeatType, stepIndex, dueAtMillis, token, createdAtMillis, updatedAtMillis)
        }

        fun withStep(stepIndex: Int, dueAtMillis: Long, updatedAtMillis: Long): LearningRepeat {
            return LearningRepeat(kanji, answerSignature, taskType, repeatType, stepIndex, dueAtMillis, "", createdAtMillis, updatedAtMillis)
        }
    }

    class ReviewRequest(
        kanji: String?,
        token: String?,
        @JvmField val rating: String?,
        @JvmField val writingRequired: Boolean,
        @JvmField val writingPassed: Boolean,
        vararg rest: Any?,
    ) {
        @JvmField val kanji: String = nullToEmpty(kanji)
        @JvmField val taskType: String
        @JvmField val token: String = nullToEmpty(token)
        @JvmField val answerSignature: String
        @JvmField val prompt: String
        @JvmField val writingClean: Boolean
        @JvmField val manualOverride: Boolean
        @JvmField val hintsUsed: Int

        init {
            val args = rest.toSchedulerArgsArray()
            requireArgCount(CONTEXT_REVIEW_REQUEST, args, 2, 3, 6)
            if (args.size == 2) {
                writingClean = writingPassed && ("good" == rating || "easy" == rating)
                manualOverride = booleanArg(args, 0, CONTEXT_REVIEW_REQUEST)
                hintsUsed = intArg(args, 1, CONTEXT_REVIEW_REQUEST)
                taskType = ""
                answerSignature = ""
                prompt = ""
            } else {
                writingClean = booleanArg(args, 0, CONTEXT_REVIEW_REQUEST)
                manualOverride = booleanArg(args, 1, CONTEXT_REVIEW_REQUEST)
                hintsUsed = intArg(args, 2, CONTEXT_REVIEW_REQUEST)
                taskType = if (args.size == 3) "" else nullToEmpty(stringArg(args, 3, CONTEXT_REVIEW_REQUEST))
                answerSignature = if (args.size == 3) "" else nullToEmpty(stringArg(args, 4, CONTEXT_REVIEW_REQUEST))
                prompt = if (args.size == 3) "" else nullToEmpty(stringArg(args, 5, CONTEXT_REVIEW_REQUEST))
            }
        }
    }

    class SchedulerParameters {
        @JvmField val targetRetention: Double
        @JvmField val againMultiplier: Double
        @JvmField val hardMultiplier: Double
        @JvmField val goodMultiplier: Double
        @JvmField val easyMultiplier: Double
        @JvmField val lastAdjustedAtMillis: Long
        @JvmField val lastAdjustmentReviewCount: Int
        @JvmField val frequencyRetentionEnabled: Boolean
        @JvmField val frequencyRetentionRanges: String

        constructor(
            targetRetention: Double,
            againMultiplier: Double,
            hardMultiplier: Double,
            goodMultiplier: Double,
            easyMultiplier: Double,
            lastAdjustedAtMillis: Long,
            lastAdjustmentReviewCount: Int,
        ) : this(
            targetRetention,
            IntervalMultipliers(againMultiplier, hardMultiplier, goodMultiplier, easyMultiplier),
            AdjustmentSnapshot(lastAdjustedAtMillis, lastAdjustmentReviewCount),
            FrequencyRetention.defaults(),
        )

        private constructor(
            targetRetention: Double,
            multipliers: IntervalMultipliers,
            adjustment: AdjustmentSnapshot,
            retention: FrequencyRetention,
        ) {
            this.targetRetention = targetRetention
            this.againMultiplier = multipliers.again
            this.hardMultiplier = multipliers.hard
            this.goodMultiplier = multipliers.good
            this.easyMultiplier = multipliers.easy
            this.lastAdjustedAtMillis = adjustment.adjustedAtMillis
            this.lastAdjustmentReviewCount = adjustment.reviewCount
            this.frequencyRetentionEnabled = retention.enabled
            this.frequencyRetentionRanges = nullToEmpty(retention.ranges).trim()
        }

        fun targetRetentionForRank(jitenRank: Int?): Double {
            if (!frequencyRetentionEnabled) {
                return targetRetention
            }
            return try {
                FrequencyRetentionRanges.retentionForRank(frequencyRetentionRanges, jitenRank) ?: targetRetention
            } catch (_: IllegalArgumentException) {
                targetRetention
            }
        }

        fun withTargetRetention(retention: Double): SchedulerParameters {
            return SchedulerParameters(retention, multipliers(), adjustment(), frequencyRetention())
        }

        fun withAdjustment(again: Double, hard: Double, good: Double, easy: Double, adjustedAtMillis: Long, reviewCount: Int): SchedulerParameters {
            return SchedulerParameters(
                targetRetention,
                IntervalMultipliers(
                    clamp(again, 0.25, 0.75),
                    clamp(hard, 1.05, 1.80),
                    clamp(good, 1.35, 3.20),
                    clamp(easy, 2.00, 4.80),
                ),
                AdjustmentSnapshot(adjustedAtMillis, reviewCount),
                frequencyRetention(),
            )
        }

        fun withFrequencyRetention(enabled: Boolean, ranges: String?): SchedulerParameters {
            return SchedulerParameters(targetRetention, multipliers(), adjustment(), FrequencyRetention(enabled, ranges))
        }

        private fun multipliers(): IntervalMultipliers = IntervalMultipliers(againMultiplier, hardMultiplier, goodMultiplier, easyMultiplier)

        private fun adjustment(): AdjustmentSnapshot = AdjustmentSnapshot(lastAdjustedAtMillis, lastAdjustmentReviewCount)

        private fun frequencyRetention(): FrequencyRetention = FrequencyRetention(frequencyRetentionEnabled, frequencyRetentionRanges)

        private class IntervalMultipliers(
            val again: Double,
            val hard: Double,
            val good: Double,
            val easy: Double,
        )

        private class AdjustmentSnapshot(
            val adjustedAtMillis: Long,
            val reviewCount: Int,
        )

        private class FrequencyRetention(
            val enabled: Boolean,
            val ranges: String?,
        ) {
            companion object {
                fun defaults(): FrequencyRetention {
                    return FrequencyRetention(DEFAULT_FREQUENCY_RETENTION_ENABLED, DEFAULT_FREQUENCY_RETENTION_RANGES)
                }
            }
        }

        companion object {
            @JvmStatic
            fun defaults(): SchedulerParameters {
                return SchedulerParameters(0.90, 0.45, 1.20, 2.00, 3.10, 0L, 0)
            }

            @JvmStatic
            protected fun clamp(value: Double, min: Double, max: Double): Double {
                return kotlin.math.max(min, kotlin.math.min(max, value))
            }
        }
    }

    class ReviewStats(
        @JvmField val total: Int,
        @JvmField val again: Int,
        @JvmField val hard: Int,
        @JvmField val good: Int,
        @JvmField val easy: Int,
        @JvmField val writingRequired: Int,
        @JvmField val writingFailed: Int,
    ) {
        fun retentionProxy(): Double {
            if (total == 0) {
                return 1.0
            }
            return (hard + good + easy) / total.toDouble()
        }

        fun writingFailureRate(): Double {
            if (writingRequired == 0) {
                return 0.0
            }
            return writingFailed / writingRequired.toDouble()
        }
    }

    class ReviewResult(
        @JvmField val item: StudyItem,
        appliedRating: String?,
        @JvmField val duplicate: Boolean,
        message: String?,
    ) {
        @JvmField val appliedRating: String = nullToEmpty(appliedRating)
        @JvmField val message: String = nullToEmpty(message)
    }

    class AdaptiveLoadPlan {
        @JvmField val autoMode: Boolean
        @JvmField val workloadPercent: Int
        @JvmField val target: Int
        @JvmField val remaining: Int
        @JvmField val focusKanji: List<String>
        @JvmField val newAdmissionLimit: Int
        @JvmField val allKanjiMode: Boolean
        @JvmField val status: String

        constructor(
            workloadPercent: Int,
            target: Int,
            remaining: Int,
            focusKanji: List<String>,
            newAdmissionLimit: Int,
            allKanjiMode: Boolean,
            status: String?,
        ) : this(false, workloadPercent, target, remaining, focusKanji, newAdmissionLimit, allKanjiMode, status)

        constructor(autoMode: Boolean, workloadPercent: Int, target: Int, remaining: Int, focusKanji: List<String>, vararg rest: Any?) {
            val args = rest.toSchedulerArgsArray()
            requireArgCount(CONTEXT_ADAPTIVE_LOAD_PLAN, args, 3)
            this.autoMode = autoMode
            this.workloadPercent = workloadPercent
            this.target = target
            this.remaining = remaining
            this.focusKanji = Collections.unmodifiableList(ArrayList(focusKanji))
            this.newAdmissionLimit = intArg(args, 0, CONTEXT_ADAPTIVE_LOAD_PLAN)
            this.allKanjiMode = booleanArg(args, 1, CONTEXT_ADAPTIVE_LOAD_PLAN)
            this.status = nullToEmpty(stringArg(args, 2, CONTEXT_ADAPTIVE_LOAD_PLAN))
        }

        fun focusComplete(): Boolean = !allKanjiMode && target > 0 && remaining <= 0
    }

    class ReleaseAsset(
        name: String?,
        downloadUrl: String?,
    ) {
        @JvmField val name: String = nullToEmpty(name)
        @JvmField val downloadUrl: String = nullToEmpty(downloadUrl)
    }

    class ReleaseInfo(
        @JvmField val tagName: String?,
        @JvmField val htmlUrl: String?,
        assets: List<ReleaseAsset>,
    ) {
        @JvmField val assets: List<ReleaseAsset> = Collections.unmodifiableList(ArrayList(assets))

        fun apkAsset(): ReleaseAsset? {
            for (asset in assets) {
                if (asset.name.endsWith(".apk")) {
                    return asset
                }
            }
            return null
        }

        fun checksumAssetFor(apkName: String?): ReleaseAsset? {
            for (asset in assets) {
                if (Objects.equals(asset.name, "$apkName.sha256")) {
                    return asset
                }
            }
            return null
        }
    }
}

package dev.bee.kanjianki.core

import dev.bee.kanjianki.syncdomain.ImportRuleMatch
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

abstract class RecordsBase protected constructor() {
    /**
     * Ladder rungs that a study item can be on. User settings own the active
     * order; enum order is retained for storage compatibility. The ladder is a
     * scaffolding gradient, not a difficulty gradient: the bottom rungs offer
     * the most support and deliberate practice (guided handwriting), the top
     * rungs the least (raw contextual reading). Demotion adds scaffolding;
     * promotion removes it (design properties P9/P10, see
     * docs/ladder-and-srs-system.md §13). New cards start near [KANJI_MEANING].
     * The [SIMILAR_KANJI] rung is included only when `hasSimilarKanji` is true
     * for the card.
     */
    enum class LadderRung(private val wireNameValue: String) {
        WRITE_KANJI("write_kanji"),
        TYPE_MEANING("type_meaning"),
        SIMILAR_KANJI("similar_kanji"),
        MEANING_KANJI("meaning_kanji"),
        KANJI_MEANING("kanji_meaning"),
        FONT_MEANING("font_meaning"),
        WORD_READING("word_reading"),

        // New conditional rungs (Goals 78-80). Enum declaration order is
        // storage-compatibility-only (serialization is by wire name); ladder
        // position is controlled exclusively via defaultsOrder(). Appended at
        // the end so stored ordinals never shift.
        KANJI_READING("kanji_reading"),
        READING_KANJI("reading_kanji"),
        SENTENCE_READING("sentence_reading");

        fun wireName(): String = wireNameValue

        companion object {
            @JvmStatic
            fun startingRung(): LadderRung = KANJI_MEANING

            @JvmStatic
            fun fromWireName(name: String?): LadderRung {
                if (name == null) {
                    return KANJI_MEANING
                }
                for (rung in entries) {
                    if (rung.wireNameValue == name) {
                        return rung
                    }
                }
                LOGGER.warning { "LadderRung.fromWireName: unknown wire name '$name', defaulting to KANJI_MEANING" }
                return KANJI_MEANING
            }
        }
    }

    /**
     * Immutable snapshot of which conditional rungs a specific study item can
     * currently support. Always-available rungs are available regardless of the
     * flags; each conditional rung consults its own flag. This generalizes the
     * former single `hasSimilarKanji: Boolean` parameter that was threaded
     * through every ladder-movement method so new conditional rungs can be
     * added by extending this value object rather than every signature.
     *
     * [NONE] means "no conditional data" — every conditional rung is treated as
     * unavailable, which is the conservative default used when seeding.
     */
    class RungAvailability private constructor(
        @JvmField val hasSimilarKanji: Boolean,
        @JvmField val hasKanjiReading: Boolean,
        @JvmField val hasReadingKanji: Boolean,
        @JvmField val hasSentenceReading: Boolean,
    ) {
        /**
         * True when [rung] is available for the item this snapshot describes:
         * always-available rungs are unconditionally available; conditional
         * rungs consult their flag.
         */
        fun isAvailable(rung: LadderRung?): Boolean {
            return StudyLadderSettings.alwaysAvailable(rung) || flagFor(rung)
        }

        private fun flagFor(rung: LadderRung?): Boolean {
            return when (rung) {
                LadderRung.SIMILAR_KANJI -> hasSimilarKanji
                LadderRung.KANJI_READING -> hasKanjiReading
                LadderRung.READING_KANJI -> hasReadingKanji
                LadderRung.SENTENCE_READING -> hasSentenceReading
                else -> false
            }
        }

        fun withHasSimilarKanji(value: Boolean): RungAvailability {
            return if (value == hasSimilarKanji) this else of(value, hasKanjiReading, hasReadingKanji, hasSentenceReading)
        }

        fun withHasKanjiReading(value: Boolean): RungAvailability {
            return if (value == hasKanjiReading) this else of(hasSimilarKanji, value, hasReadingKanji, hasSentenceReading)
        }

        fun withHasReadingKanji(value: Boolean): RungAvailability {
            return if (value == hasReadingKanji) this else of(hasSimilarKanji, hasKanjiReading, value, hasSentenceReading)
        }

        fun withHasSentenceReading(value: Boolean): RungAvailability {
            return if (value == hasSentenceReading) this else of(hasSimilarKanji, hasKanjiReading, hasReadingKanji, value)
        }

        companion object {
            @JvmField
            val NONE: RungAvailability = RungAvailability(false, false, false, false)

            @JvmStatic
            fun none(): RungAvailability = NONE

            @JvmStatic
            fun of(hasSimilarKanji: Boolean): RungAvailability = of(hasSimilarKanji, false, false, false)

            @JvmStatic
            fun of(hasSimilarKanji: Boolean, hasKanjiReading: Boolean): RungAvailability =
                of(hasSimilarKanji, hasKanjiReading, false, false)

            @JvmStatic
            fun of(
                hasSimilarKanji: Boolean,
                hasKanjiReading: Boolean,
                hasReadingKanji: Boolean,
            ): RungAvailability = of(hasSimilarKanji, hasKanjiReading, hasReadingKanji, false)

            @JvmStatic
            fun of(
                hasSimilarKanji: Boolean,
                hasKanjiReading: Boolean,
                hasReadingKanji: Boolean,
                hasSentenceReading: Boolean,
            ): RungAvailability {
                return if (!hasSimilarKanji && !hasKanjiReading && !hasReadingKanji && !hasSentenceReading) {
                    NONE
                } else {
                    RungAvailability(hasSimilarKanji, hasKanjiReading, hasReadingKanji, hasSentenceReading)
                }
            }
        }
    }

    class StudyLadderSettings {
        @JvmField
        val orderedRungs: List<LadderRung>

        @JvmField
        val enabledRungs: List<LadderRung>

        constructor(
            orderedRungs: List<LadderRung?>?,
            enabledRungs: List<LadderRung?>?
        ) : this(orderedRungs, enabledRungs, false)

        private constructor(
            orderedRungs: List<LadderRung?>?,
            enabledRungs: List<LadderRung?>?,
            fallbackOnInvalid: Boolean
        ) {
            val normalizedOrder = normalizeOrder(orderedRungs)
            val normalizedEnabled = normalizeEnabled(enabledRungs, normalizedOrder)
            if (fallbackOnInvalid && !hasAlwaysAvailableRung(normalizedEnabled)) {
                val defaults = defaults()
                this.orderedRungs = defaults.orderedRungs
                this.enabledRungs = defaults.enabledRungs
                return
            }
            if (!hasAlwaysAvailableRung(normalizedEnabled)) {
                normalizedEnabled.add(LadderRung.KANJI_MEANING)
            }
            this.orderedRungs = Collections.unmodifiableList(ArrayList(normalizedOrder))
            this.enabledRungs = Collections.unmodifiableList(ArrayList(normalizedEnabled))
        }

        fun orderText(): String = joinRungs(orderedRungs)

        fun enabledText(): String = joinRungs(enabledRungs)

        fun isEnabled(rung: LadderRung?): Boolean = enabledRungs.contains(rung)

        fun isValidForItem(rung: LadderRung?, availability: RungAvailability): Boolean {
            return isEnabled(rung) && availability.isAvailable(rung)
        }

        fun withRungEnabled(rung: LadderRung?, enabled: Boolean): StudyLadderSettings {
            if (rung == null) {
                return this
            }
            val nextEnabled = ArrayList(enabledRungs)
            if (enabled) {
                if (!nextEnabled.contains(rung)) {
                    nextEnabled.add(rung)
                }
            } else {
                if (alwaysAvailable(rung) && enabledAlwaysAvailableCount() <= 1 && nextEnabled.contains(rung)) {
                    return this
                }
                nextEnabled.remove(rung)
            }
            return StudyLadderSettings(orderedRungs, nextEnabled, false)
        }

        fun moveRung(rung: LadderRung?, delta: Int): StudyLadderSettings {
            if (rung == null || delta == 0) {
                return this
            }
            val order = ArrayList(orderedRungs)
            val from = order.indexOf(rung)
            if (from < 0) {
                return this
            }
            val to = max(0, min(order.size - 1, from + delta))
            if (to == from) {
                return this
            }
            order.removeAt(from)
            order.add(to, rung)
            return StudyLadderSettings(order, enabledRungs, false)
        }

        fun enabledAlwaysAvailableCount(): Int {
            var count = 0
            for (rung in enabledRungs) {
                if (alwaysAvailable(rung)) {
                    count++
                }
            }
            return count
        }

        fun startingRung(availability: RungAvailability): LadderRung {
            return effectiveRung(LadderRung.startingRung(), availability)
        }

        /**
         * Highest enabled rung valid for the item, honoring the user's ladder
         * order. Used to seed evidence-strong kanji at the top of the ladder
         * and to detect ceiling items for queue-cap parking.
         */
        fun highestRung(availability: RungAvailability): LadderRung {
            for (i in orderedRungs.indices.reversed()) {
                val candidate = orderedRungs[i]
                if (isValidForItem(candidate, availability)) {
                    return candidate
                }
            }
            return LadderRung.KANJI_MEANING
        }

        /** True when [current] resolves to the highest enabled rung for the item. */
        fun isAtCeiling(current: LadderRung?, availability: RungAvailability): Boolean {
            return effectiveRung(current, availability) == highestRung(availability)
        }

        fun effectiveRung(current: LadderRung?, availability: RungAvailability): LadderRung {
            val safeCurrent = current ?: LadderRung.startingRung()
            if (isValidForItem(safeCurrent, availability)) {
                return safeCurrent
            }
            var start = orderedRungs.indexOf(safeCurrent)
            if (start < 0) {
                start = orderedRungs.indexOf(LadderRung.startingRung())
            }
            start = max(0, start)
            for (distance in 1 until orderedRungs.size) {
                val before = start - distance
                if (before >= 0) {
                    val candidate = orderedRungs[before]
                    if (isValidForItem(candidate, availability)) {
                        return candidate
                    }
                }
                val after = start + distance
                if (after < orderedRungs.size) {
                    val candidate = orderedRungs[after]
                    if (isValidForItem(candidate, availability)) {
                        return candidate
                    }
                }
            }
            return LadderRung.KANJI_MEANING
        }

        fun nextRung(current: LadderRung?, availability: RungAvailability): LadderRung {
            val effective = effectiveRung(current, availability)
            val start = orderedRungs.indexOf(effective)
            for (i in start + 1 until orderedRungs.size) {
                val candidate = orderedRungs[i]
                if (isValidForItem(candidate, availability)) {
                    return candidate
                }
            }
            return effective
        }

        fun previousRung(current: LadderRung?, availability: RungAvailability): LadderRung {
            val effective = effectiveRung(current, availability)
            val start = orderedRungs.indexOf(effective)
            for (i in start - 1 downTo 0) {
                val candidate = orderedRungs[i]
                if (isValidForItem(candidate, availability)) {
                    return candidate
                }
            }
            return effective
        }

        fun rankForRung(rung: LadderRung?): Int {
            val rank = orderedRungs.indexOf(rung)
            return if (rank < 0) orderedRungs.size else rank
        }

        companion object {
            // Rungs whose availability depends on per-card data (never
            // always-available). RungAvailability answers each one's flag.
            @JvmField
            val CONDITIONAL_RUNGS: Set<LadderRung> = java.util.Collections.unmodifiableSet(
                java.util.EnumSet.of(
                    LadderRung.SIMILAR_KANJI,
                    LadderRung.KANJI_READING,
                    LadderRung.READING_KANJI,
                    LadderRung.SENTENCE_READING,
                ),
            )

            // Rungs that are auto-enabled for stored configs predating them
            // (generalizes the original MEANING_KANJI special case, D-R4).
            @JvmField
            val AUTO_ENABLE_RUNGS: Set<LadderRung> = java.util.Collections.unmodifiableSet(
                java.util.EnumSet.of(
                    LadderRung.MEANING_KANJI,
                    LadderRung.KANJI_READING,
                    LadderRung.READING_KANJI,
                    LadderRung.SENTENCE_READING,
                ),
            )

            @JvmStatic
            fun defaults(): StudyLadderSettings {
                val order = defaultsOrder()
                return StudyLadderSettings(order, defaultsEnabled(), false)
            }

            @JvmStatic
            fun fromStored(orderValue: String?, enabledValue: String?): StudyLadderSettings {
                val order = splitRungs(orderValue)
                val enabled = splitRungs(enabledValue).toMutableList()
                // D-R4: auto-enable rungs that postdate the stored order. A rung
                // absent from the stored order means the config predates it, so
                // splice it in enabled (as long as an always-available rung is
                // enabled, matching the original MEANING_KANJI behavior).
                if (hasAlwaysAvailableRung(enabled)) {
                    for (autoEnable in AUTO_ENABLE_RUNGS) {
                        if (!order.contains(autoEnable) && !enabled.contains(autoEnable)) {
                            enabled.add(autoEnable)
                        }
                    }
                }
                if (order.isEmpty() && enabled.isEmpty()) {
                    return defaults()
                }
                return StudyLadderSettings(order, enabled, true)
            }

            @JvmStatic
            fun alwaysAvailable(rung: LadderRung?): Boolean {
                return rung != null && !CONDITIONAL_RUNGS.contains(rung)
            }

            private fun splitRungs(value: String?): List<LadderRung> {
                val out = ArrayList<LadderRung>()
                if (value.isNullOrBlank()) {
                    return out
                }
                for (part in value.trim().split(Regex("[,\\s]+"))) {
                    val rung = LadderRung.fromWireName(part)
                    if (part == rung.wireName() && !out.contains(rung)) {
                        out.add(rung)
                    }
                }
                return out.toList()
            }

            private fun normalizeOrder(requested: List<LadderRung?>?): MutableList<LadderRung> {
                val out = ArrayList<LadderRung>()
                if (requested != null) {
                    for (rung in requested) {
                        if (rung != null && !out.contains(rung)) {
                            out.add(rung)
                        }
                    }
                }
                for (rung in defaultsOrder()) {
                    if (!out.contains(rung)) {
                        insertMissingRung(out, rung)
                    }
                }
                return out
            }

            private fun insertMissingRung(out: MutableList<LadderRung>, missing: LadderRung) {
                val defaults = defaultsOrder()
                val defaultIndex = defaults.indexOf(missing)
                var previousIndex = -1
                var nextIndex = -1
                for (i in defaultIndex - 1 downTo 0) {
                    previousIndex = out.indexOf(defaults[i])
                    if (previousIndex >= 0) {
                        break
                    }
                }
                for (i in defaultIndex + 1 until defaults.size) {
                    nextIndex = out.indexOf(defaults[i])
                    if (nextIndex >= 0) {
                        break
                    }
                }
                when {
                    previousIndex >= 0 && nextIndex >= 0 && previousIndex < nextIndex -> out.add(nextIndex, missing)
                    previousIndex >= 0 -> out.add(previousIndex + 1, missing)
                    nextIndex >= 0 -> out.add(nextIndex, missing)
                    else -> out.add(missing)
                }
            }

            private fun normalizeEnabled(
                requested: List<LadderRung?>?,
                order: List<LadderRung>
            ): MutableList<LadderRung> {
                val out = ArrayList<LadderRung>()
                if (requested.isNullOrEmpty()) {
                    out.addAll(order)
                    return out
                }
                for (rung in requested) {
                    if (rung != null && !out.contains(rung)) {
                        out.add(rung)
                    }
                }
                return out
            }

            private fun hasAlwaysAvailableRung(rungs: List<LadderRung>): Boolean {
                for (rung in rungs) {
                    if (alwaysAvailable(rung)) {
                        return true
                    }
                }
                return false
            }

            private fun defaultsOrder(): List<LadderRung> {
                // Ordered by scaffolding depth, most-supported to least-supported
                // (see docs/ladder-and-srs-system.md §3, design properties P9/P10).
                // similar_kanji sits directly below kanji_meaning (the new-card
                // start) so the first demotion reaches discrimination practice —
                // the app's signature remediation — after one demotion step
                // (ladder_demotion_fail_streak fails) for cards that have
                // confusion data, instead of three steps (Goal 65). Cards
                // without similar-kanji content skip over it to meaning_kanji.
                return listOf(
                    LadderRung.WRITE_KANJI,
                    LadderRung.TYPE_MEANING,
                    LadderRung.MEANING_KANJI,
                    // reading_kanji (Goal 79) is the phonetic sibling of
                    // similar_kanji; it sits directly below it (not between
                    // similar_kanji and kanji_meaning) so the Goal 65 invariant
                    // holds: the first demotion from the start rung still reaches
                    // similar_kanji in one step.
                    LadderRung.READING_KANJI,
                    LadderRung.SIMILAR_KANJI,
                    LadderRung.KANJI_MEANING,
                    LadderRung.FONT_MEANING,
                    // kanji_reading (Goal 78) sits directly below word_reading so
                    // a word_reading fail streak demotes straight into targeted
                    // reading discrimination. Conditional: cards without reading
                    // data cross over it.
                    LadderRung.KANJI_READING,
                    LadderRung.WORD_READING,
                    // sentence_reading (Goal 80) is the new ceiling: read the
                    // word inside the user's own mined sentence. Conditional on
                    // sentence+reading data; cards without it keep word_reading
                    // as their effective ceiling.
                    LadderRung.SENTENCE_READING,
                )
            }

            private fun defaultsEnabled(): MutableList<LadderRung> = ArrayList(defaultsOrder())

            private fun joinRungs(rungs: List<LadderRung>): String {
                val values = ArrayList<String>()
                for (rung in rungs) {
                    values.add(rung.wireName())
                }
                return values.joinToString(",")
            }
        }
    }

    /**
     * Phase of the card within Anki learning/relearning/review semantics.
     * Learning and relearning repeats are practice-only and must not count
     * toward ladder promotion or demotion; only [REVIEW]-phase answers on a
     * due card advance the ladder streaks.
     */
    enum class SchedulerPhase(private val wireNameValue: String) {
        NEW_LEARNING("new_learning"),
        REVIEW(LEARNING_REPEAT_REVIEW),
        RELEARNING("relearning");

        fun wireName(): String = wireNameValue

        companion object {
            @JvmStatic
            fun fromWireName(name: String?): SchedulerPhase {
                if (name == null) {
                    return NEW_LEARNING
                }
                for (phase in entries) {
                    if (phase.wireNameValue == name) {
                        return phase
                    }
                }
                LOGGER.warning { "SchedulerPhase.fromWireName: unknown wire name '$name', defaulting to NEW_LEARNING" }
                return NEW_LEARNING
            }
        }
    }

    companion object {
        const val DEFAULT_WRITING_TRIGGER_MISS_DAYS: Int = 3
        const val DEFAULT_RECOGNITION_PROMOTION_PASSES: Int = 3
        const val DEFAULT_REAL_DUE_REVIEWS_TO_MOVE: Int = 3
        const val DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS: Int = 21
        const val DEFAULT_LADDER_DEMOTION_FAIL_STREAK: Int = 3
        const val DEFAULT_LADDER_PROMOTION_MIN_PASSES: Int = 2
        const val DEFAULT_SUSPENDED_RANK_MIN: Int = 100
        const val DEFAULT_SUSPENDED_RANK_MAX: Int = 3000
        const val DEFAULT_IMPORT_ACTIVE_CARDS: Boolean = false
        const val DEFAULT_IMPORT_SUSPENDED_CARDS: Boolean = true
        const val DEFAULT_IMPORT_TAGGED_CARDS: Boolean = false

        // Weak-card import is on by default: cards the user actively and
        // repeatedly misses (leeches) are the highest-value repair targets Kani
        // can find, and each repair directly reduces failed reviews the user is
        // already doing in Anki. Thresholds are deliberately stricter than a
        // single lapse so the default queue stays Pareto-shaped.
        const val DEFAULT_IMPORT_WEAK_CARDS: Boolean = true
        const val DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY: Double = 7.5
        const val DEFAULT_IMPORT_WEAK_LAPSES: Int = 3
        const val DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI: Int = 1

        // Historical (pre-weak-default) import defaults. These are the
        // fingerprint of the very first shipped default configuration and must
        // stay frozen: the one-time "old default" repair in SyncSettings
        // compares stored values against them to decide whether a user is still
        // on the original active-cards-on default. Do not couple them to the
        // live DEFAULT_* values above, or bumping a default silently disables
        // the migration.
        const val LEGACY_IMPORT_WEAK_FSRS_DIFFICULTY: Double = 7.0
        const val LEGACY_IMPORT_WEAK_LAPSES: Int = 2
        const val LEGACY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI: Int = 1

        // Ceiling parking (Finding 1): once an item reaches the highest enabled
        // rung in the review phase and its scheduled interval grows past this
        // multiple of the promotion threshold, it is "parked" — kept studyable
        // when due, but no longer counted against the active queue cap so it can
        // never permanently block admission of new repair targets.
        const val CEILING_PARK_INTERVAL_MULTIPLIER: Int = 4
        const val DEFAULT_IMPORT_BROWSER_QUERY_CARDS: Boolean = false
        const val DEFAULT_IMPORT_BROWSER_QUERY: String = ""
        const val NEW_CARD_SORT_FREQUENCY: String = "frequency"
        const val NEW_CARD_SORT_FSRS_DIFFICULTY: String = "fsrs_difficulty"
        const val NEW_CARD_SORT_RETRIEVABILITY_RISK: String = "retrievability_risk"
        const val NEW_CARD_SORT_KANI_WEAKNESS: String = "kani_weakness"
        const val NEW_CARD_SORT_BALANCED_PRIORITY: String = "balanced_priority"

        // Balanced priority is the default: between two candidate kanji it ranks
        // the one currently causing the most failed reviews (weakness,
        // retrievability risk, difficulty, suspended pressure) above the one
        // that is merely more frequent. This applies the Pareto "value" principle
        // to admission. A stored explicit "frequency" choice still wins.
        const val DEFAULT_NEW_CARD_SORT_MODE: String = NEW_CARD_SORT_BALANCED_PRIORITY
        const val DEFAULT_FREQUENCY_RETENTION_ENABLED: Boolean = false
        const val DEFAULT_FREQUENCY_RETENTION_RANGES: String = ""
        const val LEARNING_REPEAT_NEW: String = "new"
        const val LEARNING_REPEAT_REVIEW: String = "review"

        @JvmField
        val SOURCE_ACTIVE: String = ImportRuleMatch.SOURCE_ACTIVE

        @JvmField
        val SOURCE_SUSPENDED: String = ImportRuleMatch.SOURCE_SUSPENDED

        @JvmField
        val SOURCE_TAGGED: String = ImportRuleMatch.SOURCE_TAGGED

        @JvmField
        val SOURCE_WEAK: String = ImportRuleMatch.SOURCE_WEAK

        @JvmField
        val SOURCE_BROWSER_QUERY: String = ImportRuleMatch.SOURCE_BROWSER_QUERY

        @JvmField
        val LOGGER: Logger = Logger.getLogger(RecordsBase::class.java.name)

        @JvmField
        val TASK_MEMORY_SEPARATOR: Pattern = Pattern.compile("\\t")

        @JvmField
        val IMPORT_TAG_SEPARATOR: Pattern = Pattern.compile("[,\\s]+")

        const val CONTEXT_SETTINGS: String = "Settings"
        const val CONTEXT_CARD: String = "Card"
        const val CONTEXT_EXAMPLE: String = "Example"
        const val CONTEXT_DASHBOARD_ROW: String = "DashboardRow"
        const val CONTEXT_KANJI_INVENTORY_ITEM: String = "KanjiInventoryItem"
        const val CONTEXT_SIMILAR_KANJI_CHOICE_CARD: String = "SimilarKanjiChoiceCard"
        const val CONTEXT_MEANING_KANJI_CHOICE_CARD: String = "MeaningKanjiChoiceCard"
        const val CONTEXT_SIMILAR_KANJI_WRITING_REPAIR: String = "SimilarKanjiWritingRepair"
        const val CONTEXT_KANJI_TIMELINE_EVENT: String = "KanjiTimelineEvent"
        const val CONTEXT_TASK_MEMORY: String = "TaskMemory"
        const val CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS: String = "TaskMemory.fromStudyFields"
        const val CONTEXT_STUDY_ITEM: String = "StudyItem"
        const val CONTEXT_LEARNING_REPEAT: String = "LearningRepeat"
        const val CONTEXT_REVIEW_REQUEST: String = "ReviewRequest"
        const val CONTEXT_ADAPTIVE_LOAD_PLAN: String = "AdaptiveLoadPlan"

        @JvmStatic
        fun arg(args: Array<Any?>, index: Int, context: String): Any? {
            require(index < args.size) { "$context expected more arguments" }
            return args[index]
        }

        @JvmStatic
        fun requireArgCount(context: String, args: Array<Any?>, vararg expectedCounts: Int) {
            for (expected in expectedCounts) {
                if (args.size == expected) {
                    return
                }
            }
            throw IllegalArgumentException("$context received ${args.size} trailing arguments")
        }

        @JvmStatic
        fun stringArg(args: Array<Any?>, index: Int, context: String): String? {
            return arg(args, index, context) as String?
        }

        @JvmStatic
        fun nullToEmpty(value: String?): String = value ?: ""

        @JvmStatic
        fun <T> nullToEmptyList(value: List<T>?): List<T> = value ?: Collections.emptyList()

        @JvmStatic
        fun intArg(args: Array<Any?>, index: Int, context: String): Int {
            return (arg(args, index, context) as Number).toInt()
        }

        @JvmStatic
        fun longArg(args: Array<Any?>, index: Int, context: String): Long {
            return (arg(args, index, context) as Number).toLong()
        }

        @JvmStatic
        fun booleanArg(args: Array<Any?>, index: Int, context: String): Boolean {
            return arg(args, index, context) as Boolean
        }

        @JvmStatic
        fun nullableDoubleArg(args: Array<Any?>, index: Int, context: String): Double? {
            val value = arg(args, index, context) ?: return null
            return (value as Number).toDouble()
        }

        @JvmStatic
        fun parseImportTags(value: String?): List<String> {
            if (value == null || value.trim().isEmpty()) {
                return Collections.emptyList()
            }
            val parsed = LinkedHashSet<String>()
            for (part in IMPORT_TAG_SEPARATOR.split(value.trim())) {
                parsed.add(part.trim())
            }
            return ArrayList(parsed)
        }
    }
}

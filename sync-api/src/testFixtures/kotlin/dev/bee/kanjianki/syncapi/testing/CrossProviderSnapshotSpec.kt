package dev.bee.kanjianki.syncapi.testing

import dev.bee.kanjianki.core.RecordsSyncModels

/**
 * The normalized-snapshot conformance spec both collection providers must satisfy.
 *
 * Kani reads the same Anki collection through two very different doors: AnkiDroid's
 * content provider on Android, and AnkiConnect's HTTP API on desktop. The scheduler
 * downstream cannot tell them apart and must not need to — a kanji that is mature
 * on Android has to be mature on desktop, or a user syncing both sees their queue
 * change shape when they switch device.
 *
 * Each provider has its own tests for its own wire format. What those cannot catch
 * is *divergence*: two independently reasonable readers drifting on what `queue`,
 * `ivl`, or a missing FSRS value means. So the rules live here once, stated over
 * provider-neutral inputs, and each provider's suite asserts its reader against
 * this same list. A rule change has to be made in one place, where both providers
 * see it.
 *
 * This is not hypothetical. Writing this spec is what surfaced the drift it now
 * prevents: AnkiConnect floored Anki's negative (sub-day, seconds-encoded) `ivl`
 * to zero days and clamped `reps`/`lapses`, while AnkiDroid passed the raw cursor
 * values straight through. Both providers now normalize through the single
 * `ProviderCardPolicy` in `:sync-domain`, and the rules below are asserted against
 * each provider's own entry point into it.
 *
 * ### Allowed differences
 *
 * The two providers are not identical, and pretending otherwise would be worse
 * than admitting it. Every field where they may legitimately differ is enumerated
 * in [AllowedDifference] with a reason code, so "these differ" is a decision on
 * the record rather than a discovery someone makes while debugging a user's queue.
 * A field absent from that list must agree exactly.
 */
object CrossProviderSnapshotSpec {
    /**
     * One normalization rule: a raw provider value and the snapshot value both
     * providers must produce from it.
     */
    class Rule<T>(
        /** What the rule protects, in a form that reads in a failure message. */
        val description: String,
        /** The raw value as it appears on the provider's wire. */
        val raw: Long,
        /** The normalized value the snapshot must carry. */
        val expected: T,
    ) {
        override fun toString(): String = "$description (raw=$raw, expected=$expected)"
    }

    /**
     * A field the two providers may legitimately disagree on, and why.
     *
     * The reason is not decoration. Each of these is a place where one provider
     * cannot supply what the other can, and the scheduler has to be safe under
     * *either* answer; recording which is which is what stops a future reader from
     * "fixing" a difference that is actually load-bearing.
     */
    enum class AllowedDifference(val field: String, val reason: String) {
        CARD_ID(
            "Card.cardId",
            "AnkiDroid provider projections may reject the `_id` column, so the reader " +
                "derives a synthetic stable id from (noteId, ord). AnkiConnect always " +
                "reports real card ids. Ids are compared for stability and uniqueness " +
                "within a provider, never across providers.",
        ),
        DECK_NAME(
            "Card.deckName",
            "AnkiDroid's card cursor exposes one deck value, which the reader writes to " +
                "both deckId and deckName. AnkiConnect reports the deck name separately " +
                "from its id. Kani routes on neither, so both forms are accepted.",
        ),
        FSRS_MEMORY(
            "Card.fsrsStability / fsrsDifficulty / fsrsRetrievability",
            "AnkiDroid's provider exposes FSRS memory state; stock AnkiConnect has no " +
                "action that returns it, so desktop reports null and does not declare " +
                "the FSRS_MEMORY_STATE capability. Admission seeds from Anki memory " +
                "state only when the capability is present, and otherwise starts the " +
                "conservative new-learning path.",
        ),
        CARD_TYPE(
            "Card.type",
            "AnkiDroid may omit the type column, in which case the reader infers it " +
                "from suspension (3 when suspended, else 0). AnkiConnect always reports " +
                "the real type. Kani reads suspension from `suspended`, not from type.",
        ),
        ;

        override fun toString(): String = "$field: $reason"
    }

    /**
     * Suspension. Kani's rule is `queue < 0`, which is deliberately *wider* than
     * AnkiConnect's own `areSuspended` (`queue == -1`): a user-buried or
     * sibling-buried card is not something Kani should treat as active study
     * material, and Android has always normalized it this way.
     */
    @JvmField
    val suspensionRules: List<Rule<Boolean>> = listOf(
        Rule("an active review card is not suspended", 2L, false),
        Rule("a new card is not suspended", 0L, false),
        Rule("a learning card is not suspended", 1L, false),
        Rule("the canonical suspended queue is suspended", -1L, true),
        Rule("a user-buried card counts as suspended", -2L, true),
        Rule("a sibling-buried card counts as suspended", -3L, true),
    )

    /**
     * Interval, in whole days.
     *
     * Anki stores a *negative* `ivl` to mean seconds, for sub-day learning cards.
     * A reader that passed that through unchanged would hand the scheduler a
     * negative day count, and maturity arithmetic compares against a positive day
     * threshold — so a learning card answered minutes ago could read as older than
     * a card genuinely at a long interval. Both providers floor it to 0.
     */
    @JvmField
    val intervalRules: List<Rule<Int>> = listOf(
        Rule("a mature interval passes through", 30L, 30),
        Rule("a one-day interval passes through", 1L, 1),
        Rule("a zero interval stays zero", 0L, 0),
        Rule("sub-day seconds must not read as negative days", -600L, 0),
        Rule("a large interval is not wrapped", Int.MAX_VALUE.toLong(), Int.MAX_VALUE),
        Rule("an over-Int interval saturates rather than wrapping", Long.MAX_VALUE, Int.MAX_VALUE),
    )

    /**
     * Counters (`reps`, `lapses`). Negative is not meaningful, and the snapshot
     * field is an `Int`, so both providers floor at 0 and saturate at the top
     * rather than wrapping into a negative count.
     */
    @JvmField
    val counterRules: List<Rule<Int>> = listOf(
        Rule("a normal rep count passes through", 7L, 7),
        Rule("zero passes through", 0L, 0),
        Rule("a negative counter floors at zero", -5L, 0),
        Rule("an over-Int counter saturates rather than wrapping", Long.MAX_VALUE, Int.MAX_VALUE),
    )

    /**
     * Signed fields (`queue`, `due`). Unlike counters these keep their sign, because
     * the sign carries meaning: `queue` uses negatives for suspended and buried, and
     * `due` is relative in some queues.
     */
    @JvmField
    val signedRules: List<Rule<Int>> = listOf(
        Rule("a positive due passes through", 500L, 500),
        Rule("a negative queue keeps its sign", -2L, -2),
        Rule("an under-Int value saturates rather than wrapping", Long.MIN_VALUE, Int.MIN_VALUE),
        Rule("an over-Int value saturates rather than wrapping", Long.MAX_VALUE, Int.MAX_VALUE),
    )

    /**
     * Template ordinal. Kani studies one card per note — the front template, ord 0.
     * Accepting a reverse or extra template would double-count a kanji's evidence
     * and let one note look like two independent passes.
     */
    @JvmField
    val acceptedOrdinalRules: List<Rule<Boolean>> = listOf(
        Rule("the front template is accepted", 0L, true),
        Rule("a reverse template is rejected", 1L, false),
        Rule("a third template is rejected", 2L, false),
    )

    /**
     * The fields that must agree exactly between providers for the same card.
     * Anything not here is either in [AllowedDifference] or is not part of the
     * cross-provider contract.
     */
    @JvmField
    val agreedCardFields: List<String> = listOf(
        "noteId",
        "ord",
        "queue",
        "due",
        "intervalDays",
        "reps",
        "lapses",
        "suspended",
        "browserQueryMatched",
    )

    /** Note fields that must agree exactly. */
    @JvmField
    val agreedNoteFields: List<String> = listOf("noteId", "modelName", "fields", "tags")

    /**
     * Reduces [card] to just the fields under the cross-provider agreement, so two
     * snapshots read through different providers can be compared without the
     * allowed differences drowning out a real one.
     */
    @JvmStatic
    fun agreedView(card: RecordsSyncModels.Card): Map<String, Any> = linkedMapOf(
        "noteId" to card.noteId,
        "ord" to card.ord,
        "queue" to card.queue,
        "due" to card.due,
        "intervalDays" to card.intervalDays,
        "reps" to card.reps,
        "lapses" to card.lapses,
        "suspended" to card.suspended,
        "browserQueryMatched" to card.browserQueryMatched,
    )

    /** The same reduction for a note. */
    @JvmStatic
    fun agreedView(note: RecordsSyncModels.Note): Map<String, Any> = linkedMapOf(
        "noteId" to note.noteId,
        "modelName" to note.modelName,
        "fields" to note.fields,
        "tags" to note.tags,
    )

    /**
     * A provider's own entry points into the normalization rules. Each provider
     * supplies its real production functions — not the shared policy directly — so
     * the check fails if a provider stops delegating and reintroduces a local copy.
     */
    class ProviderNormalization(
        /** Names the provider in failure messages. */
        val providerName: String,
        val isSuspended: (Long) -> Boolean,
        val isAcceptedTemplateOrd: (Long) -> Boolean,
        val intervalDays: (Long) -> Int,
        val counter: (Long) -> Int,
        val signed: (Long) -> Int,
    )

    /**
     * One rule a provider failed, in a form that names the provider, the rule, and
     * both values. Returned rather than thrown so a run reports *every* divergence
     * at once: fixing them one exception at a time hides how far the two readers
     * have drifted.
     */
    class Violation(val providerName: String, val rule: String, val expected: Any?, val actual: Any?) {
        override fun toString(): String = "$providerName: $rule but produced $actual (expected $expected)"
    }

    /**
     * Checks [normalization] against every rule in this spec.
     * @return the failures, empty when the provider conforms.
     */
    @JvmStatic
    fun verify(normalization: ProviderNormalization): List<Violation> {
        val violations = ArrayList<Violation>()
        fun <T> check(rules: List<Rule<T>>, apply: (Long) -> T) {
            for (rule in rules) {
                val actual = apply(rule.raw)
                if (actual != rule.expected) {
                    violations += Violation(
                        normalization.providerName,
                        rule.description,
                        rule.expected,
                        actual,
                    )
                }
            }
        }
        check(suspensionRules, normalization.isSuspended)
        check(acceptedOrdinalRules, normalization.isAcceptedTemplateOrd)
        check(intervalRules, normalization.intervalDays)
        check(counterRules, normalization.counter)
        check(signedRules, normalization.signed)
        return violations
    }

    /**
     * Verifies [normalization] and raises when it diverges, reporting every failed
     * rule together.
     */
    @JvmStatic
    fun verifyOrThrow(normalization: ProviderNormalization) {
        val violations = verify(normalization)
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "${normalization.providerName} diverges from the cross-provider snapshot spec:\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncdomain.ProviderCardPolicy

/**
 * AnkiConnect's view of card-field normalization.
 *
 * Every rule here delegates to [ProviderCardPolicy], which AnkiDroid's reader also
 * uses. The rules used to live in this file as AnkiConnect's own copy, and that is
 * exactly the arrangement that let the two providers drift: AnkiConnect floored
 * sub-day intervals and clamped counters while AnkiDroid passed the raw cursor
 * values straight through, so the same collection produced different maturity
 * evidence depending on which door Kani read it through.
 *
 * This object is kept — rather than calling the policy directly from the reader —
 * so the provider still names the normalization it applies, and so
 * `CrossProviderSnapshotSpec` has a per-provider entry point to hold to the shared
 * rules. Do not reintroduce a local implementation of any of these.
 */
object AnkiConnectCardNormalization {
    /** True when the card is suspended under Kani's `queue < 0` rule. */
    fun isSuspended(queue: Long): Boolean = ProviderCardPolicy.isSuspendedQueue(queue)

    /**
     * A configured-model card is only accepted when its template ordinal is 0
     * (Kani's front template). Returns whether the card should be kept.
     */
    fun isAcceptedConfiguredOrd(templateOrd: Long): Boolean =
        ProviderCardPolicy.isAcceptedTemplateOrd(templateOrd)

    /** Anki's raw `ivl` as the whole days the snapshot expects. */
    fun intervalDays(rawInterval: Long): Int = ProviderCardPolicy.intervalDays(rawInterval)

    /** An unbounded provider counter, floored at 0 and saturating at `Int.MAX_VALUE`. */
    fun counter(rawValue: Long): Int = ProviderCardPolicy.counter(rawValue)

    /** A signed provider value, saturating at both `Int` bounds. */
    fun signed(rawValue: Long): Int = ProviderCardPolicy.signed(rawValue)
}

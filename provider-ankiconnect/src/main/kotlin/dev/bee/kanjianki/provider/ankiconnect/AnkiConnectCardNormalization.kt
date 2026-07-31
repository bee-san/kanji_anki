package dev.bee.kanjianki.provider.ankiconnect

/**
 * Provider-neutral normalization of AnkiConnect card fields, matching the
 * Android (AnkiDroid) semantics exactly so a card produces the same
 * provider-neutral snapshot regardless of which provider read it.
 *
 * The one deliberate deviation from AnkiConnect's own helpers: suspension is
 * `queue < 0` (any negative queue), not AnkiConnect `areSuspended`'s narrower
 * `queue == -1`. Android normalizes on `queue < 0`, and Kani preserves that so
 * user-buried/other negative-queue states are treated consistently.
 */
object AnkiConnectCardNormalization {
    /** True when the card is suspended under Kani's `queue < 0` rule. */
    fun isSuspended(queue: Long): Boolean = queue < 0L

    /**
     * A configured-model card is only accepted when its template ordinal is 0
     * (Kani's front template). Returns whether the card should be kept.
     */
    fun isAcceptedConfiguredOrd(templateOrd: Long): Boolean = templateOrd == 0L

    /**
     * Converts Anki's raw `ivl` to the whole days the snapshot's `intervalDays`
     * expects. Anki stores a negative `ivl` as *seconds* for sub-day learning
     * cards; AnkiDroid's provider column is already days, so Kani floors the
     * sub-day case to 0 rather than letting a negative seconds value read as a
     * negative day count (which would make a learning card look mature-adjacent
     * or corrupt maturity arithmetic).
     */
    fun intervalDays(rawInterval: Long): Int {
        if (rawInterval <= 0L) return 0
        return rawInterval.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Clamps an unbounded provider counter into the snapshot's `Int` field
     * without wrapping. Negative counters are not meaningful, so they floor at 0.
     */
    fun counter(rawValue: Long): Int =
        rawValue.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Clamps a signed provider value into the snapshot's `Int` field without
     * wrapping. Unlike [counter], negatives are preserved: `queue` uses them for
     * suspended/buried and `due` is relative in some queues.
     */
    fun signed(rawValue: Long): Int =
        rawValue.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

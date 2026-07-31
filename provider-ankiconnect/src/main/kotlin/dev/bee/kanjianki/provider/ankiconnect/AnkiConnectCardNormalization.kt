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
}

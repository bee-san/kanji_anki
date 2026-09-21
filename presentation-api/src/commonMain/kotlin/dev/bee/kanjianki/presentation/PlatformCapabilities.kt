package dev.bee.kanjianki.presentation

/**
 * Something a host may or may not be able to do.
 *
 * Kani's two hosts differ in ways the user can see, and the failure mode this
 * exists to prevent is a screen offering an affordance that silently does
 * nothing. Android can wake the app from a closed state to fire a reminder;
 * a desktop app cannot, unless it is running. A desktop host has a tray; Android
 * does not. AnkiConnect exposes no FSRS memory state, while AnkiDroid's provider
 * does.
 *
 * Modelled as an enum rather than a bag of booleans so a screen must handle a
 * capability by name, and a new one cannot be forgotten by a `when`.
 */
enum class PlatformCapability {
    /** A collection provider is configured and reachable at all. */
    PROVIDER_CONNECTIVITY,

    /**
     * The provider reports FSRS stability/difficulty for its cards.
     *
     * AnkiDroid's provider does; AnkiConnect does not advertise
     * `FSRS_MEMORY_STATE` at all, so admission seeds maturity from the interval
     * instead. The user is told, because it changes how their first reviews are
     * scheduled.
     */
    PROVIDER_FSRS_MEMORY,

    /** The provider accepts the note-tag writes archive/repaired tagging needs. */
    PROVIDER_NOTE_TAG_WRITE,

    /**
     * The provider can be handed additive Missing Kanji notes directly.
     *
     * When absent, the Missing Kanji flow stays complete through CSV export —
     * which is why this is a capability and not an error.
     */
    PROVIDER_MISSING_KANJI_WRITE,

    /** The host can hand a search query to Anki's own browser/card browser. */
    PROVIDER_BROWSER_HANDOFF,

    /** Ink can be recognized locally, for the `write_kanji` task. */
    WRITING_RECOGNITION,

    /** A system tray or menu-bar presence exists. */
    TRAY_PRESENCE,

    /** The host can post a user-visible notification. */
    NOTIFICATIONS,

    /**
     * Work can run when the app is not open.
     *
     * The reason reminders are honest on one host and not the other. A reminder
     * scheduled where this is absent only fires if the app happens to be
     * running, and the Settings copy has to say so.
     */
    CLOSED_APP_SCHEDULING,

    /**
     * Secrets persist in an OS-backed store across restarts.
     *
     * When absent, `SecretStore` is session-only: an AnkiConnect API key has to
     * be re-entered each launch. Offering "remember this key" there would be a
     * promise the host cannot keep.
     */
    SECRET_PERSISTENCE,

    /** Live database snapshots and whole-file restore are available. */
    BACKUP_RESTORE,

    /** The host can install or hand off a verified update package. */
    UPDATE_DELIVERY,
}

/**
 * The capability set one host actually has, resolved once at composition.
 *
 * Immutable and complete: every capability is either present or absent, with no
 * "unknown" third state. A screen deciding what to render cannot wait, and an
 * unknown capability would be rendered as either present (offering a dead
 * button) or absent (hiding a working feature) anyway — so the host resolves it
 * before the UI ever sees it, and a genuinely dynamic condition like "the
 * provider is reachable right now" belongs in [RouteState.failure], not here.
 */
data class PlatformCapabilities(
    val present: Set<PlatformCapability>,
) {
    operator fun contains(capability: PlatformCapability): Boolean =
        capability in present

    fun supports(capability: PlatformCapability): Boolean = capability in present

    val missing: Set<PlatformCapability>
        get() = PlatformCapability.entries.toSet() - present

    /**
     * Gates an action on a capability.
     *
     * Returns the action when supported and an explanatory effect when not,
     * which is what keeps "check the capability" from being a step a screen can
     * forget. Callers pattern-match on the result.
     */
    fun gate(
        capability: PlatformCapability,
        action: KaniAction,
    ): CapabilityGate =
        if (supports(capability)) {
            CapabilityGate.Allowed(action)
        } else {
            CapabilityGate.Unavailable(capability)
        }

    companion object {
        /**
         * No capabilities.
         *
         * The default a shell starts from, chosen so a host that forgets to
         * supply its real set renders visibly reduced rather than offering
         * everything and failing at the tap.
         */
        val NONE: PlatformCapabilities = PlatformCapabilities(emptySet())

        fun of(vararg capabilities: PlatformCapability): PlatformCapabilities =
            PlatformCapabilities(capabilities.toSet())
    }
}

/** The result of gating an action on a capability. */
sealed interface CapabilityGate {
    data class Allowed(val action: KaniAction) : CapabilityGate

    data class Unavailable(val capability: PlatformCapability) : CapabilityGate
}

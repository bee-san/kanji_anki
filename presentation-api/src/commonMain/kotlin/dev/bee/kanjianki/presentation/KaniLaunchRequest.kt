package dev.bee.kanjianki.presentation

/**
 * How the app was asked to open, when something other than the user's own launch
 * asked for it.
 *
 * Kani's deep links are not web URLs. They are the launcher shortcuts, the daily
 * reminder and update notifications, and the four home-screen widgets — every one
 * of which currently names its target with a boolean intent extra
 * (`EXTRA_OPEN_STUDY`, `EXTRA_OPEN_STATS`, …) read in a `when` block inside
 * `MainActivityStartup`. That works on exactly one host: a desktop tray item, a
 * second window, or a relaunch with arguments has no `Intent` to put an extra on.
 *
 * A request is a [KaniDestination] plus the two decisions the destination cannot
 * carry, both of which the Android code makes today and neither of which is
 * obvious from the route alone:
 *
 *  - [suppressesStudyResume] — whether arriving here should cancel the
 *    resume-interrupted-study behavior an ordinary launch performs. Every Android
 *    branch except Study calls `disableStudyOrdinaryResume()`, because a user who
 *    tapped "12 due" on a stats widget asked for stats, not for the session they
 *    abandoned yesterday. Study does not, because there resuming *is* the request.
 *  - [isUserInitiated] — whether a human chose this destination. Study recovery
 *    and reminder eligibility both need to tell "the user tapped a notification"
 *    apart from "the process restarted", and a route cannot say which.
 */
data class KaniLaunchRequest(
    val destination: KaniDestination,
    val suppressesStudyResume: Boolean,
    val isUserInitiated: Boolean = true,
)

/**
 * The one place an external launch becomes a [KaniLaunchRequest].
 *
 * Each host still owns reading its own container — an `Intent`'s extras, a desktop
 * argument vector, a tray callback — because those types cannot cross to common
 * code. What no host owns any more is the *precedence* and the resume decision, so
 * a desktop host cannot quietly disagree about which wins when a widget tap arrives
 * on top of a pending notification.
 *
 * The order below is Android's `when` block with one deliberate change: Android
 * tests `EXTRA_OPEN_HOME` before the launcher-shortcut action, so Home outranks
 * Browse and Games there. Here Home is last. The two cannot disagree in practice —
 * a shortcut intent carries an action and no extras, and the extras come from
 * widget/notification `PendingIntent`s that carry no shortcut action — so no
 * existing behavior changes. Home is last because it is what every other target
 * falls back to, and a precedence table where the fallback can beat a specific
 * request is a trap for whoever adds the next target.
 *
 * Decoding is fail-closed for the same reason as [KaniDestinationCodec]: an
 * unrecognized or malformed target yields `null`, and a `null` request means
 * "launch normally", which is always a safe screen to be on.
 */
object KaniLaunchCodec {
    /**
     * The target names, in the order they win.
     *
     * These strings are the wire format — Android maps its existing extras onto
     * them and the desktop host parses them from arguments — so they are as
     * load-bearing as the route names and must not be renamed casually.
     */
    enum class Target(val wireName: String) {
        /**
         * Open a specific kanji's detail screen. Requires [KEY_KANJI].
         *
         * First because it is the most specific: the focus-kanji widget names one
         * card, and a request that names a card should not lose to a request that
         * names a tab.
         */
        KANJI_DETAIL("kanji-detail"),

        /** Resume or start studying. The one target that keeps study resume. */
        STUDY("study"),

        /** The in-app update page, from the update notification. */
        UPDATE("update"),

        /** The stats tab, from the activity widget. */
        STATS("stats"),

        /** Browse, from the launcher shortcut. */
        BROWSE("browse"),

        /** The games menu, from the launcher shortcut. */
        GAMES("games"),

        /**
         * Home. Last, because it is what every other target falls back to, so a
         * request carrying both Home and something more specific means the
         * specific one.
         */
        HOME("home"),
        ;

        /**
         * Whether arriving here cancels the ordinary resume-interrupted-study
         * behavior.
         *
         * A property on the target rather than only on the built request, because a
         * host has to know this even when the request could not be built: an
         * `EXTRA_OPEN_KANJI_DETAIL` carrying an unusable glyph still means the user
         * tapped a widget, so it falls back to Home *without* resuming study. That
         * is what Android does today, and reading the decision off the target is
         * what keeps the fallback path from quietly disagreeing with the happy one.
         */
        val suppressesStudyResume: Boolean
            get() = this != STUDY

        companion object {
            private val BY_WIRE_NAME: Map<String, Target> =
                entries.associateBy(Target::wireName)

            /** The target for [wireName], or `null` when it names nothing known. */
            fun fromWireName(wireName: String?): Target? = BY_WIRE_NAME[wireName]
        }
    }

    const val KEY_TARGET: String = "target"
    const val KEY_KANJI: String = "kanji"

    /**
     * Resolves the winning [Target] among those [present], or `null` if none are.
     *
     * Takes a set rather than one value because Android's `when` block is reached
     * with an `Intent` that may carry several extras at once — a notification tap
     * landing on an activity whose intent still holds a widget's extra, for
     * instance. Making the precedence a function of the whole set is what makes
     * that case decided rather than incidental.
     */
    fun resolve(present: Set<Target>): Target? =
        Target.entries.firstOrNull { it in present }

    /**
     * The request for [target], or `null` when [target] is `null` or unusable.
     *
     * [kanji] is required by [Target.KANJI_DETAIL] and ignored otherwise. A blank
     * or absent kanji does not fall back to Home here: it yields `null`, so the
     * caller launches normally rather than sending the user somewhere they did not
     * ask for. **Not normalized** — same boundary as [KaniDestinationCodec]:
     * `TextUtil.normalizeSingleKanji` lives in `:core`, which this module cannot
     * see, so a host decoding untrusted input normalizes before calling.
     */
    fun request(target: Target?, kanji: String? = null): KaniLaunchRequest? {
        target ?: return null
        val destination = when (target) {
            Target.KANJI_DETAIL -> kanji
                ?.takeIf(String::isNotBlank)
                ?.let { KaniDestination.Detail(kanji = it) }
                ?: return null

            Target.STUDY -> KaniDestination.Study
            Target.UPDATE -> KaniDestination.Settings(SettingsSection.UPDATE)
            Target.STATS -> KaniDestination.Stats
            Target.BROWSE -> KaniDestination.Browse()
            Target.GAMES -> KaniDestination.Games
            Target.HOME -> KaniDestination.Home
        }
        return KaniLaunchRequest(
            destination = destination,
            suppressesStudyResume = target.suppressesStudyResume,
        )
    }

    /**
     * Decodes a flat string map — a desktop argument vector or a persisted
     * hand-off — into a request.
     *
     * The map form exists so a host without an `Intent` has a container of the
     * same shape as [KaniDestinationCodec]'s, rather than inventing a parallel
     * encoding for the same information.
     */
    fun decode(arguments: Map<String, String>): KaniLaunchRequest? = request(
        target = Target.fromWireName(arguments[KEY_TARGET]),
        kanji = arguments[KEY_KANJI],
    )

    /** Encodes [request] back into the flat map [decode] accepts. */
    fun encode(request: KaniLaunchRequest): Map<String, String> = buildMap {
        val destination = request.destination
        val target = when {
            destination is KaniDestination.Detail -> Target.KANJI_DETAIL
            destination is KaniDestination.Settings &&
                destination.section == SettingsSection.UPDATE -> Target.UPDATE

            destination == KaniDestination.Study -> Target.STUDY
            destination == KaniDestination.Stats -> Target.STATS
            destination is KaniDestination.Browse -> Target.BROWSE
            destination == KaniDestination.Games -> Target.GAMES
            else -> Target.HOME
        }
        put(KEY_TARGET, target.wireName)
        if (destination is KaniDestination.Detail) {
            put(KEY_KANJI, destination.kanji)
        }
    }
}

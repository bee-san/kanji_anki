package dev.bee.kanjianki.presentation

/**
 * Every screen either host can show, as data rather than a route string.
 *
 * The Android host currently keeps these as `MainActivityBase.NAV_*` string
 * constants and rebuilds their arguments from an `android.os.Bundle`. Neither
 * crosses to a desktop host, and neither makes an invalid combination
 * unrepresentable — `"settings/dispaly-data"` is a perfectly good `String`.
 *
 * The [route] values are unchanged from the Android constants on purpose. They
 * are already persisted in saved instance state and referenced by instrumentation
 * test tags (`kani-nav-<route>`), so renaming them would be a silent
 * compatibility break for no gain.
 *
 * Durable encoding is not here — see [KaniDestinationCodec]. A destination
 * describes where the user is; the codec owns how that survives a restart.
 */
sealed interface KaniDestination {
    /** The stable wire name for this destination, arguments excluded. */
    val route: String

    /** The top-level tab this destination belongs under. */
    val tab: KaniTab

    /**
     * Where back goes from here, or `null` at the root of the app.
     *
     * A property rather than Android's `settingsParentRoute` string surgery
     * (`route.substringBeforeLast('/')`), which silently resolves an unrecognized
     * route to Home. `null` means "leave the app or close the window" — which of
     * those it is stays the host's decision.
     */
    val parent: KaniDestination?

    data object Home : KaniDestination {
        override val route: String = "home"
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination? = null
    }

    data object Study : KaniDestination {
        override val route: String = "study"
        override val tab: KaniTab = KaniTab.STUDY
        override val parent: KaniDestination = Home
    }

    data object Stats : KaniDestination {
        override val route: String = "stats"
        override val tab: KaniTab = KaniTab.STATS
        override val parent: KaniDestination = Home
    }

    /**
     * Settings and its subpages.
     *
     * One destination carrying a [SettingsSection] rather than nine objects,
     * because the point of Android's `isSettingsRoute`/`settingsParentRoute`
     * helpers was to treat this family uniformly, and the section already knows
     * its own parent.
     */
    data class Settings(
        val section: SettingsSection = SettingsSection.ROOT,
    ) : KaniDestination {
        override val route: String = section.route
        override val tab: KaniTab = KaniTab.SETTINGS
        override val parent: KaniDestination =
            section.parent?.let(::Settings) ?: Home
    }

    /** The Study focus queue, reached from Home. */
    data object FocusQueue : KaniDestination {
        override val route: String = "focus-queue"
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination = Home
    }

    data object RecentMistakes : KaniDestination {
        override val route: String = "recent-mistakes"
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination = Home
    }

    /**
     * The Games menu.
     *
     * Game *rounds* deliberately have no destination: their in-memory questions
     * are cosmetic session state, not a durable payload, so a restored process
     * lands back on the menu. That was already true on Android and is preserved
     * rather than reinvented.
     */
    data object Games : KaniDestination {
        override val route: String = "games"
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination = Home
    }

    data object MissingKanji : KaniDestination {
        override val route: String = "missing-kanji"
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination = Home
    }

    data class Browse(
        val query: String = "",
        val onlySimilarKanji: Boolean = false,
        val allKanjiScope: Boolean = false,
        val showSuspended: Boolean = false,
    ) : KaniDestination {
        override val route: String = ROUTE
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination = Home

        companion object {
            const val ROUTE: String = "browse"
        }
    }

    data class Detail(
        val kanji: String,
        val fromBrowse: Boolean = false,
        val query: String = "",
        val onlySimilarKanji: Boolean = false,
        val allKanjiScope: Boolean = false,
        val showSuspended: Boolean = false,
    ) : KaniDestination {
        override val route: String = ROUTE
        override val tab: KaniTab = KaniTab.HOME

        /**
         * Back returns to the search that found this kanji, when there was one.
         *
         * Dropping the query on the way back is the difference between "close
         * this card" and "lose my search", and Android already threaded these
         * fields through restoration for exactly that reason.
         */
        override val parent: KaniDestination = if (fromBrowse) {
            Browse(
                query = query,
                onlySimilarKanji = onlySimilarKanji,
                allKanjiScope = allKanjiScope,
                showSuspended = showSuspended,
            )
        } else {
            Home
        }

        init {
            require(kanji.isNotBlank()) { "detail destination needs a kanji" }
        }

        companion object {
            const val ROUTE: String = "detail"
        }
    }

    /**
     * A kanji the user has not imported, opened from an all-kanji search.
     *
     * Separate from [Detail] rather than a flag on it, because it has no study
     * state to edit. Conflating them is how a read-only screen grows an edit
     * affordance.
     */
    data class ReadOnlyDetail(
        val kanji: String,
        val query: String = "",
    ) : KaniDestination {
        override val route: String = ROUTE
        override val tab: KaniTab = KaniTab.HOME
        override val parent: KaniDestination =
            Browse(query = query, allKanjiScope = true)

        init {
            require(kanji.isNotBlank()) { "read-only detail destination needs a kanji" }
        }

        companion object {
            const val ROUTE: String = "read-only-detail"
        }
    }
}

/** The four bottom-bar/rail tabs, which own every destination between them. */
enum class KaniTab(val route: String) {
    HOME("home"),
    STUDY("study"),
    STATS("stats"),
    SETTINGS("settings"),
    ;

    /** The destination selecting this tab lands on. */
    val root: KaniDestination
        get() = when (this) {
            HOME -> KaniDestination.Home
            STUDY -> KaniDestination.Study
            STATS -> KaniDestination.Stats
            SETTINGS -> KaniDestination.Settings()
        }
}

/**
 * Settings' page tree.
 *
 * Each section knows its [parent], so an unrepresentable parent cannot be
 * constructed — unlike deriving one by chopping a route string.
 */
enum class SettingsSection(val route: String) {
    ROOT("settings"),
    IMPORT_SYNC("settings/import-sync"),
    STUDY_BEHAVIOR("settings/study-behavior"),
    AUTOMATION("settings/automation"),
    APPEARANCE("settings/appearance"),
    DISPLAY_DATA("settings/display-data"),
    UPDATE("settings/automation/update"),
    LICENSES("settings/display-data/licenses"),
    HOW_IT_WORKS("settings/display-data/how-kani-works"),
    ;

    /** The section one level up, or `null` at [ROOT], whose parent is Home. */
    val parent: SettingsSection?
        get() = when (this) {
            ROOT -> null
            IMPORT_SYNC, STUDY_BEHAVIOR, AUTOMATION, APPEARANCE, DISPLAY_DATA -> ROOT
            UPDATE -> AUTOMATION
            LICENSES, HOW_IT_WORKS -> DISPLAY_DATA
        }
}

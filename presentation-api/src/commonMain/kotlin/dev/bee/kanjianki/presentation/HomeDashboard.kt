package dev.bee.kanjianki.presentation

/**
 * Which of the three Home tiles a metric is.
 *
 * The Android model carried `iconRes: Int` and `accent: Int` — an Android drawable
 * id and a packed ARGB from the legacy palette. Neither crosses to a desktop host,
 * and neither says what the tile *is*, so the label had to travel separately and
 * `homeMetricCardTestTag(label)` keyed the test tag off translated copy. Naming the
 * tile instead lets the icon, the accent, the label, and the tag all be derived
 * where they belong.
 */
enum class HomeMetricKind {
    /** When the collection was last synced, and whether that is current. */
    SYNC,

    /** Consecutive study days. */
    STREAK,

    /** What the adaptive plan is working on. */
    FOCUS,
}

/**
 * The meaning behind a colour, rather than the colour.
 *
 * `HomeFocusQueueCardModel.accentColor` was an ARGB `Int` with a comment telling
 * the renderer to resolve it through `kaniColor` — which means the model had already
 * decided the hue, in a palette the active theme might not use. These are the four
 * distinctions Home actually draws, and `:feature-home` maps them to the live theme.
 */
enum class HomeAccent {
    /** Nothing notable; the calm default. */
    NEUTRAL,

    /** Work waiting now — a due card, a sync that has not happened. */
    DUE,

    /** In progress: learning or relearning. */
    LEARNING,

    /** Deliberately not due yet, and fine. */
    RESTING,
}

/** One Home tile: what it measures, the figure, and an optional line under it. */
data class HomeMetric(
    val kind: HomeMetricKind,
    val value: UiText,
    val detail: UiText = UiText.EMPTY,
    val accent: HomeAccent = HomeAccent.NEUTRAL,
    /**
     * What tapping the tile does, or `null` for a tile that only reports.
     *
     * Only the sync tile was tappable on Android. Modelled per-tile rather than as
     * "the first one is a button" so a screen reader is told which tiles are
     * actionable and a test can assert the streak tile is not one.
     */
    val action: KaniAction? = null,
)

/** A short badge on a focus card: the rung, or that the item is relearning. */
data class FocusTag(
    val label: UiText,
    val accent: HomeAccent = HomeAccent.NEUTRAL,
)

/**
 * One kanji in the focus queue, as the Home preview and the full queue both show it.
 *
 * [action] is derived rather than passed. The Android model took an
 * `onClick: () -> Unit` that every call site built as
 * `renderDetail(row.kanji, false, "")`, and a call site that built it as
 * `renderDetail(otherKanji, …)` would have compiled fine and opened the wrong
 * kanji. Deriving it from [kanji] makes that unrepresentable.
 */
data class FocusCard(
    val kanji: String,
    val meaning: UiText,
    val sourceEvidence: UiText = UiText.EMPTY,
    val reasonLine: UiText = UiText.EMPTY,
    val body: UiText = UiText.EMPTY,
    val tags: List<FocusTag> = emptyList(),
    val accent: HomeAccent = HomeAccent.NEUTRAL,
) {
    init {
        require(kanji.isNotBlank()) { "a focus card is about a kanji" }
    }

    /** Opening this card's kanji. Not reached from Browse, so back returns Home. */
    val action: KaniAction
        get() = KaniAction.Navigation.Open(KaniDestination.Detail(kanji = kanji))
}

/**
 * Why the focus queue has nothing in it.
 *
 * The two cases need different copy and the Android code told them apart by
 * checking `rows.isEmpty()` against `entries.isEmpty()` at four separate call
 * sites — the empty title, the empty body, `showSyncCta`, and whether to offer
 * "View all". Naming the reason once means those four cannot disagree.
 */
enum class FocusEmptyReason {
    /** Nothing has been imported, so the remedy is a sync. */
    NOTHING_IMPORTED,

    /** Kanji are imported but none is in active practice yet. */
    NOTHING_ACTIVE,
}

/**
 * The focus queue, and enough context to explain an empty one.
 *
 * [hasImportedKanji] is separate from `cards.isEmpty()` on purpose: an empty queue
 * on a full collection is a normal resting state, while an empty queue on an empty
 * collection is a sync that has not happened. The two look identical from the card
 * list alone.
 */
data class FocusQueue(
    val plan: UiText = UiText.EMPTY,
    val cards: List<FocusCard> = emptyList(),
    val hasImportedKanji: Boolean = cards.isNotEmpty(),
) {
    val emptyReason: FocusEmptyReason?
        get() = when {
            cards.isNotEmpty() -> null
            hasImportedKanji -> FocusEmptyReason.NOTHING_ACTIVE
            else -> FocusEmptyReason.NOTHING_IMPORTED
        }

    /** Whether the section header offers the full-queue screen. */
    val showsViewAll: Boolean
        get() = cards.isNotEmpty()

    /** Opening the full focus queue, for the "View all" affordance. */
    val viewAllAction: KaniAction
        get() = KaniAction.Navigation.Open(KaniDestination.FocusQueue)
}

/**
 * What Kani suggests doing next, as a decision rather than a label.
 *
 * `:core`'s `RecommendedAction` has the same four cases and is plain-JVM, so this
 * restates it for the same reason `OnboardingPolicy` restates
 * `HomeImportOnboardingPolicy`. The mapping to an action is here rather than in each
 * host: Android built it as `onStudy`/`onSync`/`null` lambdas and a host that wired
 * `SYNC_FIRST` to study would have compiled.
 */
enum class HomeRecommendation {
    STUDY_NOW,
    SYNC_FIRST,
    WAIT_UNTIL_LATER,
    NOTHING_USEFUL_NOW,
    ;

    /**
     * The one thing the Today card offers, or `null` when nothing would help.
     *
     * `SYNC_FIRST` asks for the confirmation rather than starting a sync, because
     * every sync in Kani is user-confirmed — see [KaniAction.Provider.RequestSync].
     */
    val action: KaniAction?
        get() = when (this) {
            STUDY_NOW -> KaniAction.Navigation.Open(KaniDestination.Study)
            SYNC_FIRST -> KaniAction.Provider.RequestSync
            WAIT_UNTIL_LATER, NOTHING_USEFUL_NOW -> null
        }
}

/**
 * The Today card: one sentence, optional supporting lines, one recommendation.
 *
 * [summary] and [details] are host-computed prose about the user's own collection —
 * `:core`'s `DailyStudyPlanPolicy` produces them on Android — so they arrive as
 * resolved text rather than as keys, exactly like a provider's own failure message.
 */
data class TodayPlan(
    val recommendation: HomeRecommendation,
    val summary: UiText = UiText.EMPTY,
    val details: List<UiText> = emptyList(),
) {
    /**
     * True when the card would render an empty box.
     *
     * The Android screen tested the same three conditions inline before deciding
     * whether to compose the card at all; putting it here means the desktop host
     * cannot forget the check and show a titled card with nothing in it.
     */
    val isEmpty: Boolean
        get() = summary == UiText.EMPTY &&
            details.isEmpty() &&
            recommendation.action == null
}

/**
 * Everything Home renders, with no lambdas and no platform resource ids.
 *
 * This is the portable replacement for `HomeScreenModel`, which carried six
 * `() -> Unit` callbacks, `Int` drawable ids, and pre-translated `String`s. The
 * callbacks are the important difference: a lambda cannot be compared, so a test
 * could only assert that *something* was invoked, and the desktop host would have
 * had to supply eleven of them correctly with nothing checking that it had.
 *
 * Absent on purpose: the update-check banner and the first-run offline notice.
 * Both are host-specific surfaces (Goals 198 and 201 own them) and neither is part
 * of the shared Home contract.
 */
data class HomeDashboard(
    val readiness: ProviderReadiness = ProviderReadiness.ABSENT,
    val metrics: List<HomeMetric> = emptyList(),
    val todayPlan: TodayPlan? = null,
    val deckOverview: List<UiText> = emptyList(),
    val focus: FocusQueue = FocusQueue(),
    /** Kanji already tagged as repaired in the collection, for the hand-off card. */
    val repairedKanjiCount: Int = 0,
    /** Cards the next study session will serve, for the count on the study button. */
    val studyRemainingCount: Int = 0,
    /** True while a sync is running, which disables the primary action. */
    val syncing: Boolean = false,
) {
    init {
        require(repairedKanjiCount >= 0) { "repaired kanji count must not be negative" }
        require(studyRemainingCount >= 0) { "study remaining count must not be negative" }
    }

    /**
     * The single primary action: sync when there is nothing to study, else study.
     *
     * One action rather than two buttons, matching what Android did with
     * `showSyncCta` — and for the reason its comment gave: the two share a footprint
     * so the primary action does not jump when the sync state flips.
     */
    val primaryAction: KaniAction
        get() = if (needsFirstSync) {
            KaniAction.Provider.RequestSync
        } else {
            KaniAction.Navigation.Open(KaniDestination.Study)
        }

    /** True when the primary action is a sync rather than a study session. */
    val needsFirstSync: Boolean
        get() = !focus.hasImportedKanji
}

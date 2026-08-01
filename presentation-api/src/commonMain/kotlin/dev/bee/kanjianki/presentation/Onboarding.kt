package dev.bee.kanjianki.presentation

/**
 * How far the user has got towards a first successful import, as a value.
 *
 * This restates `HomeImportOnboardingPolicy` from `:core` portably, and the
 * restatement is not incidental. That policy is a plain-JVM object whose inputs are
 * `providerInstalled`/`permissionGranted`/`permissionName` and whose copy names
 * AnkiDroid and Android runtime permissions; `:presentation-api` cannot depend on a
 * JVM module, and even if it could, none of those three inputs means anything on a
 * host that talks to Anki over AnkiConnect. There is no "install AnkiDroid" step and
 * no runtime permission there — there is an app that may not be running and a
 * connection that may not be authorized.
 *
 * So the inputs here are the host-neutral shape of the same question — is a
 * collection reachable, is Kani allowed to read it, is anything selected to import,
 * what happened last time — and the wording is resolved by `:feature-home` from its
 * own Compose resources, with the host supplying the one line only it can know (the
 * permission it needs, or what AnkiConnect actually said).
 *
 * The step order is deliberately the same as the Android policy's, because it is the
 * order the user has to satisfy them in and reordering would change a shipped flow.
 */
enum class OnboardingStep {
    /**
     * Nothing is there to talk to yet.
     *
     * Android's "install AnkiDroid"; desktop's "start Anki". The remedy differs by
     * host, which is why the step is named for the state and not the fix.
     */
    CONNECT_PROVIDER,

    /** A collection is reachable but Kani has not been granted access to it. */
    AUTHORIZE_PROVIDER,

    /** Access is granted, but nothing has been selected to import. */
    CHOOSE_SOURCE,

    /** Everything is configured and no sync has run yet. */
    READY_FIRST_SYNC,

    /**
     * The last sync failed for an authorization reason.
     *
     * Separate from [RECOVER_SYNC] because the remedy is different: re-granting
     * access, not checking which cards are being imported. The Android policy
     * decided this by substring-matching `"permission"` in the error text, which
     * only works for one host's English wording; here the host classifies the
     * failure into a [PresentationFailure.Kind] and this reads the kind.
     */
    RECOVER_AUTHORIZATION,

    /** The last sync failed for some other reason. */
    RECOVER_SYNC,

    /** The last sync succeeded. */
    SYNCED,
}

/**
 * Whether a collection provider is reachable, and whether Kani may read it.
 *
 * Three states rather than the two booleans the Android policy took, because
 * `providerInstalled=false, permissionGranted=true` is a combination that cannot
 * happen and should not be expressible. It is supplied by the host rather than
 * derived from [PlatformCapability.PROVIDER_CONNECTIVITY], which collapses
 * [ABSENT] and [UNAUTHORIZED] into one absent capability and so cannot tell the
 * user which of the two things to do.
 */
enum class ProviderReadiness {
    /** No collection app is installed, running, or configured to talk to. */
    ABSENT,

    /** One is reachable, but Kani has not been granted access. */
    UNAUTHORIZED,

    /** Reachable and authorized. */
    READY,
}

/** A kind of card the user has chosen to import. */
enum class ImportSource {
    ACTIVE_CARDS,
    SUSPENDED_CARDS,
    TAGGED_CARDS,
    WEAK_CARDS,
    BROWSER_QUERY,
}

/**
 * What the user has told Kani to import, and from which note type.
 *
 * [BROWSER_QUERY][ImportSource.BROWSER_QUERY] carries a required [browserQuery]:
 * `Settings.browserQueryImportEnabled()` already treats a blank query as the source
 * being off, and making that a constructor requirement means a caller cannot
 * accidentally report a source that would import nothing.
 */
data class CollectionBinding(
    val noteType: String,
    val sources: Set<ImportSource> = emptySet(),
    val browserQuery: String = "",
) {
    init {
        require(ImportSource.BROWSER_QUERY !in sources || browserQuery.isNotBlank()) {
            "a browser-query import source needs a query"
        }
    }

    /** True when at least one source would import something. */
    val hasSource: Boolean
        get() = sources.isNotEmpty()
}

/**
 * What the most recent sync did, if one ran.
 *
 * A sealed set rather than the Android policy's `(status: String, importedKanji:
 * Int, errorMessage: String)` triple, where a `"success"` carrying an error message
 * and a `"failed"` carrying an import count are both constructible. Here the
 * count exists only on success and the failure only on failure.
 */
sealed interface SyncOutcome {
    /** No sync has ever completed. */
    data object Never : SyncOutcome

    data class Succeeded(val importedKanji: Int) : SyncOutcome {
        init {
            require(importedKanji >= 0) { "imported kanji cannot be negative" }
        }
    }

    data class Failed(val failure: PresentationFailure) : SyncOutcome
}

/**
 * The wording only the host can supply for the step it is on.
 *
 * The steps whose remedy is host-specific — connect, authorize, recover
 * authorization — cannot be worded portably. "Install AnkiDroid" and "Start Anki"
 * are different instructions for the same state, and on Android the sentence has to
 * name the runtime permission it is about to request. Everything else about the
 * screen is shared, so this is deliberately just the two pieces that differ: the
 * explanatory line and the button.
 *
 * Both default to [UiText.EMPTY], and blank means "use the shared wording" — the
 * same host-wins-else-fall-back rule `ShellCopy.failureMessage` already applies to
 * failure text. That keeps a host that has nothing special to say from having to
 * restate the generic copy, and keeps the generic copy honest enough to stand alone.
 *
 * [UiText] rather than `String` because the host resolves it against its own
 * resources: Android from its string table, desktop from
 * `AnkiConnectStatusMapping.messageFor`, which already distinguishes "Anki is not
 * reachable" from "this AnkiConnect is too old for Kani".
 */
data class HostOnboardingCopy(
    val guidance: UiText = UiText.EMPTY,
    val primaryActionLabel: UiText = UiText.EMPTY,
) {
    companion object {
        /** For a host with nothing host-specific to add on this step. */
        val NONE = HostOnboardingCopy()
    }
}

/**
 * The onboarding step to show, and what its one button does.
 *
 * Carries no resolved shared copy. The Android policy returned a `Plan` whose
 * `body()` was already an English or Japanese sentence, built by branching on
 * `Locale.getDefault()`; that mechanism does not exist in common code and does not
 * extend past two languages. What travels instead is the step plus the data the
 * sentence needs — the count, the sources, the note type — and `:feature-home`
 * assembles the sentence from its own resources. The exception is [hostCopy], which
 * is the part no shared resource table could contain.
 */
data class OnboardingPlan(
    val step: OnboardingStep,
    val binding: CollectionBinding,
    val hostCopy: HostOnboardingCopy = HostOnboardingCopy.NONE,
    val importedKanji: Int = 0,
    val failure: PresentationFailure? = null,
    val repairedKanjiCount: Int = 0,
) {
    /**
     * What the primary button dispatches.
     *
     * A value rather than a callback, so a test can assert that the button on a
     * `CHOOSE_SOURCE` screen navigates and the button on a `SYNCED` screen asks for
     * a sync, without either happening. [OnboardingStep.CHOOSE_SOURCE] opens
     * Settings' root rather than its Import & sync section, matching what the
     * Android host does today; the section would be a better destination and is a
     * deliberate non-change here, because Goal 194 keeps Android behavior accepted.
     */
    val primaryAction: KaniAction
        get() = when (step) {
            OnboardingStep.CONNECT_PROVIDER -> KaniAction.Provider.Connect
            OnboardingStep.AUTHORIZE_PROVIDER,
            OnboardingStep.RECOVER_AUTHORIZATION,
            -> KaniAction.Provider.Authorize
            OnboardingStep.CHOOSE_SOURCE ->
                KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.ROOT))
            OnboardingStep.READY_FIRST_SYNC,
            OnboardingStep.RECOVER_SYNC,
            OnboardingStep.SYNCED,
            -> KaniAction.Provider.RequestSync
        }

    /**
     * True where the repaired-tagging line belongs.
     *
     * The three sync-capable steps only, which is where the Android policy appended
     * it. On a step whose remedy is granting access or choosing a source, promising
     * a tag write-back that cannot happen yet would be misleading.
     */
    val showsRepairedTagging: Boolean
        get() = repairedKanjiCount > 0 &&
            step in
            setOf(
                OnboardingStep.READY_FIRST_SYNC,
                OnboardingStep.RECOVER_SYNC,
                OnboardingStep.SYNCED,
            )
}

/**
 * Resolves the onboarding step from the host's answers.
 *
 * Pure, so both hosts reach the same step from the same facts and a test can walk
 * every branch without a provider, a database, or a window.
 */
object OnboardingPolicy {
    /**
     * The step to show.
     *
     * Evaluated in the order the user has to satisfy the conditions: there is no
     * point telling someone to pick import sources while Kani still cannot read
     * their collection, and no point reporting a stale sync failure once access has
     * been revoked — the revoked access is the thing to fix.
     *
     * [repairedKanjiCount] is the number of repaired kanji this sync would tag, and
     * zero means "nothing to tag" whether that is because the
     * `tag_repaired_cards` setting is off or because no kanji qualify. The count is
     * shown so the confirmation states it, which is what makes the write-back
     * manual-confirm-only rather than a silent side effect of syncing.
     */
    fun plan(
        readiness: ProviderReadiness,
        binding: CollectionBinding,
        sync: SyncOutcome = SyncOutcome.Never,
        hostCopy: HostOnboardingCopy = HostOnboardingCopy.NONE,
        repairedKanjiCount: Int = 0,
    ): OnboardingPlan {
        val step = step(readiness, binding, sync)
        return OnboardingPlan(
            step = step,
            binding = binding,
            hostCopy = hostCopy,
            importedKanji = (sync as? SyncOutcome.Succeeded)?.importedKanji ?: 0,
            failure = (sync as? SyncOutcome.Failed)?.failure,
            repairedKanjiCount = repairedKanjiCount.coerceAtLeast(0),
        )
    }

    private fun step(
        readiness: ProviderReadiness,
        binding: CollectionBinding,
        sync: SyncOutcome,
    ): OnboardingStep = when {
        readiness == ProviderReadiness.ABSENT -> OnboardingStep.CONNECT_PROVIDER
        readiness == ProviderReadiness.UNAUTHORIZED -> OnboardingStep.AUTHORIZE_PROVIDER
        !binding.hasSource -> OnboardingStep.CHOOSE_SOURCE
        sync is SyncOutcome.Succeeded -> OnboardingStep.SYNCED
        sync is SyncOutcome.Failed ->
            if (sync.failure.kind == PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED) {
                OnboardingStep.RECOVER_AUTHORIZATION
            } else {
                OnboardingStep.RECOVER_SYNC
            }
        else -> OnboardingStep.READY_FIRST_SYNC
    }

    /**
     * The confirmation a manual sync must pass through.
     *
     * The only producer of [KaniAction.Provider.ConfirmSync], which is what makes
     * "a sync is never started without the user answering a dialog" a property of
     * the model rather than a convention each host follows. It matters most for the
     * repaired-note tag write-back: CLAUDE.md requires that write to be
     * manual-confirm-only with the proposal count visible, and the automatic sync
     * runner is not authorized to perform it at all.
     *
     * Not destructive: the write surface behind this is note tags, which are
     * additive and idempotent. Marking it destructive would style a routine import
     * as a warning.
     */
    fun syncConfirmation(copy: SyncConfirmCopy): KaniEffect.Confirm = KaniEffect.Confirm(
        title = copy.title,
        body = copy.body,
        confirmLabel = copy.confirmLabel,
        dismissLabel = copy.dismissLabel,
        confirm = KaniAction.Provider.ConfirmSync,
        isDestructive = false,
    )
}

/**
 * The already-resolved wording of the sync confirmation.
 *
 * Resolved by the caller because the copy lives in `:feature-home`'s Compose
 * resources and assembling it needs a composable `stringResource`, which a pure
 * policy function cannot call.
 */
data class SyncConfirmCopy(
    val title: UiText,
    val body: UiText,
    val confirmLabel: UiText,
    val dismissLabel: UiText,
)

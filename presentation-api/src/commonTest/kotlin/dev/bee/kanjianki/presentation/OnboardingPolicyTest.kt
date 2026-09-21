package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OnboardingPolicyTest {
    @Test
    fun aHostWithNothingToTalkToIsToldToConnectFirst() {
        // Even with sources chosen and a past success, an absent provider is the
        // only actionable thing: nothing else can be attempted until there is a
        // collection to read.
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.ABSENT,
            binding = fullyConfigured,
            sync = SyncOutcome.Succeeded(importedKanji = 400),
        )

        assertEquals(OnboardingStep.CONNECT_PROVIDER, plan.step)
        assertEquals(KaniAction.Provider.Connect, plan.primaryAction)
    }

    @Test
    fun aReachableButUnauthorizedProviderAsksForAccessRatherThanInstallation() {
        // The distinction the Android policy's two booleans could express and a
        // single `PROVIDER_CONNECTIVITY` capability cannot: the app is there, the
        // access is not, and telling the user to install it would be wrong.
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.UNAUTHORIZED,
            binding = fullyConfigured,
        )

        assertEquals(OnboardingStep.AUTHORIZE_PROVIDER, plan.step)
        assertEquals(KaniAction.Provider.Authorize, plan.primaryAction)
    }

    @Test
    fun anAuthorizedProviderWithNoSourcesSendsTheUserToSettings() {
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = CollectionBinding(noteType = "Kiku"),
        )

        assertEquals(OnboardingStep.CHOOSE_SOURCE, plan.step)
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.ROOT)),
            plan.primaryAction,
        )
    }

    @Test
    fun aFullyConfiguredHostThatHasNeverSyncedIsReadyToSync() {
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = fullyConfigured,
            sync = SyncOutcome.Never,
        )

        assertEquals(OnboardingStep.READY_FIRST_SYNC, plan.step)
        assertEquals(KaniAction.Provider.RequestSync, plan.primaryAction)
    }

    @Test
    fun aSuccessfulSyncReportsItsCountAndStillOffersAnother() {
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = fullyConfigured,
            sync = SyncOutcome.Succeeded(importedKanji = 1_284),
        )

        assertEquals(OnboardingStep.SYNCED, plan.step)
        assertEquals(1_284, plan.importedKanji)
        assertNull(plan.failure)
        assertEquals(KaniAction.Provider.RequestSync, plan.primaryAction)
    }

    @Test
    fun anAuthFailureRoutesToReAuthorizationAndNotToSourceSettings() {
        // The Android policy decided this by looking for the substring "permission"
        // in the error message, which is a property of one host's English wording.
        // Reading the classified kind works for AnkiConnect's
        // "Anki has not granted Kani access yet" too, which contains no such word.
        val failure = PresentationFailure(
            kind = PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED,
            message = UiText.Literal("Anki has not granted Kani access yet."),
        )

        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = fullyConfigured,
            sync = SyncOutcome.Failed(failure),
        )

        assertEquals(OnboardingStep.RECOVER_AUTHORIZATION, plan.step)
        assertEquals(KaniAction.Provider.Authorize, plan.primaryAction)
        assertSame(failure, plan.failure)
    }

    @Test
    fun everyOtherFailureKindRoutesToOrdinarySyncRecovery() {
        // Exhaustive over the kinds rather than a sample, so a new kind must be
        // classified deliberately instead of silently landing in one branch.
        val nonAuth = PresentationFailure.Kind.entries -
            PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED

        for (kind in nonAuth) {
            val plan = OnboardingPolicy.plan(
                readiness = ProviderReadiness.READY,
                binding = fullyConfigured,
                sync = SyncOutcome.Failed(PresentationFailure(kind = kind)),
            )

            assertEquals(OnboardingStep.RECOVER_SYNC, plan.step, kind.name)
            assertEquals(KaniAction.Provider.RequestSync, plan.primaryAction, kind.name)
        }
    }

    @Test
    fun losingAccessAfterASuccessfulSyncShowsTheLostAccessAndNotTheStaleSuccess() {
        // The ordering that matters most in practice: the user syncs happily for a
        // week, then revokes the permission or closes Anki. Reporting the old
        // success would leave a Sync button that cannot work.
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.UNAUTHORIZED,
            binding = fullyConfigured,
            sync = SyncOutcome.Succeeded(importedKanji = 900),
        )

        assertEquals(OnboardingStep.AUTHORIZE_PROVIDER, plan.step)
    }

    @Test
    fun everyStepReachesExactlyOnePrimaryActionAndAllOfThemAreReachable() {
        // A step with no way to reach it is dead copy; a step whose button does
        // nothing is a dead end. This pins both directions at once.
        val reached = mutableMapOf<OnboardingStep, KaniAction>()
        for (case in allCases) {
            val plan = OnboardingPolicy.plan(
                readiness = case.readiness,
                binding = case.binding,
                sync = case.sync,
            )
            reached[plan.step] = plan.primaryAction
        }

        assertEquals(OnboardingStep.entries.toSet(), reached.keys)
    }

    @Test
    fun theHostGuidanceAndButtonLabelTravelThroughUntouched() {
        // The two strings the shared model cannot author. On desktop the guidance is
        // AnkiConnect's own diagnosis and the button says "Start Anki"; on Android
        // it names the runtime permission and says "Install AnkiDroid".
        val hostCopy = HostOnboardingCopy(
            guidance = UiText.Literal("Anki reported AnkiConnect API v5; Kani needs v6."),
            primaryActionLabel = UiText.Literal("Update AnkiConnect"),
        )

        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.UNAUTHORIZED,
            binding = fullyConfigured,
            hostCopy = hostCopy,
        )

        assertEquals(hostCopy, plan.hostCopy)
    }

    @Test
    fun hostCopyIsOptionalSoAHostWithNothingToAddFallsBackToSharedWording() {
        // Blank means "use the shared copy", the same host-wins-else-fall-back rule
        // ShellCopy.failureMessage already applies to failure text.
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = fullyConfigured,
        )

        assertEquals(UiText.EMPTY, plan.hostCopy.guidance)
        assertEquals(UiText.EMPTY, plan.hostCopy.primaryActionLabel)
        assertEquals(HostOnboardingCopy.NONE, plan.hostCopy)
    }

    @Test
    fun repairedTaggingIsAnnouncedOnlyWhereASyncCanActuallyPerformIt() {
        for (case in allCases) {
            val plan = OnboardingPolicy.plan(
                readiness = case.readiness,
                binding = case.binding,
                sync = case.sync,
                repairedKanjiCount = 12,
            )

            val syncable = plan.primaryAction == KaniAction.Provider.RequestSync
            assertEquals(syncable, plan.showsRepairedTagging, plan.step.name)
        }
    }

    @Test
    fun noRepairedKanjiMeansNoRepairedTaggingLineAnywhere() {
        // Zero covers both "the setting is off" and "nothing qualifies"; neither
        // should promise a write-back.
        for (case in allCases) {
            val plan = OnboardingPolicy.plan(
                readiness = case.readiness,
                binding = case.binding,
                sync = case.sync,
                repairedKanjiCount = 0,
            )

            assertFalse(plan.showsRepairedTagging, plan.step.name)
        }
    }

    @Test
    fun aNegativeRepairedCountIsClampedRatherThanRendered() {
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = fullyConfigured,
            repairedKanjiCount = -4,
        )

        assertEquals(0, plan.repairedKanjiCount)
        assertFalse(plan.showsRepairedTagging)
    }

    @Test
    fun aBrowserQuerySourceCannotBeReportedWithoutAQuery() {
        // Matches how `Settings.browserQueryImportEnabled()` already behaves: a
        // blank query means the source is off. Rejecting the combination here stops
        // a caller from reporting a configured source that would import nothing and
        // so skipping the CHOOSE_SOURCE step it should have shown.
        assertFailsWith<IllegalArgumentException> {
            CollectionBinding(
                noteType = "Kiku",
                sources = setOf(ImportSource.BROWSER_QUERY),
                browserQuery = "   ",
            )
        }
    }

    @Test
    fun aBrowserQueryIsCarriedVerbatimIncludingItsAnkiSyntax() {
        // Goal 194 requires browser queries stay exact. Anki's search syntax is
        // whitespace- and quote-significant, so any normalization here would change
        // which cards the user's own query matches.
        val query = "\"deck:Mining::Kanji\" -is:suspended tag:kani_repaired"

        val binding = CollectionBinding(
            noteType = "Kiku",
            sources = setOf(ImportSource.BROWSER_QUERY),
            browserQuery = query,
        )

        assertEquals(query, binding.browserQuery)
        assertTrue(binding.hasSource)
        assertEquals(
            OnboardingStep.READY_FIRST_SYNC,
            OnboardingPolicy.plan(ProviderReadiness.READY, binding).step,
        )
    }

    @Test
    fun anySingleSourceIsEnoughToLeaveTheChooseSourceStep() {
        for (source in ImportSource.entries) {
            val binding = CollectionBinding(
                noteType = "Kiku",
                sources = setOf(source),
                browserQuery = if (source == ImportSource.BROWSER_QUERY) "deck:Mining" else "",
            )

            assertTrue(binding.hasSource, source.name)
            assertEquals(
                OnboardingStep.READY_FIRST_SYNC,
                OnboardingPolicy.plan(ProviderReadiness.READY, binding).step,
                source.name,
            )
        }
    }

    @Test
    fun theNoteTypeSurvivesToThePlanBecauseTheCopyNamesIt() {
        // Both the sync confirmation ("Kani keeps suspended <note type> cards on
        // device") and the synced summary name it, so it has to reach the renderer.
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = fullyConfigured,
        )

        assertEquals("Kiku", plan.binding.noteType)
    }

    @Test
    fun aNegativeImportCountIsRejectedAtTheSourceRatherThanRendered() {
        assertFailsWith<IllegalArgumentException> {
            SyncOutcome.Succeeded(importedKanji = -1)
        }
    }

    @Test
    fun aSyncIsOnlyEverStartedByAnsweringTheConfirmation() {
        // The invariant behind manual-confirm-only tag write-back: the button the
        // user presses asks for a dialog, and the dialog's accept action is the only
        // thing that starts a sync.
        val confirm = OnboardingPolicy.syncConfirmation(
            SyncConfirmCopy(
                title = UiText.Literal("Sync AnkiDroid"),
                body = UiText.Literal("This sync will also tag 12 repaired kanji."),
                confirmLabel = UiText.Literal("Sync cards"),
                dismissLabel = UiText.Literal("Cancel"),
            ),
        )

        assertEquals(KaniAction.Provider.ConfirmSync, confirm.confirm)
        assertEquals(
            KaniAction.Provider.RequestSync,
            OnboardingPolicy
                .plan(ProviderReadiness.READY, fullyConfigured)
                .primaryAction,
        )
    }

    @Test
    fun theSyncConfirmationIsNotStyledAsDestructive() {
        // The write surface behind it is note tags: additive and idempotent. A
        // destructive dialog would misrepresent an ordinary import as a warning.
        val confirm = OnboardingPolicy.syncConfirmation(
            SyncConfirmCopy(
                title = UiText.Literal("Sync AnkiDroid"),
                body = UiText.Literal("Sync now?"),
                confirmLabel = UiText.Literal("Sync cards"),
                dismissLabel = UiText.Literal("Cancel"),
            ),
        )

        assertFalse(confirm.isDestructive)
    }

    @Test
    fun theConfirmationCarriesTheCopyItWasGivenWithoutSubstitution() {
        val copy = SyncConfirmCopy(
            title = UiText.Key("sync_dialog_title"),
            body = UiText.Quantity("sync_dialog_repaired", count = 3),
            confirmLabel = UiText.Key("sync_dialog_confirm"),
            dismissLabel = UiText.Key("cancel"),
        )

        val confirm = OnboardingPolicy.syncConfirmation(copy)

        assertEquals(copy.title, confirm.title)
        assertEquals(copy.body, confirm.body)
        assertEquals(copy.confirmLabel, confirm.confirmLabel)
        assertEquals(copy.dismissLabel, confirm.dismissLabel)
    }

    @Test
    fun providerActionsLeaveTheShellStackAndTabAlone() {
        // A sync must not navigate. The user pressing Sync on a nested screen should
        // stay there and watch it, not be thrown back to a tab root.
        val state = ShellReducer.launch(
            request = null,
            restored = KaniDestination.Settings(SettingsSection.IMPORT_SYNC),
        )

        for (action in providerActions) {
            assertEquals(state, ShellReducer.reduce(state, action), action.toString())
        }
    }

    @Test
    fun providerActionsDoNotDisturbAlreadyLoadedRouteContent() {
        // Pressing Sync must not blank the dashboard. Content is replaced when the
        // sync finishes and the host reloads, not when the request is dispatched.
        val loaded = RouteState(
            destination = KaniDestination.Home,
            content = Loadable.Loaded("dashboard"),
        )

        for (action in providerActions) {
            val (state, intent) = RouteReducer.reduce(loaded, action)

            assertEquals(loaded, state, action.toString())
            assertNull(intent, action.toString())
        }
    }

    private val fullyConfigured = CollectionBinding(
        noteType = "Kiku",
        sources = setOf(ImportSource.ACTIVE_CARDS, ImportSource.SUSPENDED_CARDS),
    )

    private val providerActions = listOf(
        KaniAction.Provider.Connect,
        KaniAction.Provider.Authorize,
        KaniAction.Provider.RequestSync,
        KaniAction.Provider.ConfirmSync,
        KaniAction.Provider.CancelSync,
    )

    private data class Case(
        val readiness: ProviderReadiness,
        val binding: CollectionBinding,
        val sync: SyncOutcome,
    )

    /**
     * Every combination of the three inputs, at one representative value each.
     *
     * Enumerated rather than sampled so the "all steps reachable" and
     * "repaired-tagging only where syncable" properties are checked across the whole
     * input space, and a new [OnboardingStep] with no route into it fails.
     */
    private val allCases: List<Case> = ProviderReadiness.entries.flatMap { readiness ->
        listOf(CollectionBinding(noteType = "Kiku"), fullyConfigured).flatMap { binding ->
            listOf(
                SyncOutcome.Never,
                SyncOutcome.Succeeded(importedKanji = 7),
                SyncOutcome.Failed(
                    PresentationFailure(PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED),
                ),
                SyncOutcome.Failed(
                    PresentationFailure(PresentationFailure.Kind.PROVIDER_UNAVAILABLE),
                ),
            ).map { sync -> Case(readiness, binding, sync) }
        }
    }
}

package dev.bee.kanjianki.home

import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.FieldRole
import dev.bee.kanjianki.presentation.HostOnboardingCopy
import dev.bee.kanjianki.presentation.ImportSource
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingPolicy
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.SyncOutcome
import dev.bee.kanjianki.presentation.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The copy holder's substitution, assembly, and fallback rules.
 *
 * Built from marker strings rather than resolved resources: what is under test is
 * the assembly, not the shipped wording, and a test that asserted the wording would
 * fail every time a translator improved a sentence. Every template keeps its
 * `%n$s` placeholders so an unsubstituted one is visible in the failure message.
 */
class HomeCopyTest {
    @Test
    fun everyEnumEntryHasCopyRatherThanThrowingWhenAUserReachesIt() {
        // `getValue` throws on a missing key, so this is the test that a new enum
        // entry cannot ship without copy. The maps are built from `.entries`, which
        // is what makes the exhaustive `when`s in rememberHomeCopy meaningful.
        val copy = homeCopy()
        for (readiness in ProviderReadiness.entries) {
            assertTrue(copy.readinessLabel(readiness).isNotBlank(), "$readiness has no label")
        }
        for (role in FieldRole.entries) {
            assertTrue(copy.roleLabel(role).isNotBlank(), "$role has no label")
            assertTrue(copy.roleRequirement(role).isNotBlank(), "$role has no requirement")
        }
        for (source in ImportSource.entries) {
            assertTrue(copy.sourceLabel(source).isNotBlank(), "$source has no label")
        }
        for (step in OnboardingStep.entries) {
            val action = copy.stepAction(planAt(step), TestUiTextResolver)
            assertTrue(action.isNotBlank(), "$step has no button label")
        }
    }

    @Test
    fun onlyTheTwoRequiredRolesAreLabelledRequired() {
        val copy = homeCopy()
        val required = FieldRole.entries.filter { copy.roleRequirement(it) == copy.fieldRequired }
        assertEquals(
            setOf(FieldRole.EXPRESSION, FieldRole.MEANING),
            required.toSet(),
            "the requirement label must follow FieldRole.isRequired, not a second list",
        )
    }

    @Test
    fun aFieldProblemNamesTheRoleItIsAboutRatherThanJustFailing() {
        val copy = homeCopy()
        for (role in FieldRole.entries) {
            for (problem in listOf(copy.missingRequiredField(role), copy.staleField(role))) {
                assertTrue(copy.roleLabel(role) in problem, "$problem should name its role")
                assertFalse("%" in problem, "an unsubstituted placeholder survived: $problem")
            }
        }
    }

    @Test
    fun missingAndStaleAreDifferentSentencesBecauseTheRemediesDiffer() {
        // A role that was never picked and a role whose field was renamed in Anki
        // need different actions from the user, so one sentence for both would be
        // actively misleading.
        val copy = homeCopy()
        val missing = copy.missingRequiredField(FieldRole.MEANING)
        val stale = copy.staleField(FieldRole.MEANING)
        val same = missing == stale
        assertFalse(same, "missing and stale must read differently")
    }

    @Test
    fun aNoteTypeLabelCarriesBothItsNameAndItsFieldCount() {
        val label = homeCopy().noteTypeLabel(kikuOption(), "6 fields")
        assertEquals("Kiku (6 fields)", label)
        assertFalse("%" in label, "an unsubstituted placeholder survived: $label")
    }

    @Test
    fun theSourceLineListsSourcesInDeclarationOrderRatherThanSetOrder() {
        // A `Set` iterates in whatever order it was built in, so a line assembled
        // from the set directly reshuffles between renders for no reason the user
        // can see.
        val copy = homeCopy()
        val forwards = copy.sourceAndModelLine(
            CollectionBinding(
                noteType = "Kiku",
                sources = setOf(ImportSource.ACTIVE_CARDS, ImportSource.WEAK_CARDS),
            ),
        )
        val backwards = copy.sourceAndModelLine(
            CollectionBinding(
                noteType = "Kiku",
                sources = setOf(ImportSource.WEAK_CARDS, ImportSource.ACTIVE_CARDS),
            ),
        )
        assertEquals(forwards, backwards, "the source order must not depend on set order")
        assertEquals(
            "model=Kiku sources=source-ACTIVE_CARDS + source-WEAK_CARDS",
            forwards,
        )
    }

    @Test
    fun aBindingWithNoSourcesSaysNoneRatherThanLeavingAGap() {
        assertEquals(
            "model=Kiku sources=none",
            homeCopy().sourceAndModelLine(CollectionBinding(noteType = "Kiku")),
        )
    }

    @Test
    fun aBrowserQueryIsAppendedVerbatimIncludingItsAnkiSyntax() {
        // Kani hands this string to Anki unchanged, so quoting and negation must
        // survive display: a line that dropped `-is:suspended` would describe an
        // import the user is not about to perform.
        val query = "\"deck:Mining::Kanji\" -is:suspended tag:kani_repaired"
        val line = homeCopy().sourceAndModelLine(
            CollectionBinding(
                noteType = "Kiku",
                sources = setOf(ImportSource.BROWSER_QUERY),
                browserQuery = query,
            ),
        )
        assertTrue(query in line, "the query must appear verbatim: $line")
        assertEquals(
            "model=Kiku sources=source-BROWSER_QUERY query=$query",
            line,
        )
    }

    @Test
    fun aBindingWithoutABrowserQuerySourceRendersNoQueryClause() {
        val line = homeCopy().sourceAndModelLine(configuredBinding())
        assertFalse("query=" in line, "an empty query clause reads as a bug: $line")
    }

    @Test
    fun eachFixedStepBodyComesFromItsOwnEntry() {
        val copy = homeCopy()
        for (step in FIXED_BODY_STEPS) {
            assertEquals(
                "body-$step",
                copy.stepBody(planAt(step), TestUiTextResolver),
            )
        }
    }

    @Test
    fun theHostSentenceWinsOnEveryStepBecauseOnlyItCanNameTheRemedy() {
        // "Install AnkiDroid" and "Start Anki" are different instructions for the
        // same state, so the host must be able to override any step's body.
        val copy = homeCopy()
        for (step in OnboardingStep.entries) {
            val plan = planAt(
                step,
                hostCopy = HostOnboardingCopy(guidance = UiText.Literal("host says $step")),
            )
            assertEquals("host says $step", copy.stepBody(plan, TestUiTextResolver))
        }
    }

    @Test
    fun aBlankHostSentenceFallsBackToTheSharedWordingRatherThanRenderingEmpty() {
        val copy = homeCopy()
        val blank = HostOnboardingCopy(guidance = UiText.Literal("   "))
        for (step in OnboardingStep.entries) {
            val body = copy.stepBody(planAt(step, hostCopy = blank), TestUiTextResolver)
            assertTrue(body.isNotBlank(), "$step rendered an empty body")
        }
    }

    @Test
    fun anUnresolvableHostKeyFallsBackRatherThanBlankingTheCard() {
        // A host that names a key this resolver cannot answer is the same situation
        // as a host that said nothing, and must not empty the screen.
        val copy = homeCopy()
        val plan = planAt(
            OnboardingStep.CONNECT_PROVIDER,
            hostCopy = HostOnboardingCopy(guidance = UiText.Key("host.only.key")),
        )
        assertEquals("body-CONNECT_PROVIDER", copy.stepBody(plan, TestUiTextResolver))
    }

    @Test
    fun theHostButtonLabelWinsAndABlankOneFallsBack() {
        val copy = homeCopy()
        val named = planAt(
            OnboardingStep.AUTHORIZE_PROVIDER,
            hostCopy = HostOnboardingCopy(primaryActionLabel = UiText.Literal("Grant permission")),
        )
        assertEquals("Grant permission", copy.stepAction(named, TestUiTextResolver))
        assertEquals(
            "action-AUTHORIZE_PROVIDER",
            copy.stepAction(planAt(OnboardingStep.AUTHORIZE_PROVIDER), TestUiTextResolver),
        )
    }

    @Test
    fun theGuidanceAndTheButtonLabelOverrideIndependently() {
        // A host that only knows the instruction should not have to invent a button
        // label to say it, and vice versa.
        val copy = homeCopy()
        val bodyOnly = planAt(
            OnboardingStep.CONNECT_PROVIDER,
            hostCopy = HostOnboardingCopy(guidance = UiText.Literal("Install AnkiDroid")),
        )
        assertEquals("Install AnkiDroid", copy.stepBody(bodyOnly, TestUiTextResolver))
        assertEquals("action-CONNECT_PROVIDER", copy.stepAction(bodyOnly, TestUiTextResolver))
    }

    @Test
    fun theReadyBodyNamesTheNoteTypeItIsAboutToSync() {
        val copy = homeCopy()
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = configuredBinding(),
        )
        assertEquals(OnboardingStep.READY_FIRST_SYNC, plan.step)
        assertEquals("will sync Kiku", copy.stepBody(plan, TestUiTextResolver))
    }

    @Test
    fun theSyncedBodyPairsTheCountWithTheConfigurationItImportedUnder() {
        val copy = homeCopy()
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = configuredBinding(),
            sync = SyncOutcome.Succeeded(importedKanji = 412),
        )
        assertEquals(
            "imported 412 model=Kiku sources=source-ACTIVE_CARDS + source-SUSPENDED_CARDS",
            copy.stepBody(plan, TestUiTextResolver, HomeCountedCopy(syncedBody = "imported 412")),
        )
    }

    @Test
    fun aSyncedBodyWithNoResolvedCountStillNamesTheConfiguration() {
        // The count comes from a plural resource, which only a composable can ask
        // for. A caller that has not resolved one yet must not render a leading
        // space or an empty first line.
        val copy = homeCopy()
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = configuredBinding(),
            sync = SyncOutcome.Succeeded(importedKanji = 0),
        )
        val body = copy.stepBody(plan, TestUiTextResolver)
        assertEquals("model=Kiku sources=source-ACTIVE_CARDS + source-SUSPENDED_CARDS", body)
    }

    @Test
    fun aFailedSyncPrefersTheProvidersOwnWords() {
        val copy = homeCopy()
        val plan = OnboardingPolicy.plan(
            readiness = ProviderReadiness.READY,
            binding = configuredBinding(),
            sync = SyncOutcome.Failed(
                PresentationFailure(
                    kind = PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
                    message = UiText.Literal("AnkiConnect refused the connection on port 8765"),
                ),
            ),
        )
        assertEquals(
            "last sync failed: AnkiConnect refused the connection on port 8765",
            copy.stepBody(plan, TestUiTextResolver),
        )
    }

    @Test
    fun aFailureWithNoUsableMessageFallsBackInsteadOfLeavingAGap() {
        // Both ways a message can come back empty: none at all, and one this
        // resolver cannot resolve. Neither may render "last sync failed: ." with
        // nothing in the gap.
        val copy = homeCopy()
        val kinds = listOf(
            PresentationFailure(kind = PresentationFailure.Kind.TRANSIENT),
            PresentationFailure(
                kind = PresentationFailure.Kind.TRANSIENT,
                message = UiText.Key("some.host.only.key"),
            ),
        )
        for (failure in kinds) {
            val plan = OnboardingPolicy.plan(
                readiness = ProviderReadiness.READY,
                binding = configuredBinding(),
                sync = SyncOutcome.Failed(failure),
            )
            assertEquals(
                "last sync failed: no reason given",
                copy.stepBody(plan, TestUiTextResolver),
                "a failure with no message must still explain itself",
            )
        }
    }

    @Test
    fun everyStepRendersANonBlankBodyWithNoSurvivingPlaceholder() {
        val copy = homeCopy()
        for (step in OnboardingStep.entries) {
            val body = copy.stepBody(
                planAt(step),
                TestUiTextResolver,
                HomeCountedCopy(syncedBody = "imported 3"),
            )
            assertTrue(body.isNotBlank(), "$step rendered an empty body")
            assertFalse("%" in body, "$step kept a placeholder: $body")
        }
    }

    @Test
    fun theRepairedLineIsAppendedOnlyWhereASyncCouldPerformTheTagWrite() {
        // Promising a tag write-back on a step whose remedy is granting access would
        // describe something that cannot happen yet.
        val copy = homeCopy()
        for (step in OnboardingStep.entries) {
            val plan = planAt(step, repairedKanjiCount = 9)
            val body = copy.stepBody(
                plan,
                TestUiTextResolver,
                HomeCountedCopy(syncedBody = "imported 3", repairedTaggingLine = "will tag 9"),
            )
            assertEquals(
                plan.showsRepairedTagging,
                "will tag 9" in body,
                "$step disagreed with showsRepairedTagging about the tag line",
            )
        }
    }

    @Test
    fun noRepairedKanjiMeansNoRepairedLineAnywhere() {
        val copy = homeCopy()
        for (step in OnboardingStep.entries) {
            val body = copy.stepBody(
                planAt(step),
                TestUiTextResolver,
                HomeCountedCopy(syncedBody = "imported 3", repairedTaggingLine = "will tag 0"),
            )
            assertFalse("will tag" in body, "$step promised a tag write with nothing to tag")
        }
    }

    @Test
    fun theConfirmationStatesTheTagCountAtThePointOfConsent() {
        // The count belongs in the dialog the user answers, not only on the screen
        // behind it: stating it here is what makes the write confirmed rather than
        // merely disclosed.
        val confirmation = homeCopy().syncConfirmation(
            binding = configuredBinding(),
            repairedTaggingLine = "will tag 9 repaired kanji",
        )
        assertEquals(UiText.Literal("Sync collection"), confirmation.title)
        assertEquals(
            UiText.Literal("will sync Kiku\nwill tag 9 repaired kanji"),
            confirmation.body,
        )
        assertEquals(UiText.Literal("Sync cards"), confirmation.confirmLabel)
        assertEquals(UiText.Literal("Cancel"), confirmation.dismissLabel)
    }

    @Test
    fun aConfirmationWithNothingToTagCarriesOnlyTheSyncSentence() {
        val confirmation = homeCopy().syncConfirmation(binding = configuredBinding())
        assertEquals(UiText.Literal("will sync Kiku"), confirmation.body)
    }

    @Test
    fun theConfirmationIsTheOnlyThingThatCanStartASync() {
        // The dialog's confirm action is what the policy wraps, and that action is
        // the only one that syncs. This is the assertion that would catch a
        // confirmation wired to `RequestSync`, which would loop, or to nothing.
        val copy = homeCopy()
        val effect = OnboardingPolicy.syncConfirmation(copy.syncConfirmation(configuredBinding()))
        assertEquals(UiText.Literal("will sync Kiku"), effect.body)
        assertFalse(effect.isDestructive, "syncing reads a collection; it destroys nothing")
    }

    private fun planAt(
        step: OnboardingStep,
        hostCopy: HostOnboardingCopy = HostOnboardingCopy.NONE,
        repairedKanjiCount: Int = 0,
    ): OnboardingPlan = OnboardingPlan(
        step = step,
        binding = configuredBinding(),
        hostCopy = hostCopy,
        importedKanji = 3,
        failure = null,
        repairedKanjiCount = repairedKanjiCount,
    )
}

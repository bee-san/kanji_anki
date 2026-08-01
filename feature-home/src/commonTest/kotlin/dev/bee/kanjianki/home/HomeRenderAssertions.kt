package dev.bee.kanjianki.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.FieldMapping
import dev.bee.kanjianki.presentation.FieldRole
import dev.bee.kanjianki.presentation.HostOnboardingCopy
import dev.bee.kanjianki.presentation.ImportSource
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.NoteTypeOption
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingPolicy
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.SyncOutcome
import dev.bee.kanjianki.presentation.UiText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Home and onboarding's rendering assertions, written once and run on both hosts.
 *
 * Not `@Test` functions, for the same reason as `:feature-shell`'s: the desktop JVM
 * composes into a Skia surface directly while the Android host target needs
 * Robolectric to stand up an Android environment first, and rather than let that
 * plumbing difference become two diverging copies, each host contributes a thin
 * class that calls into these.
 *
 * These assert structure, reachability, and which action a control dispatches — not
 * pixels. What a screen owes its host is the action; deciding what the action means
 * is `:presentation-api`'s job and is tested there.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryOnboardingStepShowsItsBodyAndItsOneButton() {
    // One primary button, never a menu of them: a user who cannot sync yet has one
    // blocker, and three buttons makes them guess which one addresses it.
    for (step in OnboardingStep.entries) {
        val copy = homeCopy()
        val plan = planAt(step)
        renderHome(
            content = {
                OnboardingCard(
                    plan = plan,
                    copy = copy,
                    resolver = TestUiTextResolver,
                    dispatch = {},
                    counted = HomeCountedCopy(syncedBody = "imported 3"),
                )
            },
        ) {
            onNodeWithTag(ONBOARDING_TEST_TAG).assertIsDisplayed()
            val body = onNodeWithTag(ONBOARDING_BODY_TEST_TAG).textOrEmpty()
            assertEquals(
                copy.stepBody(plan, TestUiTextResolver, HomeCountedCopy(syncedBody = "imported 3")),
                body,
                "$step rendered a body the copy holder did not produce",
            )
            onAllNodesWithTag(ONBOARDING_PRIMARY_TEST_TAG).assertCountEquals(1)
            assertTrue(
                copy.stepAction(plan, TestUiTextResolver) in
                    onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).subtreeTextOrEmpty(),
                "$step did not label its button",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertThePrimaryButtonDispatchesWhateverTheSharedPolicyDecided() {
    // The action comes from OnboardingPlan.primaryAction, not from the composable, so
    // the two hosts cannot drift on what the button does. Walking every step is what
    // proves the card never substitutes its own idea of the next move.
    for (step in OnboardingStep.entries) {
        val recorded = mutableListOf<KaniAction>()
        val plan = planAt(step)
        renderHome(
            content = {
                OnboardingCard(
                    plan = plan,
                    copy = homeCopy(),
                    resolver = TestUiTextResolver,
                    dispatch = { recorded += it },
                )
            },
        ) {
            onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).performClick()
            assertEquals(listOf(plan.primaryAction), recorded, "$step dispatched the wrong action")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertADisabledPrimaryButtonStaysVisibleAndDispatchesNothing() {
    // Disabled rather than hidden while a sync runs: a button that vanishes mid-tap
    // moves everything below it under the user's finger.
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = {
            OnboardingCard(
                plan = planAt(OnboardingStep.READY_FIRST_SYNC),
                copy = homeCopy(),
                resolver = TestUiTextResolver,
                dispatch = { recorded += it },
                enabled = false,
            )
        },
    ) {
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).performClick()
        assertTrue(recorded.isEmpty(), "a disabled button must not act: $recorded")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheHostSentenceReachesTheScreenAndTheSharedOneIsUsedOtherwise() {
    val copy = homeCopy()
    val hosted = planAt(
        OnboardingStep.CONNECT_PROVIDER,
        hostCopy = HostOnboardingCopy(
            guidance = UiText.Literal("Start Anki, then enable AnkiConnect."),
            primaryActionLabel = UiText.Literal("Open Anki"),
        ),
    )
    renderHome(
        content = {
            OnboardingCard(
                plan = hosted,
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        assertEquals(
            "Start Anki, then enable AnkiConnect.",
            onNodeWithTag(ONBOARDING_BODY_TEST_TAG).textOrEmpty(),
        )
        assertTrue(
            "Open Anki" in onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).subtreeTextOrEmpty(),
        )
    }

    renderHome(
        content = {
            OnboardingCard(
                plan = planAt(OnboardingStep.CONNECT_PROVIDER),
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        assertEquals(
            "body-CONNECT_PROVIDER",
            onNodeWithTag(ONBOARDING_BODY_TEST_TAG).textOrEmpty(),
            "with no host sentence the shared one must show",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheRepairedTaggingLineAppearsOnTheCardOnlyWhereItCanHappen() {
    val counted = HomeCountedCopy(
        syncedBody = "imported 3",
        repairedTaggingLine = "will tag 9 repaired kanji",
    )
    for (step in OnboardingStep.entries) {
        val plan = planAt(step, repairedKanjiCount = 9)
        renderHome(
            content = {
                OnboardingCard(
                    plan = plan,
                    copy = homeCopy(),
                    resolver = TestUiTextResolver,
                    dispatch = {},
                    counted = counted,
                )
            },
        ) {
            val body = onNodeWithTag(ONBOARDING_BODY_TEST_TAG).textOrEmpty()
            assertEquals(
                plan.showsRepairedTagging,
                "will tag 9 repaired kanji" in body,
                "$step disagreed with showsRepairedTagging on screen",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertProviderStatusIsAnnouncedAsOneLabelledPair() {
    // One description for the pair, so a screen reader says "Collection, Connected"
    // rather than reading two unrelated labels in sequence.
    val copy = homeCopy()
    for (readiness in ProviderReadiness.entries) {
        renderHome(
            content = { ProviderStatusRow(readiness = readiness, copy = copy) },
        ) {
            onNodeWithTag(PROVIDER_STATUS_TEST_TAG).assertIsDisplayed()
            assertEquals(
                "${copy.providerStatusTitle}, ${copy.readinessLabel(readiness)}",
                onNodeWithTag(PROVIDER_STATUS_TEST_TAG).contentDescriptionOrEmpty(),
                "$readiness was not announced with its title",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertSyncProgressOffersStopAndDispatchesCancelWithNoConfirmation() {
    // Cancellation is offered throughout rather than after a delay, and needs no
    // confirmation: sync writes in batches, so stopping keeps what already committed
    // and there is nothing to warn about.
    val recorded = mutableListOf<KaniAction>()
    val copy = homeCopy()
    renderHome(
        content = { SyncProgressCard(copy = copy, dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(SYNC_PROGRESS_TEST_TAG).assertIsDisplayed()
        assertEquals(
            copy.syncInProgressTitle,
            onNodeWithTag(SYNC_PROGRESS_TEST_TAG).contentDescriptionOrEmpty(),
        )
        onNodeWithTag(SYNC_CANCEL_TEST_TAG).performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Provider.CancelSync), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheRepairedHandoffCopiesTheSearchRatherThanUnsuspending() {
    // Kani never writes card queue state, so the search is the whole of what it can
    // usefully do: the action is a clipboard request, never a provider write.
    val recorded = mutableListOf<KaniAction>()
    val copy = homeCopy()
    renderHome(
        content = { RepairedHandoffCard(count = 9, copy = copy, dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(REPAIRED_HANDOFF_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(REPAIRED_HANDOFF_COPY_TEST_TAG).performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.RequestCopy(
                    text = "tag:kani_repaired is:suspended",
                    confirmation = UiText.Literal(copy.repairedHandoffCopied),
                ),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheRepairedHandoffIsAbsentWhenThereIsNothingTagged() {
    for (count in listOf(0, -1)) {
        renderHome(
            content = { RepairedHandoffCard(count = count, copy = homeCopy(), dispatch = {}) },
        ) {
            onAllNodesWithTag(REPAIRED_HANDOFF_TEST_TAG).assertCountEquals(0)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheNoteTypePickerOffersEveryNoteTypeAndMarksTheUnusableOnes() {
    // Unusable note types are shown marked rather than hidden: a user looking for one
    // that is not in the list cannot tell whether Kani could not see it or would not
    // offer it, and "could not see it" is the far more alarming reading.
    val options = listOf(kikuOption(), thinOption())
    renderHome(
        content = {
            NoteTypePicker(
                options = options,
                selected = kikuOption().name,
                copy = homeCopy(),
                onSelect = {},
            )
        },
    ) {
        onNodeWithTag(NOTE_TYPE_PICKER_TEST_TAG).assertIsDisplayed()
        for (option in options) {
            onNodeWithTag(noteTypeRowTestTag(option.name)).assertIsDisplayed()
        }
        assertTrue(
            homeCopy().noteTypeTooFewFields in
                onNodeWithTag(noteTypeRowTestTag(thinOption().name)).subtreeTextOrEmpty(),
            "an unimportable note type must say why",
        )
        assertFalse(
            homeCopy().noteTypeTooFewFields in
                onNodeWithTag(noteTypeRowTestTag(kikuOption().name)).subtreeTextOrEmpty(),
            "a usable note type must not be marked unusable",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertSelectingANoteTypeReportsItAndAnUnusableOneIsRefused() {
    val chosen = mutableListOf<NoteTypeOption>()
    val options = listOf(kikuOption(), thinOption())
    renderHome(
        content = {
            NoteTypePicker(
                options = options,
                selected = "",
                copy = homeCopy(),
                onSelect = { chosen += it },
            )
        },
    ) {
        onNodeWithTag(noteTypeRowTestTag(kikuOption().name)).performClick()
        assertEquals(listOf(kikuOption()), chosen)
        // Refused rather than accepted and then rejected at sync: the field mapping
        // would have nothing to offer, so there is no state worth entering.
        onNodeWithTag(noteTypeRowTestTag(thinOption().name)).performClick()
        assertEquals(listOf(kikuOption()), chosen, "an unusable note type must not be selectable")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheSelectedNoteTypeIsAnnouncedAsSelected() {
    var selected by mutableStateOf(kikuOption().name)
    val options = listOf(kikuOption(), thinOption())
    renderHome(
        content = {
            NoteTypePicker(
                options = options,
                selected = selected,
                copy = homeCopy(),
                onSelect = { selected = it.name },
            )
        },
    ) {
        onNodeWithTag(noteTypeRowTestTag(kikuOption().name)).assertIsSelectedForTest(true)
        onNodeWithTag(noteTypeRowTestTag(thinOption().name)).assertIsSelectedForTest(false)
        for (option in options) {
            assertTrue(
                option.name in
                    onNodeWithTag(noteTypeRowTestTag(option.name)).contentDescriptionOrEmpty(),
                "${option.name} was not announced by name",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEveryFieldRoleIsShownWithItsRequirementAndItsField() {
    // Required and optional alike. A screen showing only the required two leaves a
    // user wondering why the sentence task never appears, the answer being an
    // unmapped optional field they were never shown.
    val copy = homeCopy()
    renderHome(
        content = {
            FieldMappingList(option = kikuOption(), mapping = kikuMapping(), copy = copy)
        },
    ) {
        onNodeWithTag(FIELD_MAPPING_TEST_TAG).assertIsDisplayed()
        for (role in FieldRole.entries) {
            val row = onNodeWithTag(fieldRowTestTag(role)).subtreeTextOrEmpty()
            assertTrue(copy.roleLabel(role) in row, "$role was not labelled: $row")
            assertTrue(copy.roleRequirement(role) in row, "$role did not state requirement: $row")
            assertTrue(
                kikuMapping().field(role).orEmpty() in row,
                "$role did not name its field: $row",
            )
        }
        onAllNodesWithTag(FIELD_PROBLEM_TEST_TAG).assertCountEquals(0)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnUnmappedRoleSaysSoRatherThanRenderingAGap() {
    val copy = homeCopy()
    renderHome(
        content = {
            FieldMappingList(option = kikuOption(), mapping = FieldMapping(), copy = copy)
        },
    ) {
        for (role in FieldRole.entries) {
            assertTrue(
                copy.fieldUnmapped in onNodeWithTag(fieldRowTestTag(role)).subtreeTextOrEmpty(),
                "$role rendered an empty gap instead of saying it is unset",
            )
        }
        // Only the two required roles are a problem; an unset optional field is a
        // choice, not an error.
        onAllNodesWithTag(FIELD_PROBLEM_TEST_TAG).assertCountEquals(2)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAFieldRenamedInAnkiIsReportedPerRole() {
    // Without this the import silently reads nothing from the renamed field, which
    // looks like a Kani bug and is a one-word fix in the mapping.
    val copy = homeCopy()
    val renamed = NoteTypeOption(
        name = "Kiku",
        fields = listOf("Expression", "MainDefinition", "Reading"),
    )
    renderHome(
        content = { FieldMappingList(option = renamed, mapping = kikuMapping(), copy = copy) },
    ) {
        assertTrue(
            copy.staleField(FieldRole.READING) in
                onNodeWithTag(fieldRowTestTag(FieldRole.READING)).subtreeTextOrEmpty(),
            "the renamed field must be reported on its own row",
        )
        assertFalse(
            copy.staleField(FieldRole.EXPRESSION) in
                onNodeWithTag(fieldRowTestTag(FieldRole.EXPRESSION)).subtreeTextOrEmpty(),
            "a field that still exists must not be reported stale",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAMissingRequiredRoleIsReportedAheadOfAStaleOne() {
    // Both problems can hold at once, and the missing one is the blocker: the sync
    // cannot run at all until a required role is picked.
    val copy = homeCopy()
    val renamed = NoteTypeOption(name = "Kiku", fields = listOf("Word"))
    val mapping = kikuMapping().without(FieldRole.MEANING)
    renderHome(
        content = { FieldMappingList(option = renamed, mapping = mapping, copy = copy) },
    ) {
        assertTrue(
            copy.missingRequiredField(FieldRole.MEANING) in
                onNodeWithTag(fieldRowTestTag(FieldRole.MEANING)).subtreeTextOrEmpty(),
        )
        assertTrue(
            copy.staleField(FieldRole.EXPRESSION) in
                onNodeWithTag(fieldRowTestTag(FieldRole.EXPRESSION)).subtreeTextOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedResourcesResolveOnThisHost() {
    // The one assertion that exercises this module's own Compose Multiplatform
    // resources rather than test strings. It is what catches a resource that loads
    // under Skiko but not through Android's asset loader, and a plural entry missing
    // from one locale.
    renderHome(
        content = {
            val copy = rememberHomeCopy()
            val plan = OnboardingPolicy.plan(
                readiness = ProviderReadiness.READY,
                binding = CollectionBinding(
                    noteType = "Kiku",
                    sources = setOf(ImportSource.SUSPENDED_CARDS, ImportSource.BROWSER_QUERY),
                    browserQuery = "deck:Mining -is:suspended",
                ),
                sync = SyncOutcome.Succeeded(importedKanji = 1),
                repairedKanjiCount = 2,
            )
            OnboardingCard(
                plan = plan,
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
                counted = rememberHomeCountedCopy(plan),
            )
            ProviderStatusRow(readiness = ProviderReadiness.READY, copy = copy)
            SyncProgressCard(copy = copy, dispatch = {})
            RepairedHandoffCard(count = 2, copy = copy, dispatch = {})
            NoteTypePicker(
                options = listOf(kikuOption(), thinOption()),
                selected = kikuOption().name,
                copy = copy,
                onSelect = {},
            )
            FieldMappingList(option = kikuOption(), mapping = FieldMapping(), copy = copy)
        },
    ) {
        for (tag in listOf(
            ONBOARDING_TEST_TAG,
            ONBOARDING_BODY_TEST_TAG,
            ONBOARDING_PRIMARY_TEST_TAG,
            PROVIDER_STATUS_TEST_TAG,
            SYNC_PROGRESS_TEST_TAG,
            SYNC_CANCEL_TEST_TAG,
            REPAIRED_HANDOFF_TEST_TAG,
            REPAIRED_HANDOFF_COPY_TEST_TAG,
            NOTE_TYPE_PICKER_TEST_TAG,
            FIELD_MAPPING_TEST_TAG,
        )) {
            onNodeWithTag(tag).assertExists()
        }
        val body = onNodeWithTag(ONBOARDING_BODY_TEST_TAG).textOrEmpty()
        assertFalse("%" in body, "a shipped string kept a placeholder: $body")
        assertTrue("Kiku" in body, "the synced body must name the note type: $body")
        assertTrue(
            "deck:Mining -is:suspended" in body,
            "the browser query must survive display verbatim: $body",
        )
        val handoff = onNodeWithTag(REPAIRED_HANDOFF_TEST_TAG).subtreeTextOrEmpty()
        assertFalse("%" in handoff, "a shipped plural kept a placeholder: $handoff")
        val picker = onNodeWithTag(NOTE_TYPE_PICKER_TEST_TAG).subtreeTextOrEmpty()
        assertFalse("%" in picker, "a shipped plural kept a placeholder: $picker")
        // Every required role is unmapped here, so both blockers must be stated.
        onAllNodesWithTag(FIELD_PROBLEM_TEST_TAG).assertCountEquals(2)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheFailureRecoveryCardShowsTheProvidersOwnWords() {
    // The path a user actually hits: a sync failed, and the card has to say why and
    // offer the retry rather than sending them to settings.
    val plan = OnboardingPolicy.plan(
        readiness = ProviderReadiness.READY,
        binding = configuredBinding(),
        sync = SyncOutcome.Failed(
            PresentationFailure(
                kind = PresentationFailure.Kind.TRANSIENT,
                message = UiText.Literal("Anki closed mid-sync"),
            ),
        ),
    )
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = {
            OnboardingCard(
                plan = plan,
                copy = homeCopy(),
                resolver = TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        assertTrue(
            "Anki closed mid-sync" in onNodeWithTag(ONBOARDING_BODY_TEST_TAG).textOrEmpty(),
        )
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Provider.RequestSync),
            recorded,
            "a sync failure retries the sync; it does not skip the confirmation",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnEnabledPrimaryButtonIsReachableAtThePhoneWidth() {
    // The narrowest window Kani supports. A button pushed outside the viewport by a
    // long host sentence is not clickable, and no semantics assertion would notice.
    val recorded = mutableListOf<KaniAction>()
    val long = HostOnboardingCopy(
        guidance = UiText.Literal(
            "Anki is not running. Start Anki on this computer, install the AnkiConnect " +
                "add-on if you have not already, and leave the window open while Kani syncs.",
        ),
    )
    renderHome(
        content = {
            OnboardingCard(
                plan = planAt(OnboardingStep.CONNECT_PROVIDER, hostCopy = long),
                copy = homeCopy(),
                resolver = TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).assertIsEnabled()
        onNodeWithTag(ONBOARDING_PRIMARY_TEST_TAG).performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Provider.Connect), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheTestTagsAreDistinctSoAssertionsCannotCollide() {
    val tags = listOf(
        ONBOARDING_TEST_TAG,
        ONBOARDING_BODY_TEST_TAG,
        ONBOARDING_PRIMARY_TEST_TAG,
        PROVIDER_STATUS_TEST_TAG,
        SYNC_PROGRESS_TEST_TAG,
        SYNC_CANCEL_TEST_TAG,
        REPAIRED_HANDOFF_TEST_TAG,
        REPAIRED_HANDOFF_COPY_TEST_TAG,
        NOTE_TYPE_PICKER_TEST_TAG,
        FIELD_MAPPING_TEST_TAG,
        FIELD_PROBLEM_TEST_TAG,
    ) +
        FieldRole.entries.map(::fieldRowTestTag) +
        listOf(kikuOption(), thinOption()).map { noteTypeRowTestTag(it.name) }
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    // The Android instrumentation addresses these exact strings.
    assertEquals("kani-onboarding", ONBOARDING_TEST_TAG)
    assertEquals("kani-field-expression", fieldRowTestTag(FieldRole.EXPRESSION))
    assertEquals("kani-note-type-Kiku", noteTypeRowTestTag("Kiku"))
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
    repairedKanjiCount = repairedKanjiCount,
)

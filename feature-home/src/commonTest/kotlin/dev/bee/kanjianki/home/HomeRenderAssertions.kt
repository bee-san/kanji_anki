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
import androidx.compose.ui.test.performTextInput
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.BrowseRow
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.FieldMapping
import dev.bee.kanjianki.presentation.FieldRole
import dev.bee.kanjianki.presentation.FocusCard
import dev.bee.kanjianki.presentation.FocusEmptyReason
import dev.bee.kanjianki.presentation.FocusQueue
import dev.bee.kanjianki.presentation.FocusTag
import dev.bee.kanjianki.presentation.HomeAccent
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.HomeMetric
import dev.bee.kanjianki.presentation.HomeMetricKind
import dev.bee.kanjianki.presentation.HomeNotice
import dev.bee.kanjianki.presentation.HomeRecommendation
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
import dev.bee.kanjianki.presentation.TodayPlan
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

@OptIn(ExperimentalTestApi::class)
internal fun assertHomesPrimaryActionSyncsBeforeAnythingIsImportedAndStudiesAfter() {
    // One button whose meaning changes, not two that swap places. The assertion is on
    // the dispatched action rather than on the label, because the label is copy and the
    // action is the contract: a host that rendered "Study now" and dispatched a sync
    // would pass a text assertion.
    val copy = dashboardCopy()
    for (imported in listOf(false, true)) {
        val recorded = mutableListOf<KaniAction>()
        val home = HomeDashboard(focus = FocusQueue(hasImportedKanji = imported))
        renderHome(
            content = { HomePrimaryAction(home, copy, dispatch = { recorded += it }) },
        ) {
            onNodeWithTag(HOME_PRIMARY_TEST_TAG).assertIsDisplayed()
            onNodeWithTag(HOME_PRIMARY_TEST_TAG).performClick()
            assertEquals(listOf(home.primaryAction), recorded, "imported=$imported")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertThePrimaryActionCarriesTheWaitingCountAndDisablesWhileSyncing() {
    val copy = dashboardCopy()
    renderHome(
        content = {
            HomePrimaryAction(
                home = HomeDashboard(
                    focus = FocusQueue(hasImportedKanji = true),
                    studyRemainingCount = 12,
                ),
                copy = copy,
                dispatch = {},
            )
        },
    ) {
        // The count is on the button because it answers "is this worth starting" — the
        // question the user is asking as they reach for it.
        assertTrue(
            "12" in onNodeWithTag(HOME_PRIMARY_TEST_TAG).subtreeTextOrEmpty(),
            "the waiting count must reach the button",
        )
    }

    // Disabled rather than hidden, for the reason onboarding's button is: a control
    // that vanishes mid-tap moves what is underneath it under the user's finger.
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = {
            HomePrimaryAction(
                home = HomeDashboard(syncing = true),
                copy = copy,
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(HOME_PRIMARY_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(HOME_PRIMARY_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(HOME_PRIMARY_TEST_TAG).performClick()
        assertTrue(recorded.isEmpty(), "a sync in progress must not be restartable: $recorded")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEveryMetricTileIsAnnouncedWithItsLabelAndFigure() {
    // Walked over every kind so a tile added later cannot ship without a label. The
    // description is merged because three separate `Text`s read as three fragments.
    val copy = dashboardCopy()
    renderHome(
        content = {
            HomeMetricRow(
                metrics = HomeMetricKind.entries.map { metricFor(it) },
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        onNodeWithTag(HOME_METRIC_ROW_TEST_TAG).assertIsDisplayed()
        for (kind in HomeMetricKind.entries) {
            val description = onNodeWithTag(homeMetricTestTag(kind)).contentDescriptionOrEmpty()
            assertEquals(
                "${copy.metricCardDescription}, ${copy.metricLabel(kind)}, value-$kind, detail-$kind",
                description,
                "$kind was not announced as one labelled figure",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertOnlyAMetricWithSomethingToDoIsClickable() {
    // A tile that does nothing is not a button, and a screen reader must not promise an
    // activation the streak tile would ignore.
    val recorded = mutableListOf<KaniAction>()
    val actionable = HomeMetric(
        kind = HomeMetricKind.SYNC,
        value = UiText.Literal("today"),
        action = KaniAction.Provider.RequestSync,
    )
    val inert = HomeMetric(kind = HomeMetricKind.STREAK, value = UiText.Literal("4 days"))
    renderHome(
        content = {
            HomeMetricRow(
                metrics = listOf(actionable, inert),
                copy = dashboardCopy(),
                resolver = TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(homeMetricTestTag(HomeMetricKind.SYNC)).assertIsEnabled()
        onNodeWithTag(homeMetricTestTag(HomeMetricKind.STREAK)).assertIsNotEnabled()
        onNodeWithTag(homeMetricTestTag(HomeMetricKind.SYNC)).performClick()
        onNodeWithTag(homeMetricTestTag(HomeMetricKind.STREAK)).performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Provider.RequestSync),
            recorded,
            "only the tile with an action may act",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertMetricTilesStackRatherThanTruncateAtLargeFontScales() {
    // Three tiles side by side at a 1.3x font scale truncate their values, and a
    // truncated streak count is worse than none: it reads as a smaller number. Both
    // layouts must keep every tile present and reachable, which is what is asserted —
    // the arrangement itself is not a semantics fact.
    for (scale in listOf(1f, 1.5f)) {
        renderHome(
            content = {
                ScaledFont(fontScale = scale) {
                    HomeMetricRow(
                        metrics = HomeMetricKind.entries.map { metricFor(it) },
                        copy = dashboardCopy(),
                        resolver = TestUiTextResolver,
                        dispatch = {},
                    )
                }
            },
        ) {
            for (kind in HomeMetricKind.entries) {
                onNodeWithTag(homeMetricTestTag(kind)).assertIsDisplayed()
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheTodayCardOffersTheOneActionItsRecommendationChose() {
    // Exhaustive over the recommendations so a host cannot wire SYNC_FIRST to study.
    // `SYNC_FIRST` asks for the confirmation rather than starting a sync, because every
    // sync in Kani is user-confirmed.
    val copy = dashboardCopy()
    for (recommendation in HomeRecommendation.entries) {
        val recorded = mutableListOf<KaniAction>()
        val plan = TodayPlan(
            recommendation = recommendation,
            summary = UiText.Literal("summary-$recommendation"),
            details = listOf(UiText.Literal("detail-$recommendation")),
        )
        renderHome(
            content = {
                HomeTodayCard(plan, copy, TestUiTextResolver, dispatch = { recorded += it })
            },
        ) {
            onNodeWithTag(HOME_TODAY_TEST_TAG).assertIsDisplayed()
            val expected = recommendation.action
            if (expected == null) {
                onAllNodesWithTag(HOME_TODAY_ACTION_TEST_TAG).assertCountEquals(0)
            } else {
                onNodeWithTag(HOME_TODAY_ACTION_TEST_TAG).performClick()
                assertEquals(listOf(expected), recorded, "$recommendation dispatched wrongly")
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheTodayCardIsAbsentRatherThanEmptyWhenItHasNothingToSay() {
    // A titled card containing only its own title reads as content that failed to load.
    renderHome(
        content = {
            HomeTodayCard(
                plan = TodayPlan(HomeRecommendation.WAIT_UNTIL_LATER),
                copy = dashboardCopy(),
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        onAllNodesWithTag(HOME_TODAY_TEST_TAG).assertCountEquals(0)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheTodayCardIsAnnouncedAsOneSentenceInReadingOrder() {
    val copy = dashboardCopy()
    val plan = TodayPlan(
        recommendation = HomeRecommendation.STUDY_NOW,
        summary = UiText.Literal("18 cards are due"),
        details = listOf(UiText.Literal("4 are relearning"), UiText.EMPTY),
    )
    renderHome(
        content = { HomeTodayCard(plan, copy, TestUiTextResolver, dispatch = {}) },
    ) {
        // The blank detail is dropped rather than joined, so the sentence has no gap in
        // the middle where a `UiText.EMPTY` used to be.
        assertEquals(
            "${copy.todayTitle} · 18 cards are due · 4 are relearning · ${copy.studyAction}",
            onNodeWithTag(HOME_TODAY_TEST_TAG).contentDescriptionOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheDeckOverviewRendersItsLinesAndVanishesWhenEmpty() {
    val copy = dashboardCopy()
    renderHome(
        content = {
            HomeDeckOverview(
                rows = listOf(UiText.Literal("Mining: 42"), UiText.EMPTY),
                copy = copy,
                resolver = TestUiTextResolver,
            )
        },
    ) {
        val text = onNodeWithTag(HOME_DECK_OVERVIEW_TEST_TAG).subtreeTextOrEmpty()
        assertTrue(copy.deckOverviewTitle in text, "the section must be titled: $text")
        assertTrue("Mining: 42" in text, "the deck line must show: $text")
    }

    // Blank lines are dropped, and a list of nothing but blanks renders nothing at all
    // rather than a heading over empty space.
    for (rows in listOf(emptyList(), listOf(UiText.EMPTY))) {
        renderHome(
            content = { HomeDeckOverview(rows, copy, TestUiTextResolver) },
        ) {
            onAllNodesWithTag(HOME_DECK_OVERVIEW_TEST_TAG).assertCountEquals(0)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEveryHostNoticeExplainsItselfWithNoActionToTake() {
    // There is nothing for the user to do about a provider that cannot report memory
    // state, so the card has no button — but a desktop user whose intervals differ from
    // an Android user's deserves to know why.
    val copy = dashboardCopy()
    for (notice in HomeNotice.entries) {
        renderHome(
            content = { HomeNoticeCard(notice, copy) },
        ) {
            onNodeWithTag(homeNoticeTestTag(notice)).assertIsDisplayed()
            assertEquals(
                "${copy.noticeTitle(notice)}. ${copy.noticeBody(notice)}",
                onNodeWithTag(homeNoticeTestTag(notice)).contentDescriptionOrEmpty(),
                "$notice was not announced with its explanation",
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAFocusCardOpensItsOwnKanjiAndIsAnnouncedAsOne() {
    val copy = dashboardCopy()
    val cards = listOf(focusCard("脱", "take off"), focusCard("橋", "bridge"))
    val recorded = mutableListOf<KaniAction>()
    renderHome(
        content = {
            FocusQueuePanel(
                queue = FocusQueue(plan = UiText.Literal("2 kanji today"), cards = cards),
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(FOCUS_QUEUE_TEST_TAG).assertIsDisplayed()
        for (card in cards) {
            assertEquals(
                copy.focusCardDescription(card.kanji, TestUiTextResolver.resolve(card.meaning)),
                onNodeWithTag(focusCardTestTag(card.kanji)).contentDescriptionOrEmpty(),
                "${card.kanji} was not announced as one card",
            )
        }
        // The second card, not the first: a queue that dispatched the head of the list
        // whichever card was tapped would pass an assertion that only clicked one.
        onNodeWithTag(focusCardTestTag("橋")).performClick()
        assertEquals(listOf(cards[1].action), recorded, "a card must open its own kanji")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAFocusCardShowsItsSupportingLinesAndBadges() {
    // A card may carry any subset of the three supporting lines. Rendering an empty
    // `Text` for a missing one leaves a gap indistinguishable from a failed load.
    renderHome(
        content = {
            FocusQueuePanel(
                queue = FocusQueue(
                    cards = listOf(
                        FocusCard(
                            kanji = "脱",
                            meaning = UiText.Literal("take off"),
                            reasonLine = UiText.Literal("failed twice"),
                            body = UiText.EMPTY,
                            sourceEvidence = UiText.Literal("from 脱出"),
                            tags = listOf(
                                FocusTag(UiText.Literal("word reading")),
                                FocusTag(UiText.EMPTY),
                            ),
                        ),
                    ),
                ),
                copy = dashboardCopy(),
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        val text = onNodeWithTag(focusCardTestTag("脱")).subtreeTextOrEmpty()
        for (expected in listOf("脱", "take off", "failed twice", "from 脱出", "word reading")) {
            assertTrue(expected in text, "the card dropped \"$expected\": $text")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnEmptyFocusQueueNamesItsOwnCauseAndHidesViewAll() {
    // The assertion this whole enum exists for: a single "nothing queued" message sends
    // half the users who see it to the wrong remedy. And "View all" leading to the same
    // empty screen is a dead end wearing an invitation.
    val copy = dashboardCopy()
    val cases = mapOf(
        FocusEmptyReason.NOTHING_IMPORTED to FocusQueue(),
        FocusEmptyReason.NOTHING_ACTIVE to FocusQueue(hasImportedKanji = true),
    )
    for ((reason, queue) in cases) {
        renderHome(
            content = {
                FocusQueuePanel(queue, copy, TestUiTextResolver, dispatch = {})
            },
        ) {
            assertEquals(
                "${copy.emptyTitle(reason)}. ${copy.emptyBody(reason)}",
                onNodeWithTag(FOCUS_QUEUE_EMPTY_TEST_TAG).contentDescriptionOrEmpty(),
                "$reason showed the other reason's copy",
            )
            onAllNodesWithTag(FOCUS_QUEUE_VIEW_ALL_TEST_TAG).assertCountEquals(0)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertViewAllOpensTheFullQueueOnlyWhenThereIsOne() {
    val recorded = mutableListOf<KaniAction>()
    val queue = FocusQueue(cards = listOf(focusCard("脱", "take off")))
    renderHome(
        content = {
            FocusQueuePanel(queue, dashboardCopy(), TestUiTextResolver, dispatch = { recorded += it })
        },
    ) {
        onAllNodesWithTag(FOCUS_QUEUE_EMPTY_TEST_TAG).assertCountEquals(0)
        onNodeWithTag(FOCUS_QUEUE_VIEW_ALL_TEST_TAG).performClick()
        assertEquals(listOf(queue.viewAllAction), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertBrowseCommitsTheQueryOnSearchAndNotWhileTyping() {
    // The plan's "no actions while editing text" requirement, and a correctness property
    // rather than a preference: dispatching per keystroke would open a Browse
    // destination per character, filling the back stack with queries nobody searched.
    val recorded = mutableListOf<KaniAction>()
    val results = BrowseResults.of(listOf(browseRow("脱", "take off")), onlySimilarKanji = true)
    renderHome(
        content = {
            BrowseScreen(results, browseCopy(), TestUiTextResolver, dispatch = { recorded += it })
        },
    ) {
        onNodeWithTag(BROWSE_QUERY_TEST_TAG).performTextInput("bri")
        assertTrue(recorded.isEmpty(), "typing must not act: $recorded")
        onNodeWithTag(BROWSE_SEARCH_TEST_TAG).performClick()
        // The committed search keeps the filter the user already set; retyping a query
        // is not a request to widen the scope again.
        assertEquals(listOf(results.search("bri")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertBrowseFiltersOpenTheSameSearchWithTheFlagFlipped() {
    // Filters live in the back stack rather than in a callback, so back from a filtered
    // list returns to the unfiltered one instead of dropping the filter with no undo.
    val results = BrowseResults.of(listOf(browseRow("脱", "take off")), query = "off")
    val cases = listOf(
        BROWSE_SIMILAR_FILTER_TEST_TAG to results.withSimilarFilter(only = true),
        BROWSE_SHOW_SUSPENDED_TEST_TAG to results.withSuspendedShown(shown = true),
    )
    for ((tag, expected) in cases) {
        val recorded = mutableListOf<KaniAction>()
        renderHome(
            content = {
                BrowseScreen(results, browseCopy(), TestUiTextResolver, dispatch = { recorded += it })
            },
        ) {
            onNodeWithTag(tag).assertIsToggledForTest(false)
            onNodeWithTag(tag).performClick()
            assertEquals(listOf(expected), recorded, "$tag dispatched the wrong destination")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertABrowseRowOpensItsDetailCarryingTheSearch() {
    // The difference between "close this card" and "lose my search".
    val recorded = mutableListOf<KaniAction>()
    val rows = listOf(browseRow("脱", "take off"), browseRow("橋", "bridge"))
    val results = BrowseResults.of(rows, query = "off", allKanjiScope = true)
    renderHome(
        content = {
            BrowseScreen(results, browseCopy(), TestUiTextResolver, dispatch = { recorded += it })
        },
    ) {
        onNodeWithTag(browseRowTestTag("橋")).performClick()
        assertEquals(listOf(results.open(rows[1])), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertABrowseRowIsAnnouncedAsOneSentenceIncludingItsState() {
    val copy = browseCopy()
    val suspended = BrowseRow(
        kanji = "橋",
        meaning = UiText.Literal("bridge"),
        readings = UiText.Literal("きょう"),
        summary = UiText.EMPTY,
        suspended = true,
    )
    val results = BrowseResults.of(listOf(suspended), showSuspended = true)
    renderHome(
        content = { BrowseScreen(results, copy, TestUiTextResolver, dispatch = {}) },
    ) {
        // The suspended chip is in the sentence because it is the collection's own state
        // and explains why the row is not selected; the blank summary is dropped.
        assertEquals(
            copy.rowDescription(suspended, TestUiTextResolver),
            onNodeWithTag(browseRowTestTag("橋")).contentDescriptionOrEmpty(),
        )
        assertTrue(
            copy.suspendedChip in onNodeWithTag(browseRowTestTag("橋")).subtreeTextOrEmpty(),
            "a suspended row must say so on screen too",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheStudyCheckboxIsQueueStateAndNeverASuspensionWrite() {
    // Kani never writes card queue state, suspension included. The checkbox sits beside
    // a chip that *is* collection state, so this asserts what it dispatches: a
    // Kani-side queue toggle for that one kanji and nothing else.
    val recorded = mutableListOf<KaniAction>()
    val row = browseRow("脱", "take off")
    val results = BrowseResults.of(listOf(row))
    renderHome(
        content = {
            BrowseScreen(results, browseCopy(), TestUiTextResolver, dispatch = { recorded += it })
        },
    ) {
        onNodeWithTag(browseStudiedTestTag("脱")).performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Browse.SetStudied(kanji = "脱", studied = false)),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertBothSelectionControlsAreOfferedWhateverTheSelection() {
    // "Select all" on a fully-selected list is a harmless no-op, while a control that
    // disappears at the extremes leaves the user hunting for the one they need.
    val recorded = mutableListOf<KaniAction>()
    val results = BrowseResults.of(listOf(browseRow("脱", "take off")))
    renderHome(
        content = {
            BrowseScreen(results, browseCopy(), TestUiTextResolver, dispatch = { recorded += it })
        },
    ) {
        onNodeWithTag(BROWSE_SELECT_ALL_TEST_TAG).performClick()
        onNodeWithTag(BROWSE_DESELECT_ALL_TEST_TAG).performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.Browse.SetAllStudied(studied = true),
                KaniAction.Browse.SetAllStudied(studied = false),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertBrowseReportsItsResultCountAndSaysWhenTheListIsCapped() {
    val copy = browseCopy()
    // One active and one suspended row, so the summary exercises the partial template
    // rather than the "all selected" shortcut two active rows would take.
    val rows = listOf(
        browseRow("脱", "take off"),
        BrowseRow(kanji = "橋", meaning = UiText.Literal("bridge"), suspended = true),
    )
    renderHome(
        content = {
            BrowseScreen(
                results = BrowseResults.of(rows, showSuspended = true, truncated = true),
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        // The capped case is its own sentence: a bare count at exactly the limit looks
        // like a complete result set that happens to be a round number.
        assertEquals(
            "first 2 matches",
            onNodeWithTag(BROWSE_RESULT_HEADING_TEST_TAG).textOrEmpty(),
        )
        assertEquals(
            "1 of 2 selected",
            onNodeWithTag(BROWSE_SELECTION_SUMMARY_TEST_TAG).textOrEmpty(),
        )
    }

    renderHome(
        content = {
            BrowseScreen(BrowseResults(), copy, TestUiTextResolver, dispatch = {})
        },
    ) {
        assertEquals(
            copy.resultNone,
            onNodeWithTag(BROWSE_RESULT_HEADING_TEST_TAG).textOrEmpty(),
        )
        assertEquals(
            "${copy.emptyTitle}. ${copy.emptyBody}",
            onNodeWithTag(BROWSE_EMPTY_TEST_TAG).contentDescriptionOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedDashboardResourcesResolveOnThisHost() {
    // The counterpart to the onboarding resource assertion: exercises this module's own
    // Compose Multiplatform resources rather than marker strings, which is what catches
    // a resource that loads under Skiko but not through Android's asset loader, and a
    // plural entry missing from one locale.
    val row = browseRow("脱", "take off")
    renderHome(
        content = {
            val dashboard = rememberDashboardCopy()
            val browse = rememberBrowseCopy()
            HomePrimaryAction(
                home = HomeDashboard(
                    focus = FocusQueue(hasImportedKanji = true),
                    studyRemainingCount = 7,
                ),
                copy = dashboard,
                dispatch = {},
            )
            HomeMetricRow(
                metrics = HomeMetricKind.entries.map { metricFor(it) },
                copy = dashboard,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
            HomeTodayCard(
                plan = TodayPlan(
                    recommendation = HomeRecommendation.STUDY_NOW,
                    summary = UiText.Literal("18 cards are due"),
                ),
                copy = dashboard,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
            HomeDeckOverview(
                rows = listOf(UiText.Literal("Mining: 42")),
                copy = dashboard,
                resolver = TestUiTextResolver,
            )
            HomeNoticeCard(HomeNotice.REDUCED_FSRS_PRECISION, dashboard)
            FocusQueuePanel(
                queue = FocusQueue(cards = listOf(focusCard("脱", "take off"))),
                copy = dashboard,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
            BrowseScreen(
                results = BrowseResults.of(listOf(row)),
                copy = browse,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        for (tag in dashboardTestTags()) {
            onNodeWithTag(tag).assertExists()
        }
        // A shipped template that kept its placeholder is the failure this catches: it
        // renders as a literal `%1$d` and no type checker would have objected.
        for (tag in listOf(
            HOME_PRIMARY_TEST_TAG,
            HOME_METRIC_ROW_TEST_TAG,
            HOME_TODAY_TEST_TAG,
            FOCUS_QUEUE_TEST_TAG,
            BROWSE_RESULT_HEADING_TEST_TAG,
            BROWSE_SELECTION_SUMMARY_TEST_TAG,
        )) {
            val text = onNodeWithTag(tag).subtreeTextOrEmpty()
            assertFalse("%" in text, "$tag kept a placeholder: $text")
        }
        assertTrue(
            "7" in onNodeWithTag(HOME_PRIMARY_TEST_TAG).subtreeTextOrEmpty(),
            "the shipped plural must substitute the waiting count",
        )
        val toggle = onNodeWithTag(browseStudiedTestTag("脱")).contentDescriptionOrEmpty()
        assertTrue("脱" in toggle, "the shipped checkbox label must name its kanji: $toggle")
        assertFalse("%" in toggle, "the shipped checkbox label kept a placeholder: $toggle")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheDashboardTestTagsAreDistinctSoAssertionsCannotCollide() {
    val tags = dashboardTestTags() +
        HomeMetricKind.entries.map(::homeMetricTestTag) +
        HomeNotice.entries.map(::homeNoticeTestTag) +
        listOf("脱", "橋").flatMap {
            listOf(focusCardTestTag(it), browseRowTestTag(it), browseStudiedTestTag(it))
        }
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-home-metric-sync", homeMetricTestTag(HomeMetricKind.SYNC))
    assertEquals(
        "kani-home-notice-reduced_fsrs_precision",
        homeNoticeTestTag(HomeNotice.REDUCED_FSRS_PRECISION),
    )
    assertEquals("kani-browse-row-脱", browseRowTestTag("脱"))
}

/** Every fixed dashboard and Browse tag, in one list both assertions above walk. */
private fun dashboardTestTags(): List<String> = listOf(
    HOME_PRIMARY_TEST_TAG,
    HOME_METRIC_ROW_TEST_TAG,
    HOME_TODAY_TEST_TAG,
    HOME_TODAY_ACTION_TEST_TAG,
    HOME_DECK_OVERVIEW_TEST_TAG,
    FOCUS_QUEUE_TEST_TAG,
    FOCUS_QUEUE_VIEW_ALL_TEST_TAG,
    BROWSE_QUERY_TEST_TAG,
    BROWSE_SEARCH_TEST_TAG,
    BROWSE_SIMILAR_FILTER_TEST_TAG,
    BROWSE_SHOW_SUSPENDED_TEST_TAG,
    BROWSE_SELECT_ALL_TEST_TAG,
    BROWSE_DESELECT_ALL_TEST_TAG,
    BROWSE_RESULT_HEADING_TEST_TAG,
    BROWSE_SELECTION_SUMMARY_TEST_TAG,
)

private fun metricFor(kind: HomeMetricKind): HomeMetric = HomeMetric(
    kind = kind,
    value = UiText.Literal("value-$kind"),
    detail = UiText.Literal("detail-$kind"),
    accent = HomeAccent.entries[kind.ordinal],
)

private fun focusCard(kanji: String, meaning: String): FocusCard =
    FocusCard(kanji = kanji, meaning = UiText.Literal(meaning))

private fun browseRow(kanji: String, meaning: String): BrowseRow =
    BrowseRow(kanji = kanji, meaning = UiText.Literal(meaning))

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

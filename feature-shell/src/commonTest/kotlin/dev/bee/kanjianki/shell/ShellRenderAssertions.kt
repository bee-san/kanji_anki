package dev.bee.kanjianki.shell

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.CapabilityGate
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.Loadable
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.ui.KaniThemeId
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shell's rendering assertions, written once and run on both hosts.
 *
 * Not `@Test` functions, for the same reason as `:ui-common`'s theme assertions:
 * the desktop JVM composes into a Skia surface directly while the Android host
 * target needs Robolectric to stand up an Android environment first, and rather
 * than let that plumbing difference become two diverging copies, each host
 * contributes a thin class that calls into these.
 *
 * These are the semantics half of Goal 193's coverage: what a user can see and
 * reach at each window size, theme, and font scale. They assert structure and
 * reachability, not pixels — a screenshot diff cannot tell you that a tab is
 * announced as selected, and a semantics assertion cannot tell you it is the right
 * shade of pink. Both are needed; this file is the one that fails usefully.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertTheRouteBodyRendersInsideTheContentSlot() {
    // The most basic promise: the shell hands the current destination to the host's
    // content lambda and does not render it twice or swallow it.
    renderShell(state = ShellState(backStack = listOf(KaniDestination.Study))) {
        onNodeWithTag(SHELL_ROOT_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(SHELL_CONTENT_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(shellRouteTestTag(KaniDestination.Study)).assertIsDisplayed()
        onAllNodes(hasTestTag(TEST_ROUTE_BODY_TAG)).assertCountEquals(1)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheRouteTagTracksTheCurrentDestination() {
    // The route tag is on its own node precisely so it can coexist with the content
    // tag. This is the assertion that would have caught the non-additive `testTag`
    // mistake, where the second call silently replaced the first.
    for (destination in listOf(
        KaniDestination.Home,
        KaniDestination.Study,
        KaniDestination.Stats,
        KaniDestination.Settings(),
    )) {
        renderShell(state = ShellState(backStack = listOf(destination))) {
            onNodeWithTag(SHELL_CONTENT_TEST_TAG).assertExists()
            onNodeWithTag(shellRouteTestTag(destination)).assertExists()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEachWindowSizeGetsItsExpectedNavigationSurface() {
    // The pure `resolveShellLayout` decision, checked against what is actually
    // composed. A layout function that returned SIDE_RAIL while the shell drew a
    // bottom bar would pass `ShellLayoutTest` and still ship broken.
    for (window in ShellWindow.entries) {
        renderShell(state = ShellState(), window = window) {
            when (window.expectedPlacement) {
                ShellNavigationPlacement.BOTTOM_BAR -> {
                    onNodeWithTag(SHELL_BOTTOM_NAV_TEST_TAG).assertIsDisplayed()
                    onNodeWithTag(SHELL_NAV_RAIL_TEST_TAG).assertDoesNotExist()
                }

                ShellNavigationPlacement.SIDE_RAIL -> {
                    onNodeWithTag(SHELL_NAV_RAIL_TEST_TAG).assertIsDisplayed()
                    onNodeWithTag(SHELL_BOTTOM_NAV_TEST_TAG).assertDoesNotExist()
                }

                ShellNavigationPlacement.HIDDEN ->
                    error("no window size hides navigation on its own")
            }
            // Whichever surface it is, all four tabs must be reachable at every
            // size. A rail that dropped a tab off the bottom at 800px tall is the
            // failure this catches.
            for (tab in KaniTab.entries) {
                val tag = if (window.expectedPlacement == ShellNavigationPlacement.SIDE_RAIL) {
                    shellRailTabTestTag(tab)
                } else {
                    shellTabTestTag(tab)
                }
                onNodeWithTag(tag).assertIsDisplayed()
            }
            // And the content is still on screen beside or above it.
            onNodeWithTag(TEST_ROUTE_BODY_TAG).assertIsDisplayed()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertSelectingATabDispatchesAndReSelectingDoesNot() {
    val recorded = mutableListOf<KaniAction>()
    renderShell(state = ShellState(backStack = listOf(KaniDestination.Home)), recorded = recorded) {
        onNodeWithTag(shellTabTestTag(KaniTab.STATS)).performClick()
        onNodeWithTag(shellTabTestTag(KaniTab.SETTINGS)).performClick()
    }
    assertEquals(
        listOf<KaniAction>(
            KaniAction.Navigation.SelectTab(KaniTab.STATS),
            KaniAction.Navigation.SelectTab(KaniTab.SETTINGS),
        ),
        recorded,
    )

    // The already-selected tab is suppressed at the tab itself. The reducer would
    // treat it as a no-op anyway, so this is about not looking clickable rather
    // than about state.
    val reselect = mutableListOf<KaniAction>()
    renderShell(state = ShellState(backStack = listOf(KaniDestination.Home)), recorded = reselect) {
        onNodeWithTag(shellTabTestTag(KaniTab.HOME)).performClick()
    }
    assertEquals(emptyList<KaniAction>(), reselect, "re-selecting the current tab should be inert")
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheRailDispatchesTheSameActionsAsTheBar() {
    // The two placements share `ShellTab`, so this is really a test that the rail
    // is wired to the same `onSelect` — the Android host had two separate item
    // composables and this is exactly where they could have diverged.
    val recorded = mutableListOf<KaniAction>()
    renderShell(
        state = ShellState(backStack = listOf(KaniDestination.Home)),
        window = ShellWindow.DESKTOP_LARGE,
        recorded = recorded,
    ) {
        onNodeWithTag(shellRailTabTestTag(KaniTab.STUDY)).performClick()
    }
    assertEquals(listOf<KaniAction>(KaniAction.Navigation.SelectTab(KaniTab.STUDY)), recorded)
}

@OptIn(ExperimentalTestApi::class)
internal fun assertEveryTabIsAnnouncedWithItsLabelAndSelectedState() {
    // Semantics, not pixels: this is the part of the nav bar a screenshot test
    // cannot check at all, and the part a blind user depends on entirely.
    for (window in listOf(ShellWindow.PHONE, ShellWindow.DESKTOP_LARGE)) {
        val rail = window.expectedPlacement == ShellNavigationPlacement.SIDE_RAIL
        renderShell(state = ShellState(backStack = listOf(KaniDestination.Study)), window = window) {
            for (tab in KaniTab.entries) {
                val tag = if (rail) shellRailTabTestTag(tab) else shellTabTestTag(tab)
                val selected = tab == KaniTab.STUDY
                onNodeWithTag(tag).assertIsSelected(selected)
                val description = onNodeWithTag(tag).contentDescriptionOrEmpty()
                assertTrue(
                    description.isNotBlank(),
                    "$tab at $window has no accessible description",
                )
                assertEquals(
                    selected,
                    "selected" in description,
                    "$tab at $window should${if (selected) "" else " not"} " +
                        "announce selection: $description",
                )
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheStudyBadgeAppearsOnlyWhenThereIsWork() {
    // Badge on Study only, and only above zero. A badge reading "0" is worse than
    // no badge: it advertises work that does not exist.
    //
    // Queried unmerged throughout: a tab is a clickable `Surface`, which merges its
    // descendants into one node, so the badge's own tag and text are not in the
    // merged tree at all. That merge is correct — the tab is announced by its own
    // description and the badge is decoration inside it — so the test looks where
    // the badge actually lives rather than the shell flattening its semantics to
    // suit a test.
    renderShell(state = ShellState(studyBadgeCount = 0)) {
        onNodeWithTag(SHELL_NAV_BADGE_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
    renderShell(state = ShellState(studyBadgeCount = 12)) {
        onAllNodesWithTag(SHELL_NAV_BADGE_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
        assertEquals(
            "12",
            onNodeWithTag(SHELL_NAV_BADGE_TEST_TAG, useUnmergedTree = true)
                .subtreeTextOrEmpty(),
        )
    }
    renderShell(state = ShellState(studyBadgeCount = 250)) {
        assertEquals(
            "99+",
            onNodeWithTag(SHELL_NAV_BADGE_TEST_TAG, useUnmergedTree = true)
                .subtreeTextOrEmpty(),
            "an uncapped count would widen the Study tab past its neighbours",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertALargeFontScaleKeepsEveryTabReachable() {
    // The reason `stackNavigationRows` exists. At 1.5x on a phone the four labels
    // do not fit across one row; if the shell kept them there, the outer tabs get
    // clipped and become untappable. Both scales must keep all four displayed.
    for (fontScale in listOf(1f, LARGE_FONT_SCALE, 2f)) {
        renderShell(
            state = ShellState(studyBadgeCount = 3),
            window = ShellWindow.PHONE,
            fontScale = fontScale,
        ) {
            onNodeWithTag(SHELL_BOTTOM_NAV_TEST_TAG).assertIsDisplayed()
            for (tab in KaniTab.entries) {
                onNodeWithTag(shellTabTestTag(tab)).assertIsDisplayed()
            }
            onNodeWithTag(TEST_ROUTE_BODY_TAG).assertIsDisplayed()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertImmersionHidesNavigationWithoutHidingContent() {
    // Hiding the bar must not hide the screen. An immersive route that lost its
    // content would be a blank window with no way out.
    for (immersion in listOf(
        ShellImmersion(keyboardVisible = true),
        ShellImmersion(routeIsImmersive = true),
    )) {
        for (window in listOf(ShellWindow.PHONE, ShellWindow.DESKTOP_LARGE)) {
            renderShell(
                state = ShellState(backStack = listOf(KaniDestination.Study)),
                window = window,
                immersion = immersion,
            ) {
                onNodeWithTag(SHELL_BOTTOM_NAV_TEST_TAG).assertDoesNotExist()
                onNodeWithTag(SHELL_NAV_RAIL_TEST_TAG).assertDoesNotExist()
                onNodeWithTag(TEST_ROUTE_BODY_TAG).assertIsDisplayed()
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShellRendersUnderEveryThemeAndBothHostDarkSignals() {
    // Goal 193's dark/light coverage. What is asserted is that the shell composes
    // and stays navigable under each palette — the palette-to-scheme mapping itself
    // is `:ui-common`'s test, and duplicating it here would just couple two
    // modules' expectations together.
    for (theme in KaniThemeId.entries) {
        for (hostIsDark in listOf(false, true)) {
            renderShell(
                state = ShellState(studyBadgeCount = 5),
                theme = theme,
                isSystemInDarkTheme = hostIsDark,
            ) {
                onNodeWithTag(SHELL_ROOT_TEST_TAG).assertIsDisplayed()
                onNodeWithTag(TEST_ROUTE_BODY_TAG).assertIsDisplayed()
                onNodeWithTag(shellTabTestTag(KaniTab.STUDY)).assertIsDisplayed()
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheBackAffordanceIsTheHostsDecision() {
    val nested = ShellState(
        backStack = listOf(KaniDestination.Home, KaniDestination.Study),
    )
    // Android's default: the system gesture provides back, so the shell draws
    // nothing. Rendering a button here would change the Android app's appearance,
    // which Goal 193 forbids.
    renderShell(state = nested, backAffordance = ShellBackAffordanceMode.SYSTEM) {
        onNodeWithTag(SHELL_BACK_TEST_TAG).assertDoesNotExist()
    }

    // Desktop has no gesture, so the shell must draw one — and it dispatches Back
    // rather than deciding where back goes, which is the reducer's job.
    val recorded = mutableListOf<KaniAction>()
    renderShell(
        state = nested,
        backAffordance = ShellBackAffordanceMode.IN_SHELL,
        recorded = recorded,
    ) {
        onNodeWithTag(SHELL_BACK_TEST_TAG).assertIsDisplayed().performClick()
    }
    assertEquals(listOf<KaniAction>(KaniAction.Navigation.Back), recorded)

    // At the root there is nowhere to go, so even the in-shell mode draws nothing.
    renderShell(
        state = ShellState(backStack = listOf(KaniDestination.Home)),
        backAffordance = ShellBackAffordanceMode.IN_SHELL,
    ) {
        assertTrue(
            !ShellState(backStack = listOf(KaniDestination.Home)).canGoBack,
            "the root must not report that it can go back",
        )
        onNodeWithTag(SHELL_BACK_TEST_TAG).assertDoesNotExist()
    }
}

/**
 * Escape goes back on desktop, and only when going back is possible.
 *
 * The shell claims the event conditionally for a reason: a text field or dialog
 * that wants Escape for its own dismissal must still receive it, so the handler has
 * to decline rather than swallow. Both branches are asserted here because a handler
 * that always returned `true` would pass a dispatch-only test while quietly
 * breaking every nested dismissal.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEscapeGoesBackOnlyWhenThereIsSomewhereToGo() {
    // A focusable body, because key events are delivered to the focus owner and
    // travel down through the ancestors' preview handlers to reach it. The shell
    // root is not focusable itself — nor should it be.
    val focusableBody: @Composable (KaniDestination) -> Unit = {
        Box(modifier = Modifier.testTag(TEST_ROUTE_BODY_TAG).focusable())
    }

    val nested = mutableListOf<KaniAction>()
    renderShell(
        state = ShellState(backStack = listOf(KaniDestination.Home, KaniDestination.Study)),
        backAffordance = ShellBackAffordanceMode.IN_SHELL,
        recorded = nested,
        routeBody = focusableBody,
    ) {
        onNodeWithTag(TEST_ROUTE_BODY_TAG).requestFocus().performKeyInput {
            pressKey(Key.Escape)
        }
    }
    assertEquals(listOf<KaniAction>(KaniAction.Navigation.Back), nested)

    // At the root Escape is not the shell's to take. Asserted through the same
    // path, so a difference here is the guard and not the key plumbing.
    val root = mutableListOf<KaniAction>()
    renderShell(
        state = ShellState(backStack = listOf(KaniDestination.Home)),
        recorded = root,
        routeBody = focusableBody,
    ) {
        onNodeWithTag(TEST_ROUTE_BODY_TAG).requestFocus().performKeyInput {
            pressKey(Key.Escape)
        }
    }
    assertEquals(emptyList<KaniAction>(), root)

    // An unrelated key is never back, even with somewhere to go.
    val other = mutableListOf<KaniAction>()
    renderShell(
        state = ShellState(backStack = listOf(KaniDestination.Home, KaniDestination.Study)),
        recorded = other,
        routeBody = focusableBody,
    ) {
        onNodeWithTag(TEST_ROUTE_BODY_TAG).requestFocus().performKeyInput {
            pressKey(Key.Spacebar)
        }
    }
    assertEquals(emptyList<KaniAction>(), other)
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAConfirmEffectBlocksUntilAnsweredAndConsumesBeforeActing() {
    val confirm = KaniEffect.Confirm(
        title = literal("Delete every backup?"),
        body = literal("This cannot be undone."),
        confirmLabel = literal("Delete"),
        dismissLabel = literal("Keep"),
        confirm = KaniAction.Retry,
        isDestructive = true,
    )
    val queued = ShellState().enqueueForTest(confirm)
    val id = queued.effects.head!!.id

    // Accepting: consume first, then the answer. The order is what stops the
    // dialog re-showing over the result of its own action.
    val accepted = mutableListOf<KaniAction>()
    renderShell(state = queued, recorded = accepted) {
        onNodeWithTag(SHELL_CONFIRM_DIALOG_TEST_TAG).assertExists()
        onNodeWithTag(SHELL_CONFIRM_ACCEPT_TEST_TAG).performClick()
    }
    assertEquals(listOf(KaniAction.Consume.Effect(id), KaniAction.Retry), accepted)

    // Dismissing consumes the effect and dispatches nothing else: a destructive
    // action the user declined must not happen.
    val dismissed = mutableListOf<KaniAction>()
    renderShell(state = queued, recorded = dismissed) {
        onNodeWithTag(SHELL_CONFIRM_DISMISS_TEST_TAG).performClick()
    }
    assertEquals(listOf<KaniAction>(KaniAction.Consume.Effect(id)), dismissed)
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnUnansweredConfirmIsNotConsumed() {
    // The one effect that must survive being shown. If it were acknowledged on
    // display, a user who backgrounded the app mid-dialog would silently lose the
    // question.
    val recorded = mutableListOf<KaniAction>()
    renderShell(
        state = ShellState().enqueueForTest(
            KaniEffect.Confirm(
                title = literal("t"),
                body = literal("b"),
                confirmLabel = literal("ok"),
                dismissLabel = literal("no"),
                confirm = KaniAction.Retry,
            ),
        ),
        recorded = recorded,
    ) {
        onNodeWithTag(SHELL_CONFIRM_DIALOG_TEST_TAG).assertExists()
    }
    assertEquals(emptyList<KaniAction>(), recorded, "a shown-but-unanswered Confirm stays queued")
}

@OptIn(ExperimentalTestApi::class)
internal fun assertPlatformEffectsReachTheHandlerAndAreAcknowledged() {
    // Each effect the shell cannot perform itself: it must reach exactly its own
    // handler method and then be consumed, so the queue advances.
    val cases = listOf<Pair<KaniEffect, (RecordingEffectHandler) -> Unit>>(
        KaniEffect.OpenUrl("https://example.invalid/kani") to { handler ->
            assertEquals(listOf("https://example.invalid/kani"), handler.openedUrls)
        },
        KaniEffect.CopyToClipboard(text = "tag:kani_repaired is:suspended") to { handler ->
            assertEquals(listOf("tag:kani_repaired is:suspended"), handler.clipboardWrites)
        },
        KaniEffect.PickFile(purpose = KaniEffect.FilePurpose.BACKUP_EXPORT) to { handler ->
            assertEquals(1, handler.filePickers.size)
            assertEquals(
                KaniEffect.FilePurpose.BACKUP_EXPORT,
                handler.filePickers.single().purpose,
            )
        },
        KaniEffect.RequestFocus(target = "search-field") to { handler ->
            assertEquals(listOf("search-field"), handler.focusRequests)
        },
    )
    for ((effect, verify) in cases) {
        val handler = RecordingEffectHandler()
        val recorded = mutableListOf<KaniAction>()
        val state = ShellState().enqueueForTest(effect)
        val id = state.effects.head!!.id
        renderShell(state = state, effectHandler = handler, recorded = recorded) {
            waitForIdle()
        }
        verify(handler)
        assertContains(
            recorded,
            KaniAction.Consume.Effect(id),
            "$effect was performed but never acknowledged",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertOnlyTheHeadEffectIsDelivered() {
    // Two queued effects, one composition: only the head runs. The second is
    // revealed after the first is consumed, which is why both eventually show
    // rather than the first being overwritten.
    val handler = RecordingEffectHandler()
    val state = ShellState()
        .enqueueForTest(KaniEffect.OpenUrl("https://first.invalid"))
        .enqueueForTest(KaniEffect.OpenUrl("https://second.invalid"))
    renderShell(state = state, effectHandler = handler) {
        waitForIdle()
    }
    assertEquals(
        listOf("https://first.invalid"),
        handler.openedUrls,
        "the tail must wait for the head to be consumed",
    )
}

@OptIn(ExperimentalTestApi::class)
internal fun assertNoEffectsMeansNoHandlerCallsAndNoDispatches() {
    // The overwhelmingly common case, and the one a redelivery bug breaks: an
    // empty queue must be silent, not dispatch a consume for a nonexistent effect.
    val handler = RecordingEffectHandler()
    val recorded = mutableListOf<KaniAction>()
    renderShell(state = ShellState(), effectHandler = handler, recorded = recorded) {
        waitForIdle()
        onNodeWithTag(SHELL_CONFIRM_DIALOG_TEST_TAG).assertDoesNotExist()
    }
    assertEquals(emptyList<KaniAction>(), recorded)
    assertTrue(handler.openedUrls.isEmpty() && handler.focusRequests.isEmpty())
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheNoOpEffectHandlerSwallowsEverythingWithoutFailing() {
    // The default for tests and for a host that has not wired its adapters. It
    // must be inert rather than throwing, so an unwired host is merely inert too.
    val handler = ShellEffectHandler.NoOp
    handler.openUrl("https://example.invalid")
    handler.copyToClipboard("text")
    handler.pickFile(KaniEffect.PickFile(purpose = KaniEffect.FilePurpose.BACKUP_RESTORE))
    handler.requestFocus("target")
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheRouteSurfacesRenderEveryLoadableState() {
    // One answer per state, so no route has to invent its own. Idle is deliberately
    // blank: it means "nothing has been asked for yet", and a spinner there would
    // claim work that is not happening.
    renderRoute(RouteState<String>(destination = KaniDestination.Home)) {
        onNodeWithTag(SHELL_LOADING_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(SHELL_FAILURE_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(ROUTE_CONTENT_TAG).assertDoesNotExist()
    }
    renderRoute(
        RouteState<String>(destination = KaniDestination.Home, content = Loadable.Loading),
    ) {
        onNodeWithTag(SHELL_LOADING_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(ROUTE_CONTENT_TAG).assertDoesNotExist()
    }
    renderRoute(
        RouteState(
            destination = KaniDestination.Home,
            content = Loadable.Loaded("42 due"),
        ),
    ) {
        onNodeWithTag(SHELL_LOADING_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(ROUTE_CONTENT_TAG).assertIsDisplayed()
        onNodeWithText("42 due").assertIsDisplayed()
    }
    renderRoute(
        RouteState<String>(
            destination = KaniDestination.Home,
            content = Loadable.Failed(
                PresentationFailure(PresentationFailure.Kind.PROVIDER_UNAVAILABLE),
            ),
        ),
    ) {
        onNodeWithTag(SHELL_FAILURE_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(ROUTE_CONTENT_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertARefreshKeepsThePreviousContentOnScreen() {
    // The behavior the Android screens each decided for themselves, which is why
    // some flashed empty on refresh. A refresh shows a hint *and* the old value.
    renderRoute(
        RouteState(
            destination = KaniDestination.Home,
            content = Loadable.Refreshing("18 due"),
        ),
    ) {
        onNodeWithTag(SHELL_REFRESHING_TEST_TAG).assertIsDisplayed()
        onNodeWithText("18 due").assertIsDisplayed()
        // Not the blocking surface: the user is still reading something valid.
        onNodeWithTag(SHELL_LOADING_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertRetryIsOfferedOnlyForRetryableFailures() {
    // The kind's own answer, not the caller's guess: retry on a configuration
    // error sends the user in a loop, and withholding it on a transient one
    // strands them. Dismiss is always available.
    for (kind in PresentationFailure.Kind.entries) {
        val failure = PresentationFailure(kind)
        val recorded = mutableListOf<KaniAction>()
        renderRoute(
            RouteState<String>(destination = KaniDestination.Home).withFailure(failure),
            recorded = recorded,
        ) {
            onNodeWithTag(SHELL_FAILURE_TEST_TAG).assertIsDisplayed()
            onNodeWithTag(SHELL_DISMISS_FAILURE_TEST_TAG).assertIsDisplayed()
            if (failure.isRetryable) {
                onNodeWithTag(SHELL_RETRY_TEST_TAG).assertIsDisplayed().performClick()
            } else {
                onNodeWithTag(SHELL_RETRY_TEST_TAG).assertDoesNotExist()
            }
            onNodeWithTag(SHELL_DISMISS_FAILURE_TEST_TAG).performClick()
        }
        val expected = if (failure.isRetryable) {
            listOf(KaniAction.Retry, KaniAction.Consume.Failure)
        } else {
            listOf(KaniAction.Consume.Failure)
        }
        assertEquals(expected, recorded, "$kind dispatched the wrong actions")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAFailureAlongsideContentBannersRatherThanReplaces() {
    // `withFailure` keeps the last good value on purpose, so blanking the screen
    // would throw away information the user already had. And the error must appear
    // exactly once, not both as a banner and as a replacement.
    renderRoute(
        RouteState(destination = KaniDestination.Home, content = Loadable.Loaded("7 due"))
            .withFailure(PresentationFailure(PresentationFailure.Kind.TRANSIENT)),
    ) {
        onAllNodes(hasTestTag(SHELL_FAILURE_TEST_TAG)).assertCountEquals(1)
        onNodeWithText("7 due").assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnUnavailableCapabilityIsExplainedRatherThanOffered() {
    // The visible half of the capability contract: a screen either offers a working
    // control or explains why it cannot. What it must never do is offer a control
    // that silently does nothing.
    for (capability in PlatformCapability.entries) {
        renderComposable(
            content = {
                ShellCapabilityExplanation(
                    gate = CapabilityGate.Unavailable(capability),
                    copy = rememberShellCopy(),
                )
            },
        ) {
            val node = onNodeWithTag(SHELL_CAPABILITY_TEST_TAG).assertIsDisplayed()
            // The explanation has to say something. An empty panel is the same
            // dead end as an inert button, just quieter.
            assertTrue(
                node.subtreeTextOrEmpty().isNotBlank(),
                "$capability rendered an empty explanation",
            )
        }
    }
}

package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartupTest {
    @Test
    fun focusKanjiIntentAcceptsExactlyOneTrimmedKanjiGlyph() {
        assertEquals(
            "学",
            focusKanjiDetailFromIntent(
                Intent().putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "  学  "),
            ),
        )
    }

    @Test
    fun focusKanjiIntentRejectsAbsentBlankKanaAndMultiGlyphValues() {
        assertNull(focusKanjiDetailFromIntent(null))
        assertNull(focusKanjiDetailFromIntent(Intent()))
        assertNull(
            focusKanjiDetailFromIntent(
                Intent().putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "   "),
            ),
        )
        assertNull(
            focusKanjiDetailFromIntent(
                Intent().putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "かな"),
            ),
        )
        assertNull(
            focusKanjiDetailFromIntent(
                Intent().putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "学習"),
            ),
        )
    }

    @Test
    fun coldStartFocusIntentRoutesToTheExactExistingDetailTarget() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            FocusTrackingStartupActivity::class.java,
        ).putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "学")
        val controller = Robolectric.buildActivity(FocusTrackingStartupActivity::class.java, intent)
        val activity = controller.get()

        controller.create()

        assertEquals("学", activity.openedFocusKanji)
        assertEquals(0, activity.renderHomeCalls)
        assertFalse(activity.intent.hasExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL))
    }

    @Test
    fun invalidColdStartFocusIntentFallsBackToHome() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            FocusTrackingStartupActivity::class.java,
        ).putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "学習")
        val controller = Robolectric.buildActivity(FocusTrackingStartupActivity::class.java, intent)
        val activity = controller.get()

        controller.create()

        assertNull(activity.openedFocusKanji)
        assertEquals(1, activity.renderHomeCalls)
    }

    @Test
    fun warmFocusIntentRoutesToTheExactExistingDetailTarget() {
        val controller = Robolectric.buildActivity(FocusTrackingStartupActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.openedFocusKanji = null
        activity.renderHomeCalls = 0

        controller.newIntent(
            Intent().putExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL, "学"),
        )

        assertEquals("学", activity.openedFocusKanji)
        assertEquals(0, activity.renderHomeCalls)
        assertFalse(activity.intent.hasExtra(MainActivityBase.EXTRA_OPEN_KANJI_DETAIL))
    }

    @Test
    fun deniedNotificationPermissionKeepsTheSelectedReminderEnabled() {
        val controller = Robolectric.buildActivity(NoopStartupActivity::class.java)
        val activity = controller.create().get()
        val selected = LocalStoreBase.ReminderSettings(true, 19, 40)

        activity.preserveReminderAfterDeniedPermission(selected)

        val saved = activity.store.reminderSettings()
        assertTrue(saved.enabled)
        assertEquals(19, saved.hour)
        assertEquals(40, saved.minute)
    }

    @Test
    fun permissionCallbacksDoNotReplaceAnUnrelatedRoute() {
        val controller = Robolectric.buildActivity(PermissionRouteTrackingStartupActivity::class.java)
        val activity = controller.create().get()
        activity.currentRoute = MainActivityBase.NAV_STATS_ROUTE

        activity.handleAnkiPermissionResult()

        assertEquals(MainActivityBase.NAV_STATS_ROUTE, activity.currentRoute)
        activity.pendingReminderSettings = LocalStoreBase.ReminderSettings(true, 8, 30)
        activity.handlePostNotificationPermission(false)
        assertEquals(MainActivityBase.NAV_STATS_ROUTE, activity.currentRoute)
    }

    @Test
    fun focusDetailRouteDoesNotLeakTheGlyphIntoBrowseState() {
        val controller = Robolectric.buildActivity(NoopStartupActivity::class.java)
        val activity = controller.create().get()

        assertTrue(activity.openFocusKanjiDetail("学"))

        assertEquals("", activity.activeBrowseQuery)
        assertFalse(activity.activeBrowseSimilarOnly)
        assertTrue(activity.activeBrowseAllKanji)
    }

    @Test
    fun launcherShortcutActionsUseOnlyAllowlistedDestinations() {
        assertEquals(
            LauncherShortcutDestination.STUDY,
            launcherShortcutDestination(MainActivityBase.ACTION_OPEN_STUDY),
        )
        assertEquals(
            LauncherShortcutDestination.BROWSE,
            launcherShortcutDestination(MainActivityBase.ACTION_OPEN_BROWSE),
        )
        assertEquals(
            LauncherShortcutDestination.GAMES,
            launcherShortcutDestination(MainActivityBase.ACTION_OPEN_GAMES),
        )
        assertNull(launcherShortcutDestination(null))
        assertNull(launcherShortcutDestination(Intent.ACTION_MAIN))
        assertNull(launcherShortcutDestination(Intent.ACTION_VIEW))
        assertNull(launcherShortcutDestination("dev.bee.kanjianki.action.OPEN_SETTINGS"))
    }

    @Test
    fun startQueuesBackgroundStartupTasksInsteadOfRunningThemInline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, Intent(context, NoopStartupActivity::class.java))
            val activity = controller.get()
            val ioTasks = QueueingExecutorService()
            val maintenanceTasks = QueueingExecutorService()
            replaceField(activity, "io", ioTasks)
            replaceField(activity, "maintenance", maintenanceTasks)

            controller.create().start().resume()

            // io stays reserved for user-facing route loads. Only the theme-cache warm (which
            // also front-loads any pending DB migration) is queued there at startup; the home
            // route load is not, because NoopStartupActivity overrides renderHome to a no-op.
            assertEquals(1, ioTasks.pendingCount())
            // Background maintenance runs on the separate maintenance executor so it cannot block
            // route loads on cold boot: (1) the scheduler block (auto-sync/auto-update/backup,
            // incl. first-time WorkManager init), and (2) the resume-time update-install gating,
            // which reads auto-update status off the UI thread (ANR fix). The reminder re-arm is
            // deliberately still pending because this no-op activity never settles a real async
            // route. Heavy asset warmup runs on its own dedicated thread.
            assertEquals(2, maintenanceTasks.pendingCount())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    @Config(sdk = [32])
    fun normalLaunchDoesNotPromptForAnkiPermissionBeforeHomeRender() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        registerAnkiDroidProvider(context)

        val controller = Robolectric.buildActivity(
            PermissionTrackingStartupActivity::class.java,
            Intent(context, PermissionTrackingStartupActivity::class.java),
        )
        val activity = controller.get()

        controller.create().start().resume()

        assertEquals(1, activity.renderHomeCalls)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun normalLaunchReturnsToPendingAnsweredStudyCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        StudyPendingAnswerStore(preferences).save(
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = "startup-pending-token",
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.INCORRECT,
                    selectedAnswer = "wrong",
                ),
                kanji = "弱",
                taskType = "typing_meaning",
                writingRequired = false,
                prompt = "",
            ),
        )
        try {
            val controller = Robolectric.buildActivity(
                PendingAnswerStartupActivity::class.java,
                Intent(context, PendingAnswerStartupActivity::class.java),
            )
            val activity = controller.get()

            controller.create().start().resume()

            assertEquals(1, activity.renderStudyCalls)
            assertEquals(0, activity.renderHomeCalls)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun activeDraftDoesNotOverrideExplicitUpdateAndIsKeptForManualStudy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = StudySessionRecoveryStore(preferences)
        assertNotNull(store.replaceWithActive(activeSnapshot("explicit-update-token")))
        val intent = Intent(context, PendingAnswerStartupActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_OPEN_UPDATE, true)
        }

        val controller = Robolectric.buildActivity(PendingAnswerStartupActivity::class.java, intent)
        val activity = controller.get()
        try {
            controller.create().start().resume()

            assertEquals(0, activity.renderStudyCalls)
            val dormant = store.readActive()
            assertNotNull(dormant)
            assertFalse(requireNotNull(dormant).resumeOnOrdinaryLaunch)
        } finally {
            preferences.edit().clear().commit()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun recreationMarkerRestoresStudyAheadOfStaleOriginalUpdateIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val intent = Intent(context, PendingAnswerStartupActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_OPEN_UPDATE, true)
        }
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val first = Robolectric.buildActivity(PendingAnswerStartupActivity::class.java, intent)
        val state = Bundle()
        var second: org.robolectric.android.controller.ActivityController<PendingAnswerStartupActivity>? = null
        try {
            val firstActivity = first.create().start().resume().get()
            val item = startupStudyItem("recreation-token")
            firstActivity.acceptNewActiveStudySession(
                RecordsSchedulerModels.StudySession(
                    item,
                    null,
                    "recreation-token",
                    StudyTaskTypes.KANJI_MEANING,
                    false,
                    "prompt",
                ),
                StudyPromptSource.REASON_TEXT,
                latestSuccessfulSyncAtMillis = 0L,
            )
            first.pause().saveInstanceState(state).stop().destroy()

            second = Robolectric.buildActivity(PendingAnswerStartupActivity::class.java, intent)
            val recreated = second.create(state).start().resume().get()

            assertEquals(1, recreated.renderStudyCalls)
            assertEquals(0, recreated.renderHomeCalls)
        } finally {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            second?.pause()?.stop()?.destroy()
        }
    }

    @Test
    fun newerExplicitExitOverridesOlderSavedStudyMarker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val intent = Intent(context, PendingAnswerStartupActivity::class.java)
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val first = Robolectric.buildActivity(PendingAnswerStartupActivity::class.java, intent)
        val state = Bundle()
        var second: org.robolectric.android.controller.ActivityController<PendingAnswerStartupActivity>? = null
        try {
            val firstActivity = first.create().start().resume().get()
            val item = startupStudyItem("dormant-recreation-token")
            firstActivity.acceptNewActiveStudySession(
                RecordsSchedulerModels.StudySession(
                    item,
                    null,
                    "dormant-recreation-token",
                    StudyTaskTypes.KANJI_MEANING,
                    false,
                    "prompt",
                ),
                StudyPromptSource.REASON_TEXT,
                latestSuccessfulSyncAtMillis = 0L,
            )
            first.pause().saveInstanceState(state)
            firstActivity.disableStudyOrdinaryResume()
            first.stop().destroy()

            second = Robolectric.buildActivity(PendingAnswerStartupActivity::class.java, intent)
            val recreated = second.create(state).start().resume().get()

            assertEquals(0, recreated.renderStudyCalls)
            assertEquals(1, recreated.renderHomeCalls)
            assertFalse(StudySessionRecoveryStore(preferences).shouldResumeOnOrdinaryLaunch())
        } finally {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            second?.pause()?.stop()?.destroy()
        }
    }

    @Test
    fun activityRecreationRestoresStatsInsteadOfFallingBackHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, RouteRestorationStartupActivity::class.java)
        val state = Bundle()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val first = Robolectric.buildActivity(RouteRestorationStartupActivity::class.java, intent)
        var second: org.robolectric.android.controller.ActivityController<RouteRestorationStartupActivity>? = null
        try {
            val firstActivity = first.create().start().resume().get()
            firstActivity.currentRoute = MainActivityBase.NAV_STATS_ROUTE
            first.pause().saveInstanceState(state).stop().destroy()

            second = Robolectric.buildActivity(RouteRestorationStartupActivity::class.java, intent)
            val recreated = second.create(state).start().resume().get()

            assertEquals(1, recreated.renderStatsCalls)
            assertEquals(0, recreated.renderHomeCalls)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            second?.pause()?.stop()?.destroy()
        }
    }

    @Test
    fun activityRecreationRestoresBrowseRouteAndArgumentsInsteadOfFallingBackHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, RouteRestorationStartupActivity::class.java)
        val state = Bundle()
        val route = HomeRouteRestoration.browse("学", onlySimilarKanji = true, allKanjiScope = false)
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val first = Robolectric.buildActivity(RouteRestorationStartupActivity::class.java, intent)
        var second: org.robolectric.android.controller.ActivityController<RouteRestorationStartupActivity>? = null
        try {
            val firstActivity = first.create().start().resume().get()
            firstActivity.currentRoute = MainActivityBase.NAV_HOME_ROUTE
            firstActivity.currentHomeRouteRestoration = route
            first.pause().saveInstanceState(state).stop().destroy()

            second = Robolectric.buildActivity(RouteRestorationStartupActivity::class.java, intent)
            val recreated = second.create(state).start().resume().get()

            assertEquals(route, recreated.restoredHomeRoute)
            assertEquals(0, recreated.renderHomeCalls)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            second?.pause()?.stop()?.destroy()
        }
    }

    @Test
    fun onlyKnownSettingsAndStatsRoutesAreRestorable() {
        val restorable = listOf(
            MainActivityBase.NAV_STATS_ROUTE,
            MainActivityBase.NAV_SETTINGS_ROUTE,
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE,
            MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
            MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE,
            MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE,
            MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE,
            MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE,
            MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE,
            MainActivityBase.NAV_SETTINGS_HOW_IT_WORKS_ROUTE,
        )

        assertTrue(restorable.all(MainActivityBase::isRestorableRoute))
        assertFalse(MainActivityBase.isRestorableRoute(MainActivityBase.NAV_HOME_ROUTE))
        assertFalse(MainActivityBase.isRestorableRoute(MainActivityBase.NAV_STUDY))
        assertFalse(MainActivityBase.isRestorableRoute("malformed"))
    }

    @Test
    fun screenshotHarnessDoesNotMutateProductionStudyRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = StudySessionRecoveryStore(preferences)
        assertNotNull(store.replaceWithActive(activeSnapshot("harness-token")))
        val rawBefore = preferences.getString("snapshot", null)
        val intent = Intent(context, RecoveryAwareStartupActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
        }
        val controller = Robolectric.buildActivity(RecoveryAwareStartupActivity::class.java, intent)
        try {
            controller.create().start().resume()

            assertEquals(rawBefore, preferences.getString("snapshot", null))
            assertTrue(store.shouldResumeOnOrdinaryLaunch())
        } finally {
            preferences.edit().clear().commit()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun androidXStartupProviderIsRemovedFromMergedManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startupAuthority = context.packageName + ".androidx-startup"

        assertNull(
            "Kani does not use AndroidX Startup initializers; keep the provider out of cold startup.",
            context.packageManager.resolveContentProvider(startupAuthority, PackageManager.GET_META_DATA),
        )
    }

    @Test
    fun coldLaunchHelpersStayLazyUntilRouteNeedsThem() {
        assertLazyDelegates(
            MainActivityBase::class.java,
            "permissionHandler",
            "writingRecognizerProvider",
            "studyPlanProvider",
            "shellHost",
            "startup",
            "activityLifecycle",
        )
        assertLazyDelegates(MainActivityHome::class.java, "focusQueue", "browseDetail")
        assertLazyDelegates(MainActivityGames::class.java, "gameEngine", "gameRandom")
        assertLazyDelegates(
            MainActivityStudy::class.java,
            "flashcardUi",
            "writingUi",
            "writingFlow",
            "writingCheck",
            "writingReview",
            "doneActions",
            "choiceSessions",
            "studyProgress",
            "moreNewCards",
            "studyState",
            "writingSession",
            "dictionaryLookupProvider",
            "studyQueueCoordinator",
        )
    }

    @Test
    fun screenshotLaunchAppliesRequestedThemeChoiceBeforeRendering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val intent = Intent(context, NoopStartupActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_THEME, "dark")
            }
            val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, intent)
            val activity = controller.get()

            controller.create().start().resume()

            assertEquals(KaniThemeChoice.DARK, activity.screenshotThemeChoiceOverride)
            assertEquals(KaniThemeChoice.DARK, activity.store.appThemeChoice())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun screenshotLaunchAppliesRequestedLocaleBeforeRendering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val previousLocale = Locale.getDefault()
        try {
            val intent = Intent(context, NoopStartupActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_LOCALE, "ja")
            }
            val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, intent)

            controller.create().start().resume()

            assertEquals("ja", Locale.getDefault().language)
        } finally {
            Locale.setDefault(previousLocale)
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun screenshotLaunchReadsRequestedScrollPositionAndOffset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, NoopStartupActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_SCROLL_POSITION, "middle")
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_SCROLL_Y, 1080)
        }
        val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, intent)
        val activity = controller.get()

        controller.create().start().resume()

        assertEquals("middle", activity.screenshotScrollPositionLabel())
        assertEquals(1080, activity.screenshotScrollY())
    }

    private class FocusTrackingStartupActivity : MainActivity() {
        var openedFocusKanji: String? = null
        var renderHomeCalls = 0

        override fun openFocusKanjiDetail(kanji: String): Boolean {
            openedFocusKanji = kanji
            return true
        }

        override fun renderHome() {
            renderHomeCalls += 1
        }
    }

    private class NoopStartupActivity : MainActivity() {
        override fun renderHome() {
            // Keep the test focused on startup scheduling, not home rendering.
        }
    }

    private class PermissionTrackingStartupActivity : MainActivity() {
        var renderHomeCalls = 0

        override fun renderHome() {
            renderHomeCalls += 1
        }
    }

    private class PermissionRouteTrackingStartupActivity : MainActivity() {
        override fun renderHome() {
            currentRoute = MainActivityBase.NAV_HOME_ROUTE
        }
    }

    private class PendingAnswerStartupActivity : MainActivity() {
        var renderHomeCalls = 0
        var renderStudyCalls = 0

        override fun renderHome() {
            renderHomeCalls += 1
        }

        override fun renderStudy() {
            renderStudyCalls += 1
        }

        override fun renderStudyRecoveryOnly() {
            renderStudyCalls += 1
        }
    }

    private class RouteRestorationStartupActivity : MainActivity() {
        var renderHomeCalls = 0
        var renderStatsCalls = 0
        var restoredHomeRoute: HomeRouteRestoration? = null

        override fun renderHome() {
            renderHomeCalls += 1
        }

        override fun renderStats() {
            renderStatsCalls += 1
        }

        override fun renderRestoredHomeRoute(route: HomeRouteRestoration) {
            restoredHomeRoute = route
        }
    }

    private class RecoveryAwareStartupActivity : MainActivity() {
        override fun renderHome() {
            disableStudyOrdinaryResume()
        }
    }

    private fun registerAnkiDroidProvider(context: Context) {
        shadowOf(context.packageManager).addOrUpdateProvider(
            ProviderInfo().apply {
                authority = "com.ichi2.anki.api.provider"
                name = "FakeAnkiDroidProvider"
                packageName = "com.ichi2.anki"
            },
        )
    }

    private fun replaceField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        field.set(activity, value)
    }

    private fun assertLazyDelegates(owner: Class<*>, vararg propertyNames: String) {
        for (propertyName in propertyNames) {
            val delegateField = owner.declaredFields.firstOrNull { it.name == "$propertyName\$delegate" }
            assertNotNull(
                "${owner.simpleName}.$propertyName should stay lazy to keep cold route startup lean.",
                delegateField,
            )
            assertTrue(
                "${owner.simpleName}.$propertyName should be backed by kotlin.Lazy.",
                Lazy::class.java.isAssignableFrom(delegateField!!.type),
            )
        }
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun activeSnapshot(token: String): StudyActiveSessionSnapshot = StudyActiveSessionSnapshot(
        sessionToken = token,
        kanji = "復",
        answerSignatureDigest = studyAnswerSignatureDigest("復|復習|ふくしゅう|review"),
        schedulerRevision = 1L,
        routingVersion = 1,
        taskType = StudyTaskTypes.KANJI_MEANING,
        promptSource = StudyPromptSource.REASON_TEXT,
        sourceSyncFinishedAtMillis = 0L,
    )

    private fun startupStudyItem(token: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem("復", "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .answerSignature("復|復習|ふくしゅう|review")
            .activeToken(token)
            .schedulerRevision(1L)
            .routingVersion(1)
            .build()

    private class QueueingExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            val remaining = tasks.toMutableList()
            tasks.clear()
            return remaining
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun pendingCount(): Int = tasks.size
    }
}

package dev.bee.kanjianki.baseline

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.GamesPlayScreen
import dev.bee.kanjianki.GamesQuestionCard
import dev.bee.kanjianki.GamesResultCard
import dev.bee.kanjianki.GamesResultModel
import dev.bee.kanjianki.GamesScoreStripModel
import dev.bee.kanjianki.GamesUnavailableCard
import dev.bee.kanjianki.GamesUnavailableModel
import dev.bee.kanjianki.HomeRouteErrorScreen
import dev.bee.kanjianki.HomeRouteLoadingScreen
import dev.bee.kanjianki.HomeFocusQueueCardModel
import dev.bee.kanjianki.HomeFocusQueueTagModel
import dev.bee.kanjianki.HomeScreen
import dev.bee.kanjianki.HomeScreenModel
import dev.bee.kanjianki.HomeTodayPlanModel
import dev.bee.kanjianki.KaniNavActions
import dev.bee.kanjianki.MainActivity
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.MainActivityComposeRoute
import dev.bee.kanjianki.MainActivityRouteScrollMode
import dev.bee.kanjianki.MainActivityShellModel
import dev.bee.kanjianki.MissingKanjiContentModel
import dev.bee.kanjianki.MissingKanjiDestinationModel
import dev.bee.kanjianki.MissingKanjiFrequencyModel
import dev.bee.kanjianki.MissingKanjiPreset
import dev.bee.kanjianki.MissingKanjiPrimaryAction
import dev.bee.kanjianki.MissingKanjiProviderAvailability
import dev.bee.kanjianki.MissingKanjiReportUiModel
import dev.bee.kanjianki.MissingKanjiScanProgressState
import dev.bee.kanjianki.MissingKanjiScanSummaryModel
import dev.bee.kanjianki.MissingKanjiScreen
import dev.bee.kanjianki.MissingKanjiScreenModel
import dev.bee.kanjianki.ProgressAnalyticsDashboardScreen
import dev.bee.kanjianki.StudyDoneScreen
import dev.bee.kanjianki.StudyDoneScreenModel
import dev.bee.kanjianki.SyncResultScreen
import dev.bee.kanjianki.SyncResultScreenModel
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.progress.progressAnalyticsSnapshot
import kotlinx.coroutines.runBlocking

/**
 * Executable render boundary for every catalog entry.
 *
 * Durable destinations use the production route methods. Representative
 * states use the same production composables with callback-free, sanitized
 * models so loading/error/result pixels never depend on timing or a provider.
 */
internal object AndroidRouteBaselineFixtureRenderer {
    fun render(
        renderer: AndroidRouteBaselineCatalog.Renderer,
        activity: MainActivity,
        captureCase: AndroidRouteBaselineCatalog.CaptureCase,
    ) {
        when (renderer) {
            AndroidRouteBaselineCatalog.Renderer.SCREENSHOT_INTENT -> Unit
            AndroidRouteBaselineCatalog.Renderer.HOME -> renderHome(activity, captureCase.state)
            AndroidRouteBaselineCatalog.Renderer.HOME_FOCUS_QUEUE -> activity.renderFocusQueue()
            AndroidRouteBaselineCatalog.Renderer.HOME_RECENT_MISTAKES -> activity.renderRecentMistakes()
            AndroidRouteBaselineCatalog.Renderer.HOME_BROWSE ->
                activity.renderBrowseKanji(if (captureCase.state == "data") "裂" else "")
            AndroidRouteBaselineCatalog.Renderer.HOME_DETAIL -> {
                activity.strokeGuides = emptyMap()
                activity.renderDetail("裂", true, "裂")
            }
            AndroidRouteBaselineCatalog.Renderer.HOME_READ_ONLY_DETAIL -> {
                activity.strokeGuides = emptyMap()
                activity.renderReadOnlyDetail(if (captureCase.state == "data") "裂" else "A", "裂")
            }
            AndroidRouteBaselineCatalog.Renderer.STATS -> {
                // Exercise the production empty-data projection and composable
                // synchronously with a fixed clock. The ordinary route method uses
                // wall time for its chart windows, which is not a stable golden.
                val stats = runBlocking {
                    activity.statsUseCases.loadForDisplay(FIXTURE_TIME_MILLIS)
                }
                val settings = runBlocking { activity.settingsUseCases.load() }
                val state = progressAnalyticsSnapshot(
                    stats,
                    FIXTURE_TIME_MILLIS,
                    settings.studyLadder,
                )
                activity.showFixture(MainActivityBase.NAV_STATS_ROUTE) {
                    ProgressAnalyticsDashboardScreen(state = state)
                }
            }
            AndroidRouteBaselineCatalog.Renderer.STUDY -> renderStudy(activity, captureCase.state)
            AndroidRouteBaselineCatalog.Renderer.SHARED_LOADING ->
                renderLoading(activity, "Loading state", "shared")
            AndroidRouteBaselineCatalog.Renderer.SHARED_ERROR ->
                renderError(activity, "Something went wrong", "shared")
            AndroidRouteBaselineCatalog.Renderer.SETTINGS_LOADING ->
                renderLoading(activity, "Settings", MainActivityBase.NAV_SETTINGS_ROUTE)
            AndroidRouteBaselineCatalog.Renderer.SETTINGS_ERROR ->
                renderError(activity, "Settings", MainActivityBase.NAV_SETTINGS_ROUTE)
            AndroidRouteBaselineCatalog.Renderer.GAMES -> renderGames(activity, captureCase.state)
            AndroidRouteBaselineCatalog.Renderer.MISSING_KANJI ->
                renderMissingKanji(activity, captureCase.state)
            AndroidRouteBaselineCatalog.Renderer.HOME_SYNC -> renderSync(activity, captureCase.state)
        }
    }

    private fun renderHome(activity: MainActivity, state: String) {
        val hasData = when (state) {
            "data" -> true
            "empty" -> false
            else -> error("Unsupported Home baseline state: $state")
        }
        val model = HomeScreenModel(
            title = HomeTextCopy.appTitle(),
            subtitle = HomeTextCopy.appSubtitle(),
            metrics = emptyList(),
            todayPlan = if (hasData) {
                HomeTodayPlanModel(
                    title = HomeTextCopy.todayPlanTitle(),
                    summary = "1 due now",
                    details = listOf("Sanitized local study fixture"),
                    actionLabel = HomeTextCopy.studyNowLabel(),
                    onClick = {},
                )
            } else {
                HomeTodayPlanModel(
                    title = HomeTextCopy.todayPlanTitle(),
                    summary = "Sync needed before Kani can judge progress",
                    details = emptyList(),
                    actionLabel = HomeTextCopy.syncAnkiDroidLabel(),
                    onClick = {},
                )
            },
            deckOverviewRows = if (hasData) listOf("Due 1 · New 0") else emptyList(),
            showSyncCta = !hasData,
            syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
            studyLabel = HomeTextCopy.studyNowLabel(),
            onSync = {},
            onStudy = {},
            actions = emptyList(),
            focusTitle = HomeTextCopy.focusQueueTitle(),
            focusActionLabel = if (hasData) HomeTextCopy.viewAllLabel() else null,
            onFocusAction = if (hasData) ({}) else null,
            emptyTitle = if (hasData) null else HomeTextCopy.noKanjiQueuedTitle(),
            emptyBody = if (hasData) null else HomeTextCopy.homeNoKanjiQueuedBody(),
            previewCards = if (hasData) {
                listOf(
                    HomeFocusQueueCardModel(
                        kanji = "裂",
                        meaning = "Split",
                        sourceEvidence = "From AnkiDroid",
                        reasonLine = "Due now",
                        body = "裂語 · レツ",
                        tags = listOf(
                            HomeFocusQueueTagModel("Recognition", MainActivityBase.BLUE),
                        ),
                        accentColor = MainActivityBase.TEAL,
                        onClick = {},
                    ),
                )
            } else {
                emptyList()
            },
            studyRemainingCount = if (hasData) 1 else 0,
            firstRunOfflineNotice = if (hasData) null else HomeTextCopy.firstRunOfflineNotice(),
        )
        val readyTag = if (hasData) {
            AndroidRouteBaselineCatalog.HOME_DATA_READY_TAG
        } else {
            AndroidRouteBaselineCatalog.HOME_EMPTY_READY_TAG
        }
        activity.showFixture(MainActivityBase.NAV_HOME_ROUTE) {
            Box(Modifier.testTag(readyTag)) {
                HomeScreen(model)
            }
        }
    }

    private fun renderStudy(activity: MainActivity, state: String) {
        when (state) {
            "data", "active" -> renderActiveStudy(activity)
            "loading" -> activity.renderStudyLoading(studySessionActive = false)
            "error" -> renderError(activity, StudyTextCopy.studyPracticeTitle(), MainActivityBase.NAV_STUDY)
            "empty" -> activity.showFixture(MainActivityBase.NAV_STUDY) {
                StudyDoneScreen(
                    StudyDoneScreenModel(
                        modeLabel = StudyTextCopy.practiceLabel(),
                        title = StudyTextCopy.studyPracticeTitle(),
                        headline = StudyTextCopy.nothingToStudyHeadline(),
                        body = StudyTextCopy.syncAnkiDroidFirstBody(),
                        summaryLines = emptyList(),
                        showDoneActions = false,
                        availableStudyMoreNewCards = 0,
                        showBackHome = true,
                        backHomePrimary = true,
                        onStudyMore = NO_OP,
                        onContinueAll = NO_OP,
                        onBackHome = NO_OP,
                    ),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            "done" -> activity.showFixture(MainActivityBase.NAV_STUDY) {
                StudyDoneScreen(
                    StudyDoneScreenModel(
                        modeLabel = StudyTextCopy.practiceLabel(),
                        title = StudyTextCopy.studyDoneTitle(),
                        headline = null,
                        body = StudyTextCopy.studyRunDoneBody(),
                        summaryLines = listOf(
                            StudyTextCopy.movedForwardSummary(3),
                            StudyTextCopy.missedSummary(1),
                        ),
                        showDoneActions = true,
                        availableStudyMoreNewCards = 2,
                        showBackHome = false,
                        backHomePrimary = false,
                        onStudyMore = NO_OP,
                        onContinueAll = NO_OP,
                        onBackHome = NO_OP,
                    ),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            else -> error("Unsupported Study baseline state: $state")
        }
    }

    private fun renderActiveStudy(activity: MainActivity) {
        val row = RecordsImportModels.DashboardRow(
            "裂",
            1_000,
            "split",
            "レツ",
            "裂",
            10,
            "baseline",
            "baseline fixture",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
        val token = "goal165-study-token"
        val item = RecordsStudyModels.StudyItem(
            row.kanji,
            "review",
            0L,
            1.0,
            5.0,
            1,
            0,
            0,
            1,
            0,
            0,
            0,
            false,
            "",
            0L,
            0,
            "goal165-signature",
            token,
            0L,
        )
        val session = RecordsSchedulerModels.StudySession(
            item,
            row,
            token,
            BridgeScheduler.TASK_KANJI_MEANING,
            false,
            row.primaryMeaning,
        )
        activity.activeStudyPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
            20,
            1,
            1,
            listOf(row.kanji),
            0,
            false,
            "One left",
        )
        activity.activeSession = session
        activity.studySessionTracker.setTargetCount(1)
        activity.cancelPendingHomeRouteLoads()
        activity.startActiveStudyTask(
            activity.sessionTaskKey(session),
            row.kanji,
            session.taskType,
            FIXTURE_TIME_MILLIS,
        )
        // The production study renderer supplies studySessionActive=true to
        // the shell, so the active-card golden intentionally has no bottom nav.
        activity.renderSession(session)
        activity.revealFlashcardAnswer()
    }

    private fun renderGames(activity: MainActivity, state: String) {
        val score = GamesScoreStripModel(
            roundLabel = KanjiGameCopy.roundLabel(),
            roundValue = "2/10",
            scoreLabel = KanjiGameCopy.scoreLabel(),
            scoreValue = "1/10",
            streakLabel = KanjiGameCopy.streakLabel(),
            streakValue = "1",
            scoreDescription = "Score 1 out of 10",
        )
        activity.showFixture("games") {
            when (state) {
                "unavailable" -> GamesPlayScreen(
                    title = KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MEANING_POP),
                    onGames = {},
                ) {
                    GamesUnavailableCard(
                        GamesUnavailableModel(
                            KanjiGameCopy.gameNotReadyTitle(),
                            KanjiGameCopy.gameNotReadyBody(),
                        ),
                    )
                }
                "question" -> GamesPlayScreen(
                    title = KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MEANING_POP),
                    onGames = {},
                    score = score,
                ) {
                    GamesQuestionCard(
                        question = gameQuestion(),
                        onChoiceSelected = {},
                    )
                }
                "result" -> GamesPlayScreen(
                    title = KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MEANING_POP),
                    onGames = {},
                    score = score,
                ) {
                    GamesResultCard(
                        GamesResultModel(
                            title = "Correct",
                            titleColor = MainActivityBase.TEAL,
                            finalScore = null,
                            accuracy = null,
                            answer = "Answer: language",
                            selectedAnswer = "You chose language",
                            explanation = "語 means language.",
                            primaryLabel = KanjiGameCopy.nextLabel(),
                            primaryColor = MainActivityBase.CORAL,
                            onPrimary = NO_OP,
                            onGames = NO_OP,
                        ),
                    )
                }
                else -> error("Unsupported Games baseline state: $state")
            }
        }
    }

    private fun gameQuestion(): KanjiGameEngine.GameQuestion =
        KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.MEANING_POP,
            "語",
            "語",
            "Pick the meaning",
            "language",
            listOf("language", "word"),
            "語 means language.",
        )

    private fun renderMissingKanji(activity: MainActivity, state: String) {
        val content = when (state) {
            "scanning" -> MissingKanjiContentModel.Scanning(
                MissingKanjiScanProgressState().apply {
                    update(notesScanned = 240, uniqueKanjiCount = 812, skippedNotes = 2)
                },
            )
            "provider-error" -> MissingKanjiContentModel.Error("provider_unavailable")
            "empty" -> MissingKanjiContentModel.Report(
                MissingKanjiReportUiModel(
                    reportKey = "goal165-empty",
                    scan = MissingKanjiScanSummaryModel(
                        scanId = 1L,
                        completedAtMillis = FIXTURE_TIME_MILLIS,
                        notesScanned = 240,
                        uniqueAnkiKanjiCount = 812,
                        skippedNotes = 0,
                    ),
                    eligibleDictionaryKanjiCount = 812,
                    missingKanjiCount = 0,
                    rows = emptyList(),
                    staleReason = null,
                ),
            )
            else -> error("Unsupported Missing Kanji baseline state: $state")
        }
        activity.showFixture(
            MainActivityBase.SCREENSHOT_MISSING_KANJI_ROUTE,
            scrollMode = MainActivityRouteScrollMode.CONTENT,
        ) {
            MissingKanjiScreen(
                MissingKanjiScreenModel(
                    content = content,
                    providerAvailability = MissingKanjiProviderAvailability.READY,
                    frequency = MissingKanjiFrequencyModel(
                        preset = MissingKanjiPreset.TOP_2000,
                        range = MissingKanjiFrequencyRange.TOP_2000,
                        searchQuery = "",
                    ),
                    primaryAction = if (state == "empty") {
                        MissingKanjiPrimaryAction.SCAN_AGAIN
                    } else {
                        MissingKanjiPrimaryAction.RETRY
                    },
                    onHome = {},
                    onPrimaryAction = {},
                    onCancelScan = {},
                    onRangeApplied = { _, _ -> },
                    onRangePreview = { _, callback -> callback(812) },
                    onSearchQueryChanged = {},
                    destinations = MissingKanjiDestinationModel(),
                ),
            )
        }
    }

    private fun renderSync(activity: MainActivity, state: String) {
        val model = when (state) {
            "sync-skipped" -> SyncResultScreenModel(
                title = HomeTextCopy.syncAlreadyRunningTitle(),
                headline = null,
                lines = listOf(HomeTextCopy.syncAlreadyRunningFallback()),
                accentColor = MainActivityBase.BLUE,
                primaryLabel = null,
                primaryColor = MainActivityBase.TEAL,
                onPrimary = null,
                secondaryLabel = StudyTextCopy.backHomeLabel(),
                onSecondary = NO_OP,
            )
            "sync-success" -> SyncResultScreenModel(
                title = HomeTextCopy.syncCompleteTitle(),
                headline = HomeTextCopy.syncReadyCountText(3),
                lines = listOf(
                    HomeTextCopy.syncCandidateSummary(3, "Three in today's focus"),
                    HomeTextCopy.importedSuspendedKanjiText(1),
                ),
                accentColor = MainActivityBase.TEAL,
                primaryLabel = HomeTextCopy.studyNowLabel(),
                primaryColor = MainActivityBase.CORAL,
                onPrimary = NO_OP,
                secondaryLabel = StudyTextCopy.backHomeLabel(),
                onSecondary = NO_OP,
            )
            "sync-failure" -> SyncResultScreenModel(
                title = HomeTextCopy.syncNeedsAttentionTitle(),
                headline = HomeTextCopy.syncReadErrorTitle(),
                lines = listOf(HomeTextCopy.syncFailureFallback()),
                accentColor = MainActivityBase.CORAL,
                primaryLabel = HomeTextCopy.trySyncAgainLabel(),
                primaryColor = MainActivityBase.TEAL,
                onPrimary = NO_OP,
                secondaryLabel = StudyTextCopy.backHomeLabel(),
                onSecondary = NO_OP,
            )
            else -> error("Unsupported sync baseline state: $state")
        }
        activity.showFixture(MainActivityBase.NAV_HOME_ROUTE) {
            SyncResultScreen(model)
        }
    }

    private fun renderLoading(activity: MainActivity, title: String, route: String) {
        activity.showFixture(route) {
            HomeRouteLoadingScreen(
                title = title,
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = {},
            )
        }
    }

    private fun renderError(activity: MainActivity, title: String, route: String) {
        activity.showFixture(route) {
            HomeRouteErrorScreen(
                title = title,
                retryLabel = HomeTextCopy.retryLabel(),
                onRetry = {},
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = {},
            )
        }
    }

    private fun MainActivity.showFixture(
        route: String,
        scrollMode: MainActivityRouteScrollMode = MainActivityRouteScrollMode.SHELL,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = route),
                navActions = NO_OP_NAV_ACTIONS,
                themeChoice = KaniThemeChoice.LIGHT,
                isSystemDarkTheme = false,
                scrollMode = scrollMode,
                content = content,
            )
        }
    }

    private val NO_OP = Runnable {}
    private val NO_OP_NAV_ACTIONS = KaniNavActions({}, {}, {}, {})
    private const val FIXTURE_TIME_MILLIS = 1_700_000_000_000L
}

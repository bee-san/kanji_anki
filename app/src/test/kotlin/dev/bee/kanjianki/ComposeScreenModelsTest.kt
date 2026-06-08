package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ComposeScreenModelsTest {
    @Test
    fun asyncHomeRouteLoaderReturnsImmediatelyWhenLoadBlocks() {
        val background = Executors.newSingleThreadExecutor()
        val releaseLoad = CountDownLatch(1)
        val rendered = CountDownLatch(1)
        val loader = AsyncHomeRouteLoader(background = background, postToMain = { task -> task.run() })

        try {
            val started = System.nanoTime()
            loader.load(
                showLoading = {},
                load = {
                    releaseLoad.await(5, TimeUnit.SECONDS)
                    "ready"
                },
                render = { rendered.countDown() },
            )
            val responseMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            assertTrue("tap-to-loading response took ${responseMillis}ms", responseMillis < 200)

            releaseLoad.countDown()
            assertTrue("background result did not render", rendered.await(5, TimeUnit.SECONDS))
        } finally {
            background.shutdownNow()
        }
    }

    @Test
    fun asyncHomeRouteLoaderShowsLoadingAfterDelayOnlyOnSlowLoads() {
        val loading = mutableListOf<String>()
        val events = mutableListOf<String>()
        val background = QueueingExecutor()
        val main = QueueingExecutor()
        val loadingScheduler = ManualLoadingTaskScheduler()
        val loader = AsyncHomeRouteLoader(
            background = background,
            postToMain = { task -> main.execute(task) },
            loadingTaskScheduler = loadingScheduler,
        )

        loader.load(
            showLoading = {
                loading.add("loading")
            },
            load = {
                "ready"
            },
            render = {
                events.add("render")
            },
            showLoadingAfterMs = 40,
        )

        background.runNext()
        main.runNext()

        assertEquals(listOf("render"), events)
        loadingScheduler.runPendingTask()
        assertTrue("unexpected loading task was queued", main.isEmpty())
        assertEquals(listOf<String>(), loading)
    }

    @Test
    fun asyncHomeRouteLoaderShowsLoadingAfterDelayBeforeRenderingSlowLoad() {
        val events = mutableListOf<String>()
        val background = QueueingExecutor()
        val main = QueueingExecutor()
        val loadingScheduler = ManualLoadingTaskScheduler()
        val loadingShown = CountDownLatch(1)
        val loader = AsyncHomeRouteLoader(
            background = background,
            postToMain = { task -> main.execute(task) },
            loadingTaskScheduler = loadingScheduler,
        )

        loader.load(
            showLoading = {
                events.add("loading")
                loadingShown.countDown()
            },
            load = {
                loadingScheduler.runPendingTask()
                assertTrue("loading should appear before slow load completes", loadingShown.await(5, TimeUnit.SECONDS))
                "ready"
            },
            render = { value ->
                events.add("render:$value")
            },
            showLoadingAfterMs = 40,
        )

        val backgroundWorker = Thread { background.runNext() }
        backgroundWorker.start()
        while (main.isEmpty()) {
            Thread.yield()
        }

        main.runNext()
        assertEquals(listOf("loading"), events)

        backgroundWorker.join(5_000)
        main.runNext()
        assertEquals(listOf("loading", "render:ready"), events)
    }

    @Test
    fun asyncHomeRouteLoaderShowsLoadingBeforeBackgroundWork() {
        val events = mutableListOf<String>()
        val background = QueueingExecutor()
        val main = QueueingExecutor()
        val loader = AsyncHomeRouteLoader(background = background, postToMain = { task -> main.execute(task) })

        loader.load(
            showLoading = { events.add("loading") },
            load = {
                events.add("load")
                "ready"
            },
            render = { value -> events.add("render:$value") },
        )

        assertEquals(listOf("loading"), events)

        background.runNext()
        assertEquals(listOf("loading", "load"), events)

        main.runNext()
        assertEquals(listOf("loading", "load", "render:ready"), events)
    }

    @Test
    fun asyncHomeRouteLoaderIgnoresStaleBackgroundResults() {
        val events = mutableListOf<String>()
        val background = QueueingExecutor()
        val main = QueueingExecutor()
        val loader = AsyncHomeRouteLoader(background = background, postToMain = { task -> main.execute(task) })

        loader.load(
            showLoading = { events.add("loading:first") },
            load = { "first" },
            render = { value -> events.add("render:$value") },
        )
        loader.load(
            showLoading = { events.add("loading:second") },
            load = { "second" },
            render = { value -> events.add("render:$value") },
        )

        background.runNext()
        background.runNext()
        main.runNext()
        main.runNext()

        assertEquals(listOf("loading:first", "loading:second", "render:second"), events)
    }

    @Test
    fun asyncHomeRouteLoaderCancelPendingIgnoresQueuedResult() {
        val events = mutableListOf<String>()
        val background = QueueingExecutor()
        val main = QueueingExecutor()
        val loader = AsyncHomeRouteLoader(background = background, postToMain = { task -> main.execute(task) })

        loader.load(
            showLoading = { events.add("loading") },
            load = { "ready" },
            render = { value -> events.add("render:$value") },
        )
        loader.cancelPending()

        background.runNext()
        main.runNext()

        assertEquals(listOf("loading"), events)
    }

    @Test
    fun shellModelDefaultsToHomeAndCopiesSelectedRoute() {
        assertEquals("home", MainActivityShellModel().selectedRoute)

        val study = MainActivityShellModel().copy(selectedRoute = MainActivityBase.NAV_STUDY)

        assertEquals(MainActivityBase.NAV_STUDY, study.selectedRoute)
        assertEquals(MainActivityShellModel(MainActivityBase.NAV_STUDY), study)
        assertEquals("main-route-${MainActivityBase.NAV_STUDY}", study.routeTestTag)
        assertEquals("Kani route ${MainActivityBase.NAV_STUDY}", study.routeContentDescription)
    }

    @Test
    fun homeScreenModelKeepsAllCallbacksAndSections() {
        val onSync = {}
        val onStudy = {}
        val onFocus = {}
        val action = HomeActionModel("Stats", R.drawable.ic_stats_24, onClick = {})
        val coral = 0xFFFF4C76.toInt()
        val metric = HomeMetricModel(R.drawable.ic_target_24, coral, "Focus", "2", "Ready", null)

        val model = HomeScreenModel(
            title = "Kani",
            subtitle = "Repair weak kanji",
            metrics = listOf(metric),
            deckOverviewRows = listOf("Due 2", "New 1"),
            showSyncCta = true,
            syncLabel = "Sync",
            studyLabel = "Study",
            onSync = onSync,
            onStudy = onStudy,
            actions = listOf(action),
            focusTitle = "Focus queue",
            focusActionLabel = "View all",
            onFocusAction = onFocus,
            emptyTitle = "Empty",
            emptyBody = "Sync first",
            previewCards = emptyList(),
        )

        assertEquals("Kani", model.title)
        assertEquals("Repair weak kanji", model.subtitle)
        assertEquals(listOf(metric), model.metrics)
        assertEquals(listOf("Due 2", "New 1"), model.deckOverviewRows)
        assertEquals(true, model.showSyncCta)
        assertEquals("Sync", model.syncLabel)
        assertEquals("Study", model.studyLabel)
        assertSame(onSync, model.onSync)
        assertSame(onStudy, model.onStudy)
        assertEquals(listOf(action), model.actions)
        assertEquals(buttonTraceSection("home-action-Stats"), action.traceSection)
        assertEquals("Focus queue", model.focusTitle)
        assertEquals("View all", model.focusActionLabel)
        assertSame(onFocus, model.onFocusAction)
        assertEquals("Empty", model.emptyTitle)
        assertEquals("Sync first", model.emptyBody)
        assertEquals(emptyList<HomeFocusQueueCardModel>(), model.previewCards)
        assertEquals(model, model.copy())
    }

    @Test
    fun secondaryScreenModelsKeepPanelContracts() {
        val onHome = {}
        val onSync = {}
        val queue = HomeFocusQueuePanelModel(
            planText = "Adaptive focus",
            emptyTitle = "No cards",
            emptyBody = "Sync first",
            showSyncButton = true,
            cards = emptyList(),
        )
        val mistakes = HomeRecentMistakesPanelModel(
            emptyTitle = "No mistakes",
            emptyBody = "Missed reviews appear here",
            cards = emptyList(),
        )

        val focusModel = HomeFocusQueueScreenModel("Focus", "Home", onHome, queue, onSync)
        val mistakesModel = HomeRecentMistakesScreenModel("Mistakes", "Home", onHome, mistakes)

        assertEquals("Focus", focusModel.title)
        assertEquals("Home", focusModel.homeLabel)
        assertSame(onHome, focusModel.onHome)
        assertSame(queue, focusModel.queue)
        assertSame(onSync, focusModel.onSync)
        assertEquals(focusModel, focusModel.copy())
        assertEquals("Mistakes", mistakesModel.title)
        assertEquals("Home", mistakesModel.homeLabel)
        assertSame(onHome, mistakesModel.onHome)
        assertSame(mistakes, mistakesModel.mistakes)
        assertEquals(mistakesModel, mistakesModel.copy())
    }

    @Test
    fun browseExampleModelKeepsSourceAndTextFields() {
        val coral = 0xFFFF4C76.toInt()
        val model = BrowseExampleCardModel(
            sourceLabel = "Suspended",
            expression = "裂語",
            sentence = "裂語 is an example.",
            meaning = "split word",
            color = coral,
        )

        assertEquals("Suspended", model.sourceLabel)
        assertEquals("裂語", model.expression)
        assertEquals("裂語 is an example.", model.sentence)
        assertEquals("split word", model.meaning)
        assertEquals(coral, model.color)
        assertEquals(model, model.copy())
    }

    @Test
    fun browseDetailIdentityKeepsAnkiStateBadges() {
        val coral = 0xFFFF4C76.toInt()
        val badge = BrowseStateBadgeModel("Suspended", coral)
        val model = BrowseDetailIdentityModel(
            title = "split",
            reading = "レツ",
            stateBadges = listOf(badge),
        )

        assertEquals("split", model.title)
        assertEquals("レツ", model.reading)
        assertEquals(listOf(badge), model.stateBadges)
        assertEquals("Suspended", model.stateBadges.single().label)
        assertEquals(coral, model.stateBadges.single().color)
        assertEquals(model, model.copy())
    }

    @Test
    fun syncResultModelKeepsPrimaryAndSecondaryActions() {
        val coral = 0xFFFF4C76.toInt()
        val teal = 0xFF00AEB5.toInt()
        val primary = Runnable {}
        val secondary = Runnable {}
        val model = SyncResultScreenModel(
            title = "Sync complete",
            headline = "12 cards imported",
            lines = listOf("8 suspended", "4 active"),
            accentColor = coral,
            primaryLabel = "Study now",
            primaryColor = teal,
            onPrimary = primary,
            secondaryLabel = "Back home",
            onSecondary = secondary,
        )

        assertEquals("Sync complete", model.title)
        assertEquals("12 cards imported", model.headline)
        assertEquals(listOf("8 suspended", "4 active"), model.lines)
        assertEquals(coral, model.accentColor)
        assertEquals("Study now", model.primaryLabel)
        assertEquals(teal, model.primaryColor)
        assertSame(primary, model.onPrimary)
        assertEquals("Back home", model.secondaryLabel)
        assertSame(secondary, model.onSecondary)
        assertEquals(model, model.copy())
    }

    @Test
    fun reminderPanelModelKeepsMutableTimeAndActions() {
        var picked: Pair<Int, Int>? = null
        var saved = false
        var turnedOff = false
        var openedSettings = false
        val selectedHour = intArrayOf(7)
        val selectedMinute = intArrayOf(30)
        val pickTime = SettingsReminderTimePickerAction { hour, minute, onSelected ->
            picked = hour to minute
            onSelected.select(8, 45)
        }
        val save = SettingsReminderAction { saved = true }
        val turnOff = SettingsReminderAction { turnedOff = true }
        val openSettings = SettingsReminderAction { openedSettings = true }
        val preset = SettingsReminderPresetModel("Morning", 8, 0)

        val model = SettingsReminderPanelModel(
            title = "Daily reminder",
            status = "On",
            statusColor = 0xFF00AEB5.toInt(),
            body = "Kani will remind you to study.",
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            presets = listOf(preset),
            saveLabel = "Save reminder",
            turnOffLabel = "Turn off",
            warning = "Notifications are blocked",
            notificationSettingsLabel = "Open notification settings",
            onPickTime = pickTime,
            onSave = save,
            onTurnOff = turnOff,
            onOpenNotificationSettings = openSettings,
        )

        assertEquals("Daily reminder", model.title)
        assertEquals("On", model.status)
        assertEquals(0xFF00AEB5.toInt(), model.statusColor)
        assertEquals("Kani will remind you to study.", model.body)
        assertSame(selectedHour, model.selectedHour)
        assertSame(selectedMinute, model.selectedMinute)
        assertEquals(listOf(preset), model.presets)
        assertEquals("Save reminder", model.saveLabel)
        assertEquals("Turn off", model.turnOffLabel)
        assertEquals("Notifications are blocked", model.warning)
        assertEquals("Open notification settings", model.notificationSettingsLabel)
        assertSame(pickTime, model.onPickTime)
        assertSame(save, model.onSave)
        assertSame(turnOff, model.onTurnOff)
        assertSame(openSettings, model.onOpenNotificationSettings)

        model.onPickTime.pick(model.selectedHour[0], model.selectedMinute[0]) { hour, minute ->
            model.selectedHour[0] = hour
            model.selectedMinute[0] = minute
        }
        model.onSave.run()
        model.onTurnOff?.run()
        model.onOpenNotificationSettings?.run()

        assertEquals(7 to 30, picked)
        assertEquals(8, model.selectedHour[0])
        assertEquals(45, model.selectedMinute[0])
        assertEquals(true, saved)
        assertEquals(true, turnedOff)
        assertEquals(true, openedSettings)
    }

    @Test
    fun autoSyncPanelModelKeepsOptionalActionContract() {
        var toggled = false
        val action = SettingsAutoSyncAction { toggled = true }
        val model = SettingsAutoSyncPanelModel(
            title = "Daily sync",
            status = "On at 06:45",
            statusColor = 0xFF00AEB5.toInt(),
            detail = "Last success yesterday. Next run tomorrow.",
            actionLabel = "Turn off",
            primaryAction = false,
            onAction = action,
        )
        val disabled = model.copy(
            status = "Not configured",
            actionLabel = null,
            primaryAction = false,
            onAction = null,
        )

        assertEquals("Daily sync", model.title)
        assertEquals("On at 06:45", model.status)
        assertEquals(0xFF00AEB5.toInt(), model.statusColor)
        assertEquals("Last success yesterday. Next run tomorrow.", model.detail)
        assertEquals("Turn off", model.actionLabel)
        assertEquals(false, model.primaryAction)
        assertSame(action, model.onAction)
        model.onAction?.run()
        assertEquals(true, toggled)
        assertEquals("Not configured", disabled.status)
        assertEquals(null, disabled.actionLabel)
        assertEquals(null, disabled.onAction)
    }

    @Test
    fun writingPromptModelsKeepHeaderAndLineFields() {
        val muted = 0xFF6C5674.toInt()
        val plum = 0xFF4B2552.toInt()
        val promptLine = WritingPromptLineModel(
            text = "Prompt: split, rend",
            sizeSp = 17,
            color = plum,
            bold = true,
        )
        val readingLine = WritingPromptLineModel(
            text = "Reading: レツ",
            sizeSp = 15,
            color = muted,
            bold = false,
        )
        val model = WritingPromptHeaderModel(
            modeLabel = "Practice",
            title = "Draw this kanji",
            taskLabel = "Write kanji",
            reasonLine = "Weak Anki evidence",
            detailLines = listOf(promptLine, readingLine),
        )

        assertEquals("Practice", model.modeLabel)
        assertEquals("Draw this kanji", model.title)
        assertEquals("Write kanji", model.taskLabel)
        assertEquals("Weak Anki evidence", model.reasonLine)
        assertEquals(listOf(promptLine, readingLine), model.detailLines)
        assertEquals("Prompt: split, rend", promptLine.text)
        assertEquals(17, promptLine.sizeSp)
        assertEquals(plum, promptLine.color)
        assertEquals(true, promptLine.bold)
        assertEquals("Reading: レツ", readingLine.text)
        assertEquals(15, readingLine.sizeSp)
        assertEquals(muted, readingLine.color)
        assertEquals(false, readingLine.bold)
        assertEquals(model, model.copy())
    }

    @Test
    fun writingToolActionsModelKeepsDefaultAndCallbackState() {
        val initial = WritingToolActionsModel.initial()
        assertEquals(false, initial.undoEnabled)
        assertEquals("Hint", initial.hintText)
        assertEquals(false, initial.hintVisible)

        var erased = false
        var undone = false
        var hinted = false
        val erase = Runnable { erased = true }
        val undo = Runnable { undone = true }
        val hint = Runnable { hinted = true }
        val model = WritingToolActionsModel(
            undoEnabled = true,
            hintText = "More help",
            hintVisible = true,
            onErase = erase,
            onUndo = undo,
            onHint = hint,
        )

        assertEquals(true, model.undoEnabled)
        assertEquals("More help", model.hintText)
        assertEquals(true, model.hintVisible)
        assertSame(erase, model.onErase)
        assertSame(undo, model.onUndo)
        assertSame(hint, model.onHint)
        model.onErase.run()
        model.onUndo.run()
        model.onHint.run()
        assertEquals(true, erased)
        assertEquals(true, undone)
        assertEquals(true, hinted)
        assertEquals(model, model.copy())
    }

    @Test
    fun writingFallbackActionsModelKeepsDefaultAndCallbackState() {
        val initial = WritingFallbackActionsModel.initial()
        assertEquals(false, initial.replayVisible)
        assertEquals(false, initial.manualOverrideVisible)
        assertEquals(false, initial.practiceWithGuideVisible)

        var replayed = false
        var manuallyAccepted = false
        var retried = false
        val replay = Runnable { replayed = true }
        val manualOverride = Runnable { manuallyAccepted = true }
        val practiceWithGuide = Runnable { retried = true }
        val model = WritingFallbackActionsModel(
            replayVisible = true,
            manualOverrideVisible = true,
            practiceWithGuideVisible = true,
            onReplay = replay,
            onManualOverride = manualOverride,
            onPracticeWithGuide = practiceWithGuide,
        )

        assertEquals(true, model.replayVisible)
        assertEquals(true, model.manualOverrideVisible)
        assertEquals(true, model.practiceWithGuideVisible)
        assertSame(replay, model.onReplay)
        assertSame(manualOverride, model.onManualOverride)
        assertSame(practiceWithGuide, model.onPracticeWithGuide)
        model.onReplay.run()
        model.onManualOverride.run()
        model.onPracticeWithGuide.run()
        assertEquals(true, replayed)
        assertEquals(true, manuallyAccepted)
        assertEquals(true, retried)
        assertEquals(model, model.copy())
    }

    @Test
    fun writingPrimaryActionsModelKeepsDefaultAndCallbackState() {
        val initial = WritingPrimaryActionsModel.initial()
        assertEquals("Check", initial.checkText)
        assertEquals(true, initial.checkVisible)
        assertEquals(true, initial.checkEnabled)
        assertEquals("Download checker", initial.downloadText)
        assertEquals(true, initial.downloadVisible)
        assertEquals(MainActivityBase.LABEL_PASS, initial.nextText)
        assertEquals(false, initial.nextVisible)

        var checked = false
        var downloaded = false
        var advanced = false
        val check = Runnable { checked = true }
        val download = Runnable { downloaded = true }
        val next = Runnable { advanced = true }
        val model = WritingPrimaryActionsModel(
            checkText = "Try cleaner",
            checkVisible = true,
            checkEnabled = false,
            downloadText = "Download checker",
            downloadVisible = false,
            nextText = "Save hard",
            nextVisible = true,
            onCheck = check,
            onDownload = download,
            onNext = next,
        )

        assertEquals("Try cleaner", model.checkText)
        assertEquals(true, model.checkVisible)
        assertEquals(false, model.checkEnabled)
        assertEquals("Download checker", model.downloadText)
        assertEquals(false, model.downloadVisible)
        assertEquals("Save hard", model.nextText)
        assertEquals(true, model.nextVisible)
        assertSame(check, model.onCheck)
        assertSame(download, model.onDownload)
        assertSame(next, model.onNext)
        model.onCheck.run()
        model.onDownload.run()
        model.onNext.run()
        assertEquals(true, checked)
        assertEquals(true, downloaded)
        assertEquals(true, advanced)
        assertEquals(model, model.copy())
    }

    @Test
    fun settingsScreenModelsKeepCategoryAndShellActions() {
        var homeClicked = false
        var toggled = false
        val home = Runnable { homeClicked = true }
        val toggle = Runnable { toggled = true }
        val hero = SettingsAutomationHeroModel(
            cockpitLabel = "Cockpit",
            title = "Settings",
            body = "Configure Kani behavior.",
            rows = listOf(
                listOf(SettingsAutomationHeroPillModel("Daily sync", "Enabled", 0xFF00AEB5.toInt()))
            ),
        )
        val panel = SettingsReferenceDataLinkModel(
            title = "Data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = "Open licenses",
            onAction = Runnable {},
        )
        val category = settingsCategorySectionModel(
            sectionKey = "settings-study-behavior",
            title = "Study",
            summary = "Tune review behavior.",
            iconRes = R.drawable.ic_target_24,
            expanded = true,
            onToggle = toggle,
            panels = listOf(panel),
        )
        val screen = SettingsScreenModel(
            homeLabel = "Home",
            onHome = home,
            hero = hero,
            categories = listOf(category),
        )

        assertEquals("Home", screen.homeLabel)
        assertSame(home, screen.onHome)
        assertSame(hero, screen.hero)
        assertEquals(listOf(category), screen.categories)
        assertEquals("settings-study-behavior", category.sectionKey)
        assertEquals("Study", category.title)
        assertEquals("Tune review behavior.", category.summary)
        assertEquals(R.drawable.ic_target_24, category.iconRes)
        assertEquals(true, category.expanded)
        assertEquals("1 card", category.panelCount)
        assertEquals("Collapse Study", category.contentDescription)
        assertSame(toggle, category.onToggle)
        assertEquals(listOf(panel), category.panels)
        screen.onHome.run()
        category.onToggle.run()
        assertEquals(true, homeClicked)
        assertEquals(true, toggled)
        assertEquals(screen, screen.copy())
        assertEquals(category, category.copy())
    }

    @Test
    fun settingsCategoryCopyUsesAnkiLikeSections() {
        assertEquals("Import & sync", dev.bee.kanjianki.core.SettingsTextCopy.settingsAnkiSourceTitle())
        assertEquals(
            "Fields, filters, range, and sync.",
            dev.bee.kanjianki.core.SettingsTextCopy.settingsAnkiSourceBody(),
        )
        assertEquals("Study settings", dev.bee.kanjianki.core.SettingsTextCopy.settingsStudyBehaviorTitle())
        assertEquals(
            "New cards, timing, workload, and ladder controls.",
            dev.bee.kanjianki.core.SettingsTextCopy.settingsStudyBehaviorBody(),
        )
        assertEquals("Automation", dev.bee.kanjianki.core.SettingsTextCopy.settingsAutomationTitle())
        assertEquals(
            "Reminders and updates.",
            dev.bee.kanjianki.core.SettingsTextCopy.settingsAutomationBody(),
        )
        assertEquals("Display & data", dev.bee.kanjianki.core.SettingsTextCopy.settingsReferenceDataTitle())
        assertEquals(
            "Dictionaries, stroke data, fonts, and credits.",
            dev.bee.kanjianki.core.SettingsTextCopy.settingsReferenceDataBody(),
        )
    }


    @Test
    fun settingsCategoryFactoriesGroupSyncWithImports() {
        val noop = Runnable {}
        val noteType = SettingsNoteTypePanelModel(
            title = "Note type",
            status = "Kiku",
            body = "Fields",
            fields = SettingsNoteTypeFieldState("Kiku", "Kanji", "Reading", "Meaning", "Sentence", "Frequency", "Sort"),
            requiredTitle = "Required",
            requiredBody = "Expression required",
            noteTypeLabel = "Note type",
            expressionLabel = "Expression",
            readingLabel = "Reading",
            meaningLabel = "Meaning",
            sentenceLabel = "Sentence",
            frequencyLabel = "Frequency",
            frequencySortLabel = "Sort",
            chooseLabel = "Choose",
            kikuLabel = "Use Kiku",
            saveLabel = "Save",
            onChoose = SettingsNoteTypeAction {},
            onUseKiku = SettingsNoteTypeAction {},
            onSave = SettingsNoteTypeAction {},
        )
        val importFilters = SettingsImportFiltersPanelModel(
            title = "Import filters",
            summary = "Suspended",
            body = "Choose imports",
            presetsTitle = "Presets",
            presets = emptyList(),
            state = SettingsImportFiltersState(false, true, false, false, false, "", "", "0.5", "2", "1"),
            activeCardsLabel = "Active",
            suspendedCardsLabel = "Suspended",
            taggedCardsLabel = "Tagged",
            weakCardsLabel = "Weak",
            browserQueryCardsLabel = "Browser query",
            browserQueryLabel = "Query",
            browserQueryHint = "rated:1",
            browserQueryHelperText = "Use Anki browser syntax",
            tagsLabel = "Tags",
            tagsHint = "kani",
            difficultyLabel = "Difficulty",
            lapsesLabel = "Lapses",
            minMatchingLabel = "Minimum",
            saveLabel = "Save",
            onSave = SettingsImportFilterAction {},
        )
        val frequency = SettingsFrequencyRangePanelModel(
            title = "Kanji frequency range",
            body = "Ranks",
            selectedRanks = intArrayOf(1, 500),
            minRankLabel = "Min",
            initialMinRankText = "1",
            maxRankLabel = "Max",
            initialMaxRankText = "500",
            minimumRankLabel = "Most frequent",
            maximumRankLabel = "Least frequent",
            saveLabel = "Save",
            onSave = SettingsFrequencyRangeSaveAction { _, _ -> },
        )
        val autoSync = SettingsAutoSyncPanelModel(
            title = "Daily sync",
            status = "On",
            statusColor = 0xFF00AEB5.toInt(),
            detail = "Runs daily",
            actionLabel = "Turn off",
            primaryAction = false,
            onAction = SettingsAutoSyncAction {},
        )
        val reminder = SettingsReminderPanelModel(
            title = "Daily reminder",
            status = "Off",
            statusColor = 0xFF6C5674.toInt(),
            body = "Study reminder",
            selectedHour = intArrayOf(8),
            selectedMinute = intArrayOf(0),
            presets = emptyList(),
            saveLabel = "Save",
            turnOffLabel = null,
            warning = null,
            notificationSettingsLabel = null,
            onPickTime = SettingsReminderTimePickerAction { _, _, _ -> },
            onSave = SettingsReminderAction {},
            onTurnOff = null,
            onOpenNotificationSettings = null,
        )
        val update = SettingsUpdateOverviewPanelModel(
            panel = SettingsUpdatePanelModel(
                title = "Updates",
                statusLine = "Manual checks",
                statusColor = ComposeColor.Black,
                lastCheckLine = "Never checked",
                lastResultLine = "No result",
                installPermissionLine = "Allowed",
                installPermissionColor = ComposeColor.Black,
                hasPendingUpdate = false,
                pendingVersionLine = null,
                pendingMessageLine = null,
                canInstallUpdates = true,
                onInstallVerifiedUpdate = {},
                onOpenInstallSettings = {},
                onToggleAutomaticUpdates = {},
                automaticUpdatesToggleLabel = "Enable",
            ),
            openUpdaterLabel = "Open updater",
            onOpenUpdater = {},
        )

        val importSync = settingsAnkiSourceCategoryModel(true, noop, noteType, importFilters, frequency, autoSync)
        val advanced = settingsAutomationCategoryModel(false, noop, reminder, update)
        val collapsedBehavior = settingsStudyBehaviorCategoryModel(
            false,
            noop,
            panelCount = 8,
            panels = emptyList(),
        )

        assertEquals("Import & sync", importSync.title)
        assertEquals("4 cards", importSync.panelCount)
        assertEquals("Automation", advanced.title)
        assertEquals("2 cards", advanced.panelCount)
        assertEquals("Study settings", collapsedBehavior.title)
        assertEquals("8 cards", collapsedBehavior.panelCount)
        assertTrue(collapsedBehavior.panels.isEmpty())

        assertEquals(listOf(reminder, update), advanced.panels)
    }

    @Test
    fun referenceDataModelsKeepNavigationAndAttributionFields() {
        var opened = false
        var wentHome = false
        var wentBack = false
        val openAction = Runnable { opened = true }
        val homeAction = Runnable { wentHome = true }
        val backAction = Runnable { wentBack = true }
        val link = SettingsReferenceDataLinkModel(
            title = "Data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = "Open licenses",
            onAction = openAction,
        )
        val intro = SettingsReferenceDataIntroModel(
            backLabel = "Back to settings",
            title = "Data licenses",
            body = "Bundled source attribution.",
            onBack = backAction,
        )
        val sources = SettingsReferenceDataModel(
            dictionaryTitle = "Dictionary data",
            dictionaryBody = "KANJIDIC2 and Jiten sources",
            strokeTitle = "Stroke data",
            strokeBody = "KanjiVG attribution",
            fontsTitle = "Fonts",
            fontsBody = "Bundled font attribution",
        )
        val screen = SettingsReferenceDataScreenModel(
            homeLabel = "Home",
            onHome = homeAction,
            intro = intro,
            dataSources = sources,
        )

        assertEquals("Data licenses", link.title)
        assertEquals("Dictionary, stroke, and font attributions.", link.body)
        assertEquals("Open licenses", link.actionLabel)
        assertSame(openAction, link.onAction)
        assertEquals("Back to settings", intro.backLabel)
        assertEquals("Data licenses", intro.title)
        assertEquals("Bundled source attribution.", intro.body)
        assertSame(backAction, intro.onBack)
        assertEquals("Dictionary data", sources.dictionaryTitle)
        assertEquals("KANJIDIC2 and Jiten sources", sources.dictionaryBody)
        assertEquals("Stroke data", sources.strokeTitle)
        assertEquals("KanjiVG attribution", sources.strokeBody)
        assertEquals("Fonts", sources.fontsTitle)
        assertEquals("Bundled font attribution", sources.fontsBody)
        assertEquals("Home", screen.homeLabel)
        assertSame(homeAction, screen.onHome)
        assertSame(intro, screen.intro)
        assertSame(sources, screen.dataSources)
        link.onAction.run()
        screen.onHome.run()
        intro.onBack.run()
        assertEquals(true, opened)
        assertEquals(true, wentHome)
        assertEquals(true, wentBack)
        assertEquals(link, link.copy())
        assertEquals(intro, intro.copy())
        assertEquals(sources, sources.copy())
        assertEquals(screen, screen.copy())
    }

    @Test
    fun newCardSortModelKeepsOptionsAndSaverContract() {
        var savedMode: String? = null
        val saver = SettingsNewCardSortSaver { mode -> savedMode = mode }
        val frequency = SettingsNewCardSortOptionModel("Frequency", "frequency", "Jiten frequency first.")
        val risk = SettingsNewCardSortOptionModel("Retrievability risk", "retrievability_risk", "Cards most likely to be forgotten first.")
        val model = SettingsNewCardSortPanelModel(
            title = "New card order",
            body = "Choose how Kani admits new problem kanji.",
            initialMode = frequency.mode,
            options = listOf(frequency, risk),
            saveLabel = "Save order",
            onSave = saver,
        )

        assertEquals("Frequency", frequency.label)
        assertEquals("frequency", frequency.mode)
        assertEquals("Jiten frequency first.", frequency.description)
        assertEquals("New card order", model.title)
        assertEquals("Choose how Kani admits new problem kanji.", model.body)
        assertEquals("frequency", model.initialMode)
        assertEquals(listOf(frequency, risk), model.options)
        assertEquals("Save order", model.saveLabel)
        assertSame(saver, model.onSave)
        model.onSave.save(risk.mode)
        assertEquals("retrievability_risk", savedMode)
        assertEquals(frequency, frequency.copy())
        assertEquals(model, model.copy())
    }

    @Test
    fun newCardSortPreviewRowsHideWhenUnavailable() {
        val model = SettingsNewCardSortPanelModel(
            title = "New card order",
            body = "Choose how Kani admits new problem kanji.",
            initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
            options = emptyList(),
            saveLabel = "Save order",
            previewRowsByMode = emptyMap(),
            onSave = SettingsNewCardSortSaver {},
        )

        assertEquals(false, model.hasPreviewRows())
        assertEquals(emptyList<SettingsNewCardSortPreviewRowModel>(), model.previewRows(RecordsBase.NEW_CARD_SORT_FREQUENCY))
    }

    @Test
    fun newCardSortPreviewRowsUseSelectedModeOrder() {
        val frequencyPreview = SettingsNewCardSortPreviewRowModel("日", "sun", "#1 frequency")
        val riskPreview = SettingsNewCardSortPreviewRowModel("難", "difficult", "Risk 82%")
        val model = SettingsNewCardSortPanelModel(
            title = "New card order",
            body = "Choose how Kani admits new problem kanji.",
            initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
            options = emptyList(),
            saveLabel = "Save order",
            previewRowsByMode = mapOf(
                RecordsBase.NEW_CARD_SORT_FREQUENCY to listOf(frequencyPreview),
                RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK to listOf(riskPreview),
            ),
            onSave = SettingsNewCardSortSaver {},
        )

        assertEquals(true, model.hasPreviewRows())
        assertEquals(listOf(frequencyPreview), model.previewRows(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals(listOf(riskPreview), model.previewRows(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals(emptyList<SettingsNewCardSortPreviewRowModel>(), model.previewRows("missing"))
        assertEquals(frequencyPreview, frequencyPreview.copy())
        assertEquals(model, model.copy())
    }

    @Test
    fun newCardSortPreviewWarningsFindNearbySimilarPairsWithDeterministicCap() {
        val rows = listOf(
            SettingsNewCardSortPreviewRowModel("人", "person", "#1 frequency"),
            SettingsNewCardSortPreviewRowModel("犬", "dog", "#2 frequency"),
            SettingsNewCardSortPreviewRowModel("入", "enter", "#3 frequency"),
            SettingsNewCardSortPreviewRowModel("土", "soil", "#4 frequency"),
            SettingsNewCardSortPreviewRowModel("士", "samurai", "#5 frequency"),
            SettingsNewCardSortPreviewRowModel("未", "not yet", "#6 frequency"),
            SettingsNewCardSortPreviewRowModel("末", "end", "#7 frequency"),
            SettingsNewCardSortPreviewRowModel("日", "sun", "#8 frequency"),
            SettingsNewCardSortPreviewRowModel("曰", "say", "#9 frequency"),
        )

        val examples = SettingsNewCardSortPreviewWarnings.nearbySimilarPairExamples(rows) { first, second ->
            setOf(setOf("人", "入"), setOf("土", "士"), setOf("未", "末"), setOf("日", "曰")).contains(setOf(first, second))
        }

        assertEquals(listOf("人/入", "土/士", "未/末"), examples)
    }

    @Test
    fun newCardSortPreviewWarningsIgnoreDistantAndUnknownPairs() {
        val rows = listOf(
            SettingsNewCardSortPreviewRowModel("人", "person", "#1 frequency"),
            SettingsNewCardSortPreviewRowModel("犬", "dog", "#2 frequency"),
            SettingsNewCardSortPreviewRowModel("木", "tree", "#3 frequency"),
            SettingsNewCardSortPreviewRowModel("入", "enter", "#4 frequency"),
        )

        val examples = SettingsNewCardSortPreviewWarnings.nearbySimilarPairExamples(rows) { first, second ->
            setOf(first, second) == setOf("人", "入")
        }

        assertEquals(emptyList<String>(), examples)
    }

    @Test
    fun newCardSortPreviewWarningTextFollowsSelectedMode() {
        val warning = SettingsTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入"))
        val model = SettingsNewCardSortPanelModel(
            title = "New card order",
            body = "Choose how Kani admits new problem kanji.",
            initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
            options = emptyList(),
            saveLabel = "Save order",
            previewWarningsByMode = mapOf(RecordsBase.NEW_CARD_SORT_FREQUENCY to warning),
            onSave = SettingsNewCardSortSaver {},
        )

        assertEquals(warning, model.previewWarning(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals(null, model.previewWarning(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals(model, model.copy())
    }

    @Test
    fun studyAheadModelKeepsInitialMinutesAndSaverContract() {
        var savedMinutesText: String? = null
        val saver = SettingsStudyAheadSaver { minutesText -> savedMinutesText = minutesText }
        val model = SettingsStudyAheadPanelModel(
            title = "Study ahead",
            body = "Let Kani include near-due cards.",
            minutesLabel = "Minutes ahead",
            initialMinutesText = "45",
            saveLabel = "Save window",
            onSave = saver,
        )

        assertEquals("Study ahead", model.title)
        assertEquals("Let Kani include near-due cards.", model.body)
        assertEquals("Minutes ahead", model.minutesLabel)
        assertEquals("45", model.initialMinutesText)
        assertEquals("Save window", model.saveLabel)
        assertSame(saver, model.onSave)
        model.onSave.save("60")
        assertEquals("60", savedMinutesText)
        assertEquals(model, model.copy())
    }

    @Test
    fun ladderThresholdModelKeepsDefaultsAndSaveContract() {
        var savedPromotionDays: String? = null
        var savedFailStreak: String? = null
        val save = SettingsLadderThresholdSaveAction { promotionDaysText, failStreakText ->
            savedPromotionDays = promotionDaysText
            savedFailStreak = failStreakText
        }
        val model = SettingsLadderThresholdPanelModel(
            title = "Ladder movement",
            body = "Tune rung movement.",
            promotionDaysLabel = "Days to move up",
            initialPromotionDaysText = "21",
            failStreakLabel = "Fails to move down",
            initialFailStreakText = "3",
            defaultPromotionDaysText = "21",
            defaultFailStreakText = "3",
            defaultsLabel = "Use default movement rules",
            saveLabel = "Save movement rules",
            onSave = save,
        )

        assertEquals("Ladder movement", model.title)
        assertEquals("Tune rung movement.", model.body)
        assertEquals("Days to move up", model.promotionDaysLabel)
        assertEquals("21", model.initialPromotionDaysText)
        assertEquals("Fails to move down", model.failStreakLabel)
        assertEquals("3", model.initialFailStreakText)
        assertEquals("21", model.defaultPromotionDaysText)
        assertEquals("3", model.defaultFailStreakText)
        assertEquals("Use default movement rules", model.defaultsLabel)
        assertEquals("Save movement rules", model.saveLabel)
        assertSame(save, model.onSave)
        model.onSave.save("28", "4")
        assertEquals("28", savedPromotionDays)
        assertEquals("4", savedFailStreak)
        assertEquals(model, model.copy())
    }

    @Test
    fun settingsAutomationHeroFactoryKeepsEnabledAndPendingSummaries() {
        val model = settingsAutomationHeroModel(
            current = RecordsSyncModels.Settings.kikuDefaults(),
            reminder = LocalStoreBase.ReminderSettings(true, 8, 5),
            autoSync = LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L),
            autoUpdate = LocalStoreBase.AutoUpdateStatus(
                true,
                0L,
                "update_available",
                "0.5.0",
                "kani-0.5.0.apk",
                "Ready to install",
            ),
            notificationsAllowed = false,
        )

        assertEquals("Settings overview", model.cockpitLabel)
        assertEquals(MainActivityBase.NAV_SETTINGS, model.title)
        assertEquals(4, model.rows.size)
        assertEquals("Note type", model.rows[0][0].label)
        assertEquals("Kiku", model.rows[0][0].value)
        assertEquals(0xFF4B2552.toInt(), model.rows[0][0].valueColor)
        assertEquals("Import filters", model.rows[0][1].label)
        assertEquals(0xFF00AEB5.toInt(), model.rows[0][1].valueColor)
        assertEquals("Daily reminder", model.rows[1][1].label)
        assertEquals("Notifications off", model.rows[1][1].value)
        assertEquals(0xFF00AEB5.toInt(), model.rows[1][1].valueColor)
        assertEquals("Daily sync", model.rows[2][0].label)
        assertEquals("07:30", model.rows[2][0].value)
        assertEquals(0xFF00AEB5.toInt(), model.rows[2][0].valueColor)
        assertEquals("App updates", model.rows[2][1].label)
        assertEquals("Verified APK ready", model.rows[2][1].value)
        assertEquals(0xFFFF4C76.toInt(), model.rows[2][1].valueColor)
        assertEquals("Cards per kanji", model.rows[3][0].label)
        assertEquals(0xFF4B2552.toInt(), model.rows[3][0].valueColor)
    }

    @Test
    fun settingsAutomationHeroFactoryKeepsDisabledSummaries() {
        val model = settingsAutomationHeroModel(
            current = RecordsSyncModels.Settings.kikuDefaults(),
            reminder = LocalStoreBase.ReminderSettings(false, 8, 5),
            autoSync = LocalStoreBase.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L),
            autoUpdate = LocalStoreBase.AutoUpdateStatus(false, 0L, "", "", "", ""),
            notificationsAllowed = true,
        )

        assertEquals("Off", model.rows[1][1].value)
        assertEquals(0xFF6C5674.toInt(), model.rows[1][1].valueColor)
        assertEquals("Off", model.rows[2][0].value)
        assertEquals(0xFF6C5674.toInt(), model.rows[2][0].valueColor)
        assertEquals("Manual checks", model.rows[2][1].value)
        assertEquals(0xFFDA3A7A.toInt(), model.rows[2][1].valueColor)
    }

    @Test
    fun settingsAutomationHeroFactoryKeepsAllowedReminderSummary() {
        val model = settingsAutomationHeroModel(
            current = RecordsSyncModels.Settings.kikuDefaults(),
            reminder = LocalStoreBase.ReminderSettings(true, 8, 5),
            autoSync = LocalStoreBase.AutoSyncSettings(false, false, 7, 30, 0L, 0L, 0L),
            autoUpdate = LocalStoreBase.AutoUpdateStatus(false, 0L, "", "", "", ""),
            notificationsAllowed = true,
        )

        assertEquals("08:05", model.rows[1][1].value)
        assertEquals(0xFF00AEB5.toInt(), model.rows[1][1].valueColor)
    }

    @Test
    fun workloadModelKeepsMutableSelectionsAndActions() {
        val calls = mutableListOf<String>()
        val workload = intArrayOf(20)
        val maxItems = intArrayOf(12)
        val saveMaximum = SettingsWorkloadAction { calls.add("saveMaximum") }
        val enableManual = SettingsWorkloadAction { calls.add("enableManual") }
        val saveWorkload = SettingsWorkloadAction { calls.add("saveWorkload") }
        val enableAutomatic = SettingsWorkloadAction { calls.add("enableAutomatic") }
        val model = SettingsWorkloadPanelModel(
            title = "Daily workload",
            autoMode = true,
            autoStatus = "Automatic workload",
            automaticBody = "Kani chooses the focus set.",
            manualBody = "Choose the percent manually.",
            selectedWorkloadPercent = workload,
            selectedMaxItems = maxItems,
            scaleLabels = listOf("Tiny", "Normal", "Huge"),
            saveMaximumLabel = "Save item limit",
            manualWorkloadLabel = "Set workload manually",
            saveWorkloadLabel = "Save workload",
            automaticParetoLabel = "Use automatic workload",
            onSaveMaximum = saveMaximum,
            onEnableManual = enableManual,
            onSaveWorkload = saveWorkload,
            onEnableAutomatic = enableAutomatic,
        )

        assertEquals("Daily workload", model.title)
        assertEquals(true, model.autoMode)
        assertEquals("Automatic workload", model.autoStatus)
        assertEquals("Kani chooses the focus set.", model.automaticBody)
        assertEquals("Choose the percent manually.", model.manualBody)
        assertSame(workload, model.selectedWorkloadPercent)
        assertSame(maxItems, model.selectedMaxItems)
        assertEquals(listOf("Tiny", "Normal", "Huge"), model.scaleLabels)
        assertEquals("Save item limit", model.saveMaximumLabel)
        assertEquals("Set workload manually", model.manualWorkloadLabel)
        assertEquals("Save workload", model.saveWorkloadLabel)
        assertEquals("Use automatic workload", model.automaticParetoLabel)
        assertSame(saveMaximum, model.onSaveMaximum)
        assertSame(enableManual, model.onEnableManual)
        assertSame(saveWorkload, model.onSaveWorkload)
        assertSame(enableAutomatic, model.onEnableAutomatic)
        model.onSaveMaximum.run()
        model.onEnableManual.run()
        model.onSaveWorkload.run()
        model.onEnableAutomatic.run()
        assertEquals(listOf("saveMaximum", "enableManual", "saveWorkload", "enableAutomatic"), calls)
        assertEquals(model, model.copy())
    }

    @Test
    fun studyLadderModelKeepsRungsAndActions() {
        val calls = mutableListOf<String>()
        val toggle = SettingsStudyLadderAction { calls.add("toggle") }
        val moveUp = SettingsStudyLadderAction { calls.add("moveUp") }
        val moveDown = SettingsStudyLadderAction { calls.add("moveDown") }
        val restore = SettingsStudyLadderAction { calls.add("restore") }
        val rung = SettingsStudyLadderRungModel(
            label = "Recognition",
            subtitle = "Current default rung",
            toggleLabel = "Disable",
            moveUpLabel = "Move up",
            moveDownLabel = "Move down",
            canMoveUp = true,
            canMoveDown = false,
            toggleDescription = "Disable Recognition",
            moveUpDescription = "Move up Recognition",
            moveDownDescription = "Move down Recognition",
            onToggle = toggle,
            onMoveUp = moveUp,
            onMoveDown = moveDown,
        )
        val model = SettingsStudyLadderPanelModel(
            title = "Study ladder",
            body = "Choose recognition steps.",
            rungs = listOf(rung),
            restoreLabel = "Restore defaults",
            restoreDescription = "Restore default study ladder",
            onRestore = restore,
        )

        assertEquals("Recognition", rung.label)
        assertEquals("Current default rung", rung.subtitle)
        assertEquals("Disable", rung.toggleLabel)
        assertEquals("Move up", rung.moveUpLabel)
        assertEquals("Move down", rung.moveDownLabel)
        assertEquals(true, rung.canMoveUp)
        assertEquals(false, rung.canMoveDown)
        assertEquals("Disable Recognition", rung.toggleDescription)
        assertEquals("Move up Recognition", rung.moveUpDescription)
        assertEquals("Move down Recognition", rung.moveDownDescription)
        assertSame(toggle, rung.onToggle)
        assertSame(moveUp, rung.onMoveUp)
        assertSame(moveDown, rung.onMoveDown)
        assertEquals("Study ladder", model.title)
        assertEquals("Choose recognition steps.", model.body)
        assertEquals(listOf(rung), model.rungs)
        assertEquals("Restore defaults", model.restoreLabel)
        assertEquals("Restore default study ladder", model.restoreDescription)
        assertSame(restore, model.onRestore)
        rung.onToggle.run()
        rung.onMoveUp.run()
        rung.onMoveDown.run()
        model.onRestore.run()
        assertEquals(listOf("toggle", "moveUp", "moveDown", "restore"), calls)
        assertEquals(rung, rung.copy())
        assertEquals(model, model.copy())
    }

    @Test
    fun settingsUpdateModelsKeepPendingFieldsAndCallbacks() {
        val calls = mutableListOf<String>()
        val install = { calls += "install" }
        val openSettings = { calls += "settings" }
        val toggle = { calls += "toggle" }
        val openUpdater = { calls += "updater" }
        val statusColor = ComposeColor(0xFFFF4C76)
        val permissionColor = ComposeColor(0xFF00AEB5)
        val panel = SettingsUpdatePanelModel(
            title = "App updates",
            statusLine = "Verified APK ready",
            statusColor = statusColor,
            lastCheckLine = "Checked today",
            lastResultLine = "No errors",
            installPermissionLine = "Permission granted",
            installPermissionColor = permissionColor,
            hasPendingUpdate = true,
            pendingVersionLine = "Version 0.5.0",
            pendingMessageLine = "Ready to install",
            canInstallUpdates = true,
            onInstallVerifiedUpdate = install,
            onOpenInstallSettings = openSettings,
            onToggleAutomaticUpdates = toggle,
            automaticUpdatesToggleLabel = "Turn off automatic checks",
        )
        val overview = SettingsUpdateOverviewPanelModel(
            panel = panel,
            openUpdaterLabel = "Open updater",
            onOpenUpdater = openUpdater,
        )

        assertEquals("App updates", panel.title)
        assertEquals("Verified APK ready", panel.statusLine)
        assertEquals(statusColor, panel.statusColor)
        assertEquals("Checked today", panel.lastCheckLine)
        assertEquals("No errors", panel.lastResultLine)
        assertEquals("Permission granted", panel.installPermissionLine)
        assertEquals(permissionColor, panel.installPermissionColor)
        assertEquals(true, panel.hasPendingUpdate)
        assertEquals("Version 0.5.0", panel.pendingVersionLine)
        assertEquals("Ready to install", panel.pendingMessageLine)
        assertEquals(true, panel.canInstallUpdates)
        assertSame(install, panel.onInstallVerifiedUpdate)
        assertSame(openSettings, panel.onOpenInstallSettings)
        assertSame(toggle, panel.onToggleAutomaticUpdates)
        assertEquals("Turn off automatic checks", panel.automaticUpdatesToggleLabel)
        assertSame(panel, overview.panel)
        assertEquals("Open updater", overview.openUpdaterLabel)
        assertSame(openUpdater, overview.onOpenUpdater)
        panel.onInstallVerifiedUpdate()
        panel.onOpenInstallSettings()
        panel.onToggleAutomaticUpdates()
        overview.onOpenUpdater()
        assertEquals(listOf("install", "settings", "toggle", "updater"), calls)
        assertEquals(panel, panel.copy())
        assertEquals(overview, overview.copy())
    }

    @Test
    fun settingsUpdateScreenModelsKeepNavigationCallbacks() {
        val calls = mutableListOf<String>()
        val home = { calls += "home" }
        val back = { calls += "back" }
        val check = { calls += "check" }
        val panel = SettingsUpdatePanelModel(
            title = "Automatic updates",
            statusLine = "On",
            statusColor = ComposeColor(0xFF00AEB5),
            lastCheckLine = "Never checked",
            lastResultLine = "No previous result",
            installPermissionLine = "Permission missing",
            installPermissionColor = ComposeColor(0xFFFF4C76),
            hasPendingUpdate = false,
            pendingVersionLine = null,
            pendingMessageLine = null,
            canInstallUpdates = false,
            onInstallVerifiedUpdate = {},
            onOpenInstallSettings = {},
            onToggleAutomaticUpdates = {},
            automaticUpdatesToggleLabel = "Turn off",
        )
        val page = SettingsUpdatePageModel(
            title = "Updater",
            onHome = home,
            onBack = back,
            onCheckForUpdate = check,
            panel = panel,
        )
        val run = SettingsUpdateRunModel(
            title = "Checking",
            progressLabel = "Checking GitHub",
            onHome = home,
            onBack = back,
        )

        assertEquals("Updater", page.title)
        assertSame(home, page.onHome)
        assertSame(back, page.onBack)
        assertSame(check, page.onCheckForUpdate)
        assertSame(panel, page.panel)
        assertEquals("Checking", run.title)
        assertEquals("Checking GitHub", run.progressLabel)
        assertSame(home, run.onHome)
        assertSame(back, run.onBack)
        page.onHome()
        page.onBack()
        page.onCheckForUpdate()
        run.onHome()
        run.onBack()
        assertEquals(listOf("home", "back", "check", "home", "back"), calls)
        assertEquals(page, page.copy())
        assertEquals(run, run.copy())
    }

    @Test
    fun studyAnswerPanelModelKeepsGlyphLinesAndHelperText() {
        val reading = StudyAnswerLineModel(
            text = "Reading: かに",
            color = ComposeColor(0xFFFF4C76),
            sizeSp = 17,
            bold = true,
        )
        val meaning = StudyAnswerLineModel(
            text = "crab",
            color = ComposeColor(0xFF3B2350),
            sizeSp = 15,
            bold = true,
        )
        val model = StudyAnswerPanelModel(
            title = "Answer",
            glyph = "蟹",
            glyphSizeSp = 76,
            lines = listOf(reading, meaning),
            helperText = "Trace it below, then check.",
        )

        assertEquals("Answer", model.title)
        assertEquals("蟹", model.glyph)
        assertEquals(76, model.glyphSizeSp)
        assertEquals(listOf(reading, meaning), model.lines)
        assertEquals("Trace it below, then check.", model.helperText)
        assertEquals("Reading: かに", reading.text)
        assertEquals(ComposeColor(0xFFFF4C76), reading.color)
        assertEquals(17, reading.sizeSp)
        assertEquals(true, reading.bold)
        assertEquals("crab", meaning.text)
        assertEquals(ComposeColor(0xFF3B2350), meaning.color)
        assertEquals(15, meaning.sizeSp)
        assertEquals(true, meaning.bold)
        assertEquals(model, model.copy())
        assertEquals(reading, reading.copy())
    }

    @Test
    fun studyChoiceModelsKeepSessionTextAndChoiceCallbacks() {
        val calls = mutableListOf<String>()
        val handler = KanjiChoiceHandler { calls += it }
        val grid = SimilarChoiceGridModel(
            choices = listOf("裂", "列", "烈"),
            balanceLastRow = true,
            onChoice = handler,
        )
        val answer = StudyAnswerPanelModel(
            title = "Answer",
            glyph = "裂",
            glyphSizeSp = 76,
            lines = listOf(
                StudyAnswerLineModel(
                    text = "Reading: れつ",
                    color = ComposeColor(0xFFFF4C76),
                    sizeSp = 17,
                    bold = true,
                )
            ),
            helperText = null,
        )
        val similar = SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            title = "Choose the kanji",
            taskLabel = MainActivityBase.LABEL_SIMILAR_KANJI,
            body = "Pick the matching kanji.",
            reasonLine = "Weak Anki evidence",
            question = "Which kanji means split?",
            gridModel = grid,
        )
        val meaning = MeaningChoiceSessionModel(
            modeLabel = "Recall",
            title = "Choose the kanji",
            taskLabel = "meaning -> kanji",
            body = "Pick the matching kanji.",
            reasonLine = "",
            question = "Which kanji means split?",
            choices = listOf("裂", "列", "烈", "劣"),
            answerPanel = answer,
            onChoice = handler,
        )

        assertEquals(listOf("裂", "列", "烈"), grid.choices)
        assertEquals(true, grid.balanceLastRow)
        assertSame(handler, grid.onChoice)
        assertEquals("Recognise", similar.modeLabel)
        assertEquals("Choose the kanji", similar.title)
        assertEquals(MainActivityBase.LABEL_SIMILAR_KANJI, similar.taskLabel)
        assertEquals("Pick the matching kanji.", similar.body)
        assertEquals("Weak Anki evidence", similar.reasonLine)
        assertEquals("Which kanji means split?", similar.question)
        assertSame(grid, similar.gridModel)
        assertEquals("Recall", meaning.modeLabel)
        assertEquals("meaning -> kanji", meaning.taskLabel)
        assertEquals(listOf("裂", "列", "烈", "劣"), meaning.choices)
        assertSame(answer, meaning.answerPanel)
        assertSame(handler, meaning.onChoice)
        val correctResult = MeaningChoiceResultModel(
            status = "Correct",
            statusColor = 0xFF00AEB5.toInt(),
            actionLabel = "Good",
            correctChoice = "裂",
            selectedChoiceCorrect = true,
        )
        val wrongResult = MeaningChoiceResultModel(
            status = "Wrong",
            statusColor = 0xFFFF4C76.toInt(),
            actionLabel = "Fail",
            correctChoice = "裂",
            selectedChoiceCorrect = false,
        )
        assertEquals(KanjiChoiceFeedback.CORRECT, feedbackForMeaningChoice("裂", "裂", correctResult))
        assertEquals(KanjiChoiceFeedback.CORRECT, feedbackForMeaningChoice("裂", "列", wrongResult))
        assertEquals(KanjiChoiceFeedback.INCORRECT, feedbackForMeaningChoice("列", "列", wrongResult))
        assertEquals(null, feedbackForMeaningChoice("烈", "列", wrongResult))
        assertEquals(null, feedbackForMeaningChoice("烈", null, wrongResult))
        grid.onChoice.onChoice("列")
        meaning.onChoice.onChoice("裂")
        assertEquals(listOf("列", "裂"), calls)
        assertEquals(grid, grid.copy())
        assertEquals(similar, similar.copy())
        assertEquals(meaning, meaning.copy())
    }

    @Test
    fun writingAnswerPanelStateKeepsVisibilityTransitions() {
        val hidden = WritingAnswerPanelState()
        val visible = WritingAnswerPanelState(true)

        assertEquals(false, hidden.visible)
        assertEquals(true, visible.visible)
        hidden.updateVisible(true)
        visible.updateVisible(false)

        assertEquals(true, hidden.visible)
        assertEquals(false, visible.visible)
    }

    @Test
    fun statsModelsKeepVerdictSectionsAndDefaults() {
        val line = StatsLineModel("裂: 88 -> 33")
        val verdict = StatsCardModel(
            title = "Kani is working",
            body = "Weak kanji and support are both improving.",
            fillColor = STATS_VERDICT_WORKING_FILL,
            strokeColor = STATS_TEAL_COLOR,
        )
        val section = StatsCardModel(
            title = "Weak kanji trend",
            summary = "3 weak kanji improved",
            body = "Average weakness fell.",
            lines = listOf(line),
            strokeColor = STATS_CORAL_COLOR,
            titleSizeSp = 20,
            summarySizeSp = 24,
            bodySizeSp = 16,
        )
        val screen = StatsScreenModel(
            title = "Stats",
            intro = "Kani repairs weak kanji.",
            verdict = verdict,
            sections = listOf(section),
        )

        assertEquals("Stats", screen.title)
        assertEquals("Kani repairs weak kanji.", screen.intro)
        assertSame(verdict, screen.verdict)
        assertEquals(listOf(section), screen.sections)
        assertEquals("Kani is working", verdict.title)
        assertEquals(null, verdict.summary)
        assertEquals("Weak kanji and support are both improving.", verdict.body)
        assertEquals(emptyList<StatsLineModel>(), verdict.lines)
        assertEquals(STATS_VERDICT_WORKING_FILL, verdict.fillColor)
        assertEquals(STATS_TEAL_COLOR, verdict.strokeColor)
        assertEquals(STATS_MUTED_COLOR, verdict.titleColor)
        assertEquals(STATS_INK_COLOR, verdict.summaryColor)
        assertEquals(STATS_MUTED_COLOR, verdict.bodyColor)
        assertEquals(18, verdict.titleSizeSp)
        assertEquals(25, verdict.summarySizeSp)
        assertEquals(15, verdict.bodySizeSp)
        assertEquals("Weak kanji trend", section.title)
        assertEquals("3 weak kanji improved", section.summary)
        assertEquals("Average weakness fell.", section.body)
        assertEquals(listOf(line), section.lines)
        assertEquals(STATS_WHITE_COLOR, section.fillColor)
        assertEquals(STATS_CORAL_COLOR, section.strokeColor)
        assertEquals(20, section.titleSizeSp)
        assertEquals(24, section.summarySizeSp)
        assertEquals(16, section.bodySizeSp)
        assertEquals("裂: 88 -> 33", line.text)
        assertEquals(STATS_INK_COLOR, line.color)
        assertEquals(true, line.bold)
        assertEquals(16, line.sizeSp)
        assertEquals(screen, screen.copy())
        assertEquals(section, section.copy())
        assertEquals(line, line.copy())
    }

    @Test
    fun writingStatusModelsKeepTextColorAndVisibility() {
        val muted = 0xFF826084.toInt()
        val coral = 0xFFFF4C76.toInt()
        val status = WritingStatusState()
        val result = WritingResultStatusHandle()

        assertEquals("", status.getText().toString())
        status.setStatus("Trace the first strokes", muted)
        assertEquals("Trace the first strokes", status.getText().toString())
        assertEquals(muted, status.color)
        status.setText("Existing analysis message")
        assertEquals("Existing analysis message", status.getText().toString())
        assertEquals(muted, status.color)

        assertEquals(false, result.visible)
        result.show("Model unavailable", coral)
        assertEquals("Model unavailable", result.getText().toString())
        assertEquals(coral, result.status.color)
        assertEquals(true, result.visible)
        result.hide()
        assertEquals(false, result.visible)
    }

    @Test
    fun typingAnswerStateKeepsTextAndWindowBounds() {
        val state = TypingAnswerState("split")

        assertEquals("split", state.text)
        assertEquals("split", state.getText().toString())
        assertEquals(false, state.containsWindowPoint(10f, 10f))

        state.updateText("rend")
        state.updateBounds(Rect(left = 4f, top = 8f, right = 24f, bottom = 38f))

        assertEquals("rend", state.text)
        assertEquals("rend", state.getText().toString())
        assertEquals(true, state.containsWindowPoint(4f, 8f))
        assertEquals(true, state.containsWindowPoint(24f, 38f))
        assertEquals(true, state.containsWindowPoint(12f, 20f))
        assertEquals(false, state.containsWindowPoint(3f, 20f))
        assertEquals(false, state.containsWindowPoint(12f, 39f))
    }
}

package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ComposeScreenModelsTest {
    @Test
    fun shellModelDefaultsToHomeAndCopiesSelectedRoute() {
        assertEquals("home", MainActivityShellModel().selectedRoute)

        val study = MainActivityShellModel().copy(selectedRoute = MainActivityBase.NAV_STUDY)

        assertEquals(MainActivityBase.NAV_STUDY, study.selectedRoute)
        assertEquals(MainActivityShellModel(MainActivityBase.NAV_STUDY), study)
    }

    @Test
    fun homeScreenModelKeepsAllCallbacksAndSections() {
        val onSync = {}
        val onStudy = {}
        val onFocus = {}
        val action = HomeActionModel("Stats", R.drawable.ic_stats_24) {}
        val coral = 0xFFFF4C76.toInt()
        val metric = HomeMetricModel(R.drawable.ic_target_24, coral, "Focus", "2", "Ready", null)

        val model = HomeScreenModel(
            title = "Kani",
            subtitle = "Repair weak kanji",
            metrics = listOf(metric),
            showSyncCta = true,
            syncLabel = "Sync",
            studyLabel = "Study",
            studySubtitle = "Next weak kanji",
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
        assertEquals(true, model.showSyncCta)
        assertEquals("Sync", model.syncLabel)
        assertEquals("Study", model.studyLabel)
        assertEquals("Next weak kanji", model.studySubtitle)
        assertSame(onSync, model.onSync)
        assertSame(onStudy, model.onStudy)
        assertEquals(listOf(action), model.actions)
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
            title = "Daily Anki sync",
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

        assertEquals("Daily Anki sync", model.title)
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
            title = "Offline data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = "Open licenses",
            onAction = Runnable {},
        )
        val category = settingsCategorySectionModel(
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
    fun referenceDataModelsKeepNavigationAndAttributionFields() {
        var opened = false
        var wentHome = false
        var wentBack = false
        val openAction = Runnable { opened = true }
        val homeAction = Runnable { wentHome = true }
        val backAction = Runnable { wentBack = true }
        val link = SettingsReferenceDataLinkModel(
            title = "Offline data licenses",
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

        assertEquals("Offline data licenses", link.title)
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
        val frequency = SettingsNewCardSortOptionModel("Frequency", "frequency")
        val risk = SettingsNewCardSortOptionModel("Retrievability risk", "retrievability_risk")
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
            title = "Ladder thresholds",
            body = "Tune rung movement.",
            promotionDaysLabel = "FSRS days to go up",
            initialPromotionDaysText = "21",
            failStreakLabel = "Fails to go down",
            initialFailStreakText = "3",
            defaultPromotionDaysText = "21",
            defaultFailStreakText = "3",
            defaultsLabel = "Use 21 and 3",
            saveLabel = "Save ladder thresholds",
            onSave = save,
        )

        assertEquals("Ladder thresholds", model.title)
        assertEquals("Tune rung movement.", model.body)
        assertEquals("FSRS days to go up", model.promotionDaysLabel)
        assertEquals("21", model.initialPromotionDaysText)
        assertEquals("Fails to go down", model.failStreakLabel)
        assertEquals("3", model.initialFailStreakText)
        assertEquals("21", model.defaultPromotionDaysText)
        assertEquals("3", model.defaultFailStreakText)
        assertEquals("Use 21 and 3", model.defaultsLabel)
        assertEquals("Save ladder thresholds", model.saveLabel)
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

        assertEquals("Settings cockpit", model.cockpitLabel)
        assertEquals(MainActivityBase.NAV_SETTINGS, model.title)
        assertEquals(4, model.rows.size)
        assertEquals("Note type", model.rows[0][0].label)
        assertEquals("Kiku", model.rows[0][0].value)
        assertEquals(0xFF4B2552.toInt(), model.rows[0][0].valueColor)
        assertEquals("Import filters", model.rows[0][1].label)
        assertEquals(0xFF00AEB5.toInt(), model.rows[0][1].valueColor)
        assertEquals("Reminder", model.rows[1][1].label)
        assertEquals("Blocked", model.rows[1][1].value)
        assertEquals(0xFF00AEB5.toInt(), model.rows[1][1].valueColor)
        assertEquals("Daily sync", model.rows[2][0].label)
        assertEquals("07:30", model.rows[2][0].value)
        assertEquals(0xFF00AEB5.toInt(), model.rows[2][0].valueColor)
        assertEquals("Updates", model.rows[2][1].label)
        assertEquals("Verified APK ready", model.rows[2][1].value)
        assertEquals(0xFFFF4C76.toInt(), model.rows[2][1].valueColor)
        assertEquals("Matching cards", model.rows[3][0].label)
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
            autoStatus = "Automatic Pareto",
            automaticBody = "Kani chooses the focus set.",
            manualBody = "Choose the percent manually.",
            selectedWorkloadPercent = workload,
            selectedMaxItems = maxItems,
            scaleLabels = listOf("Tiny", "Normal", "Huge"),
            saveMaximumLabel = "Save maximum",
            manualWorkloadLabel = "Manual workload",
            saveWorkloadLabel = "Save workload",
            automaticParetoLabel = "Automatic Pareto",
            onSaveMaximum = saveMaximum,
            onEnableManual = enableManual,
            onSaveWorkload = saveWorkload,
            onEnableAutomatic = enableAutomatic,
        )

        assertEquals("Daily workload", model.title)
        assertEquals(true, model.autoMode)
        assertEquals("Automatic Pareto", model.autoStatus)
        assertEquals("Kani chooses the focus set.", model.automaticBody)
        assertEquals("Choose the percent manually.", model.manualBody)
        assertSame(workload, model.selectedWorkloadPercent)
        assertSame(maxItems, model.selectedMaxItems)
        assertEquals(listOf("Tiny", "Normal", "Huge"), model.scaleLabels)
        assertEquals("Save maximum", model.saveMaximumLabel)
        assertEquals("Manual workload", model.manualWorkloadLabel)
        assertEquals("Save workload", model.saveWorkloadLabel)
        assertEquals("Automatic Pareto", model.automaticParetoLabel)
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
}

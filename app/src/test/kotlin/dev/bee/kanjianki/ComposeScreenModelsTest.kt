package dev.bee.kanjianki

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
}

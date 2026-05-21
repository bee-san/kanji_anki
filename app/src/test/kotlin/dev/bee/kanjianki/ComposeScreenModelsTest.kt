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
}

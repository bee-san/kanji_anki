package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.presentation.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of the Settings parity: the mapping turns a [SettingsSection] into
 * the portable screen the shared surface renders. The surface's layout is proven by
 * `:feature-settings`'s render tests on both hosts; this checks the root menu, the
 * ported sections' controls, the control-to-command round trip, and that an un-ported
 * section is the honest placeholder.
 */
class DesktopSettingsModelTest {
    @Test
    fun theRootBuildsTheFiveCategoryMenu() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT, snapshot())

        assertEquals(SettingsSection.ROOT, screen.section)
        val root = screen.root
        assertNotNull("root screen carries a menu", root)
        assertEquals(
            listOf(
                SettingsSection.IMPORT_SYNC,
                SettingsSection.STUDY_BEHAVIOR,
                SettingsSection.AUTOMATION,
                SettingsSection.APPEARANCE,
                SettingsSection.DISPLAY_DATA,
            ),
            root!!.categories.map { it.section },
        )
        // Every category names itself; none is blank.
        assertTrue(root.categories.all { it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test
    fun automationCarriesTheReminderCapabilityNotice() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT, snapshot())
        val automation = screen.root!!.categories.first { it.section == SettingsSection.AUTOMATION }

        assertEquals(1, automation.notices.size)
        assertTrue(automation.notices.single().contains("while Kani is open"))
    }

    @Test
    fun otherCategoriesCarryNoNotice() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT, snapshot())
        val nonAutomation = screen.root!!.categories.filter { it.section != SettingsSection.AUTOMATION }

        assertTrue(nonAutomation.all { it.notices.isEmpty() })
    }

    @Test
    fun anUnportedSectionIsTheHonestPlaceholder() {
        val ported = setOf(
            SettingsSection.ROOT,
            SettingsSection.APPEARANCE,
            SettingsSection.STUDY_BEHAVIOR,
        )
        for (section in SettingsSection.entries.filter { it !in ported }) {
            val screen = DesktopSettingsModel.screen(section, snapshot())
            assertEquals(section, screen.section)
            assertNull("a leaf section has no root menu", screen.root)
            assertEquals(SettingsSectionContent.Placeholder, screen.content)
        }
    }

    @Test
    fun appearanceOffersEveryThemeAndMarksTheCurrentOne() {
        val screen = DesktopSettingsModel.screen(
            SettingsSection.APPEARANCE,
            snapshot(theme = KaniThemeChoice.OCEAN_STUDY),
        )

        val content = screen.content as SettingsSectionContent.Controls
        val choice = content.controls.single() as SettingsControl.Choice
        assertEquals(
            KaniThemeChoice.entries.map { it.storageKey },
            choice.options.map { it.id },
        )
        assertEquals(KaniThemeChoice.OCEAN_STUDY.storageKey, choice.selectedId)
        // Every option dispatches a theme choice keyed by the shared setting key.
        val dark = choice.options.first { it.id == KaniThemeChoice.DARK.storageKey }
        assertEquals(
            KaniAction.Settings.SetChoice(KaniThemeChoice.SETTING_KEY, KaniThemeChoice.DARK.storageKey),
            dark.action,
        )
    }

    @Test
    fun studyBehaviorReflectsTheCurrentValuesAndBounds() {
        val screen = DesktopSettingsModel.screen(SettingsSection.STUDY_BEHAVIOR, snapshot())
        val controls = (screen.content as SettingsSectionContent.Controls).controls

        val sort = controls.filterIsInstance<SettingsControl.Choice>().single()
        assertEquals(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY, sort.selectedId)
        assertTrue(sort.options.any { it.id == RecordsBase.NEW_CARD_SORT_FREQUENCY })

        val steppers = controls.filterIsInstance<SettingsControl.Stepper>()
        val promotion = steppers.first { it.value == RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS }
        assertEquals(1, promotion.min)
        assertEquals(365, promotion.max)
        val demotion = steppers.first { it.max == 30 }
        assertEquals(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK, demotion.value)
    }

    @Test
    fun aThemeChoiceMapsBackToTheThemeSaveCommand() {
        val command = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetChoice(KaniThemeChoice.SETTING_KEY, KaniThemeChoice.MATCHA_MILK.storageKey),
            snapshot(),
        )
        assertEquals(SettingsSaveCommand.Theme(KaniThemeChoice.MATCHA_MILK), command)
    }

    @Test
    fun aLadderStepperMapsToTheThresholdCommandKeepingTheOtherValue() {
        // Promotion set to 28 keeps the current fail streak; only the touched value moves.
        val current = snapshot()
        val command = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetNumber("ladder_promotion_interval_days", 28),
            current,
        )
        assertEquals(
            SettingsSaveCommand.LadderThresholds(
                promotionIntervalDays = 28,
                demotionFailStreak = current.sync.ladderDemotionFailStreak,
            ),
            command,
        )

        val demotion = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetNumber("ladder_demotion_fail_streak", 5),
            current,
        )
        assertEquals(
            SettingsSaveCommand.LadderThresholds(
                promotionIntervalDays = current.sync.ladderPromotionIntervalDays,
                demotionFailStreak = 5,
            ),
            demotion,
        )
    }

    @Test
    fun sortAndStudyAheadMapToTheirCommands() {
        assertEquals(
            SettingsSaveCommand.NewCardSort(RecordsBase.NEW_CARD_SORT_FREQUENCY),
            DesktopSettingsModel.settingsCommandFor(
                KaniAction.Settings.SetChoice("new_card_sort_mode", RecordsBase.NEW_CARD_SORT_FREQUENCY),
                snapshot(),
            ),
        )
        assertEquals(
            SettingsSaveCommand.StudyAhead(minutes = 30),
            DesktopSettingsModel.settingsCommandFor(
                KaniAction.Settings.SetNumber("study_ahead_minutes", 30),
                snapshot(),
            ),
        )
    }

    @Test
    fun anUnmappedSettingsEditProducesNoCommand() {
        val current = snapshot()
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetChoice("unknown_key", "x"), current))
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetNumber("unknown_key", 1), current))
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetToggle("import_weak_cards", enabled = true), current))
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.Command("reset_ladder"), current))
    }

    private fun snapshot(theme: KaniThemeChoice = KaniThemeChoice.GIRLYPOP): SettingsSnapshot = SettingsSnapshot(
        sync = RecordsSyncModels.Settings.kikuDefaults(),
        tagRepairedCards = false,
        adaptiveWorkload = AdaptiveWorkloadSnapshot(workPercent = 100, maxItems = 40, mode = "balanced"),
        studyAheadMinutes = 0,
        studyLadder = RecordsBase.StudyLadderSettings.defaults(),
        schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
        schedulerFsrsWeights = null,
        learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
        themeChoice = theme,
        fsrsPersonalizationEnabled = false,
        fsrsFitSummaryJson = "",
    )
}

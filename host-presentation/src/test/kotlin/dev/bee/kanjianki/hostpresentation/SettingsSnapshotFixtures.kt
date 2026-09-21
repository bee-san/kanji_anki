package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SettingsSnapshot

/**
 * A default [SettingsSnapshot] for tests that need one but assert nothing about it.
 *
 * Shared because `SettingsSnapshot` has twelve required fields and every settings test
 * needs one: a private copy per test file means twelve places to edit when a thirteenth
 * field lands, and the copies drift in exactly the values a test then accidentally relies
 * on. A test that cares about a field overrides it at the call site.
 */
internal object SettingsSnapshotFixtures {
    fun blank(theme: KaniThemeChoice = KaniThemeChoice.GIRLYPOP): SettingsSnapshot = SettingsSnapshot(
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

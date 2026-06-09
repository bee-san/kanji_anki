package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyAheadPanelModelsTest {
    @Test
    fun createBuildsStudyAheadPanelModelFromCurrentMinutes() {
        var minutesText: String? = null

        val model = SettingsStudyAheadPanelModels.create(30) { minutesText = it }

        assertEquals("Study ahead", model.title)
        assertEquals("Review soon-due cards. Learning waits stay fixed.", model.body)
        assertEquals("Minutes ahead (0-1440)", model.minutesLabel)
        assertEquals("30", model.initialMinutesText)
        assertEquals("Save study ahead", model.saveLabel)

        model.onSave.save("45")
        assertEquals("45", minutesText)
    }
}

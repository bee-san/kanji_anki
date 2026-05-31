package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyAheadPanelModelsTest {
    @Test
    fun createBuildsStudyAheadPanelModelFromCurrentMinutes() {
        val action = RecordingSaveAction()

        val model = SettingsStudyAheadPanelModels.create(30, action)

        assertEquals("Study ahead", model.title)
        assertEquals("Minutes (0-1440)", model.minutesLabel)
        assertEquals("30", model.initialMinutesText)
        assertEquals("Save study ahead", model.saveLabel)

        model.onSave.save("45")
        assertEquals("45", action.minutesText)
    }

    private class RecordingSaveAction : SettingsStudyAheadSaver {
        var minutesText: String? = null

        override fun save(minutesText: String) {
            this.minutesText = minutesText
        }
    }
}

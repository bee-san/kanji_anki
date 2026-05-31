package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStepsSettingsPolicyTest {
    @Test
    fun saveRequestBuildsLearningStepSettingsFromBothInputs() {
        val result = LearningStepsSettingsPolicy.saveRequest("1m, 10m", "10m 1h")

        assertTrue(result.valid)
        val settings = result.settings!!
        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertEquals(listOf(10, 60), settings.reviewStepsMinutes)
        assertEquals("", result.message)
    }

    @Test
    fun saveRequestAllowsEmptyReviewSteps() {
        val result = LearningStepsSettingsPolicy.saveRequest("1m, 10m", "")

        assertTrue(result.valid)
        val settings = result.settings!!
        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertTrue(settings.reviewStepsMinutes.isEmpty())
    }

    @Test
    fun saveResultFactoriesRemainCallableFromJava() {
        val settings = RecordsSchedulerModels.LearningStepSettings(listOf(1), listOf(10))

        val valid = LearningStepsSettingsPolicy.SaveResult.valid(settings)
        val invalid = LearningStepsSettingsPolicy.SaveResult.invalid("bad")

        assertTrue(valid.valid)
        assertEquals(settings, valid.settings)
        assertEquals("", valid.message)
        assertFalse(invalid.valid)
        assertNull(invalid.settings)
        assertEquals("bad", invalid.message)
    }

    @Test
    fun saveRequestRejectsInvalidNewStepsWithExistingCopy() {
        assertInvalid("", "10m")
        assertInvalid("soon", "10m")
        assertInvalid("0m", "10m")
    }

    @Test
    fun saveRequestRejectsInvalidReviewStepsWithExistingCopy() {
        assertInvalid("1m, 10m", "soon")
        assertInvalid("1m, 10m", "0m")
    }

    private fun assertInvalid(newStepsText: String, reviewStepsText: String) {
        val result = LearningStepsSettingsPolicy.saveRequest(newStepsText, reviewStepsText)

        assertFalse(result.valid)
        assertNull(result.settings)
        assertEquals(LearningStepsSettingsPolicy.STEP_FORMAT_ERROR, result.message)
    }
}

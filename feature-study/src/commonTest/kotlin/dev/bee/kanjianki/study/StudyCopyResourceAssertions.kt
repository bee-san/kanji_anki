package dev.bee.kanjianki.study

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import dev.bee.kanjianki.presentation.StudyProgress
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises this module's own Compose Multiplatform resources, not marker strings.
 *
 * The counterpart to Home's resource assertion: it catches a string that resolves
 * under Skiko but not through Android's asset loader, and a template that shipped
 * with its `%1$d` placeholder intact — which no type checker would object to.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedStudyResourcesResolveOnThisHost() {
    renderStudy(
        content = {
            val copy = rememberStudyCopy()
            StudySessionScreen(
                StudySession(
                    state = StudySessionState.CARD,
                    progress = StudyProgress(completed = 2, target = 5),
                    card = flashcard(),
                ),
                copy,
                TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        val progress = onNodeWithTag(STUDY_PROGRESS_TEST_TAG).contentDescriptionOrEmpty()
        assertTrue("2" in progress && "5" in progress, "the shipped progress line must substitute: $progress")
        assertFalse("%" in progress, "the shipped progress line kept a placeholder: $progress")
    }

    renderStudy(
        content = { StudySessionScreen(StudySession(state = StudySessionState.DONE), rememberStudyCopy(), TestUiTextResolver, dispatch = {}) },
    ) {
        val text = onNodeWithTag(STUDY_DONE_TEST_TAG).subtreeTextOrEmpty()
        assertTrue(text.isNotBlank(), "the shipped done copy must resolve")
        assertFalse("%" in text, "the shipped done copy kept a placeholder: $text")
    }
}

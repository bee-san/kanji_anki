package dev.bee.kanjianki

import dev.bee.kanjianki.core.StudyTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class MainActivityStudyAnswerAnkiTapActionTest {
    @Test
    fun copyIdDelegatesTheExactActionWithoutTryingToLaunchAnkiDroid() {
        val action = StudyAnswerAnkiTapActionModel.CopyId(
            kind = StudyAnswerAnkiCopiedIdKind.CARD,
            value = 99L,
            toastMessage = StudyTextCopy.studyAnswerAnkiCardIdCopiedMessage(),
        )
        var launchCount = 0
        var copiedAction: StudyAnswerAnkiTapActionModel.CopyId? = null

        performStudyAnswerAnkiTapAction(
            action = action,
            launchAnkiDroid = {
                launchCount += 1
                true
            },
            copyId = { copiedAction = it },
        )

        assertEquals(0, launchCount)
        assertSame(action, copiedAction)
    }

    @Test
    fun failedAnkiDroidLaunchRecursesToTheNoteIdCopyFallback() {
        val action = StudyAnswerAnkiTapActionModel.OpenAnkiDroid(
            noteId = 42L,
            cardId = 99L,
        )
        val launchAttempts = mutableListOf<StudyAnswerAnkiTapActionModel.OpenAnkiDroid>()
        val copiedActions = mutableListOf<StudyAnswerAnkiTapActionModel.CopyId>()

        performStudyAnswerAnkiTapAction(
            action = action,
            launchAnkiDroid = {
                launchAttempts += it
                false
            },
            copyId = { copiedActions += it },
        )

        assertEquals(listOf(action), launchAttempts)
        assertEquals(1, copiedActions.size)
        assertEquals(StudyAnswerAnkiCopiedIdKind.NOTE, copiedActions.single().kind)
        assertEquals(42L, copiedActions.single().value)
        assertEquals(
            StudyTextCopy.studyAnswerAnkiNoteIdCopiedMessage(),
            copiedActions.single().toastMessage,
        )
    }

    @Test
    fun successfulAnkiDroidLaunchDoesNotCopyAnId() {
        val action = StudyAnswerAnkiTapActionModel.OpenAnkiDroid(
            noteId = null,
            cardId = 99L,
        )
        var copied = false

        performStudyAnswerAnkiTapAction(
            action = action,
            launchAnkiDroid = { true },
            copyId = { copied = true },
        )

        assertFalse(copied)
    }
}

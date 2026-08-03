package dev.bee.kanjianki.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.study.generated.resources.Res
import dev.bee.kanjianki.feature.study.generated.resources.study_continue
import dev.bee.kanjianki.feature.study.generated.resources.study_done_body
import dev.bee.kanjianki.feature.study.generated.resources.study_done_home
import dev.bee.kanjianki.feature.study.generated.resources.study_done_title
import dev.bee.kanjianki.feature.study.generated.resources.study_empty_body
import dev.bee.kanjianki.feature.study.generated.resources.study_empty_title
import dev.bee.kanjianki.feature.study.generated.resources.study_fail
import dev.bee.kanjianki.feature.study.generated.resources.study_pass
import dev.bee.kanjianki.feature.study.generated.resources.study_progress
import dev.bee.kanjianki.feature.study.generated.resources.study_reveal
import dev.bee.kanjianki.feature.study.generated.resources.study_submit
import dev.bee.kanjianki.feature.study.generated.resources.study_undo
import org.jetbrains.compose.resources.stringResource

/**
 * The study session's structural wording.
 *
 * Small on purpose: the task-specific text — what each card asks, the answer lines,
 * the rung labels, the feedback — is computed in `:core`'s `StudyTaskCopy`/
 * `StudyTextCopy` and reaches the surface as pre-resolved [dev.bee.kanjianki.presentation.UiText]
 * on the model, exactly as Home's does. Only the labels the session shell adds itself
 * live here: the progress line, the two grades, and the done/empty screens.
 *
 * The grade labels are the shell's default Pass/Fail, used when a card does not carry
 * its own label. A card's own [dev.bee.kanjianki.presentation.StudyGradeAction.label]
 * wins where present, which is how the writing rung shows "Save hard".
 */
data class StudyCopy(
    val pass: String,
    val fail: String,
    val cont: String,
    val reveal: String,
    val submit: String,
    val undo: String,
    val doneTitle: String,
    val doneBody: String,
    val doneHome: String,
    val emptyTitle: String,
    val emptyBody: String,
    private val progressTemplate: String,
) {
    /** The "N of M" progress line. */
    fun progress(completed: Int, target: Int): String = progressTemplate
        .replace(FIRST_ARGUMENT, completed.toString())
        .replace(SECOND_ARGUMENT, target.toString())

    companion object {
        private const val FIRST_ARGUMENT = "%1\$d"
        private const val SECOND_ARGUMENT = "%2\$d"
    }
}

/** Resolves [StudyCopy] from this module's resources. */
@Composable
fun rememberStudyCopy(): StudyCopy {
    val fixed = FixedStudyStrings(
        pass = stringResource(Res.string.study_pass),
        fail = stringResource(Res.string.study_fail),
        cont = stringResource(Res.string.study_continue),
        reveal = stringResource(Res.string.study_reveal),
        submit = stringResource(Res.string.study_submit),
        undo = stringResource(Res.string.study_undo),
        doneTitle = stringResource(Res.string.study_done_title),
        doneBody = stringResource(Res.string.study_done_body),
        doneHome = stringResource(Res.string.study_done_home),
        emptyTitle = stringResource(Res.string.study_empty_title),
        emptyBody = stringResource(Res.string.study_empty_body),
        progressTemplate = stringResource(Res.string.study_progress),
    )
    return remember(fixed) { fixed.toCopy() }
}

private data class FixedStudyStrings(
    val pass: String,
    val fail: String,
    val cont: String,
    val reveal: String,
    val submit: String,
    val undo: String,
    val doneTitle: String,
    val doneBody: String,
    val doneHome: String,
    val emptyTitle: String,
    val emptyBody: String,
    val progressTemplate: String,
) {
    fun toCopy(): StudyCopy = StudyCopy(
        pass = pass,
        fail = fail,
        cont = cont,
        reveal = reveal,
        submit = submit,
        undo = undo,
        doneTitle = doneTitle,
        doneBody = doneBody,
        doneHome = doneHome,
        emptyTitle = emptyTitle,
        emptyBody = emptyBody,
        progressTemplate = progressTemplate,
    )
}

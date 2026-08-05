@file:JvmName("MainActivityStudyChoiceResultCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MeaningChoiceResultActionBar(
    model: MeaningChoiceSessionModel,
    state: MeaningChoiceSessionState,
    // Observable feedback for this route, or null when the caller has none to observe.
    // Defaulting to `model.feedbackState?.snapshot()` keeps test callers compiling, but a
    // production caller must pass the snapshot it collected from `studySessionUiState`:
    // see the comment on the parameter read below.
    feedback: StudyAnswerFeedbackSnapshot? = model.feedbackState?.snapshot(),
) {
    val selectedChoice = state.selectedChoice ?: return
    val result = model.resultResolver?.resultForChoice(selectedChoice) ?: return
    MeaningChoiceResultActionBar(
        status = result.status,
        statusColor = result.statusColor,
        actionTone = result.actionTone,
        continueEnabled = feedback?.let { it.phase == StudyAnswerFeedbackPhase.APPLIED } ?: true,
        continueAction = model.continueAction,
        onNext = { model.onContinue.run() },
    )
}

@Composable
internal fun MeaningChoiceResultActionBar(
    status: String,
    statusColor: Int,
    actionTone: StudyActionTone,
    continueEnabled: Boolean = true,
    continueAction: StudyContinueAction? = null,
    onNext: () -> Unit,
) {
    val continueBinding = if (continueAction == null) {
        null
    } else {
        rememberStudyContinueUiBinding(continueAction)
    }
    // Read the parameter, not `continueAction.feedbackState.continueEnabled`. The feedback
    // state lives in `:application`, a plain JVM module with no Compose dependency, so its
    // phase is an ordinary field rather than a `mutableStateOf` -- reading it here creates
    // no subscription, and the button keeps whatever enablement it had at first
    // composition. Every caller derives this parameter from the observable session model
    // instead, which is what actually recomposes when the answer is applied.
    val actionEnabled = continueEnabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = kaniColor(statusColor),
            fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
        )
        StudyPrimaryActionButton(
            label = dev.bee.kanjianki.core.StudyTextCopy.continueLabel(),
            onClick = continueBinding?.onClick ?: onNext,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    this[StudyExplicitContinueSemantics] = true
                }
                .then(continueBinding?.modifier ?: Modifier),
            enabled = actionEnabled,
            tone = actionTone,
        )
    }
}

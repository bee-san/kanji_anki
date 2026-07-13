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
) {
    val selectedChoice = state.selectedChoice ?: return
    val result = model.resultResolver?.resultForChoice(selectedChoice) ?: return
    MeaningChoiceResultActionBar(
        status = result.status,
        statusColor = result.statusColor,
        actionTone = result.actionTone,
        continueEnabled = model.feedbackState?.continueEnabled ?: true,
        onNext = { model.onContinue.run() },
    )
}

@Composable
internal fun MeaningChoiceResultActionBar(
    status: String,
    statusColor: Int,
    actionTone: StudyActionTone,
    continueEnabled: Boolean = true,
    onNext: () -> Unit,
) {
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
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth(),
            enabled = continueEnabled,
            tone = actionTone,
        )
    }
}

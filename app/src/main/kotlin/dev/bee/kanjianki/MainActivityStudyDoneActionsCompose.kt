@file:JvmName("MainActivityStudyDoneActionsCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudyDonePrimary = Color(0xFFDA3A7A)
private val StudyDonePrimaryBorder = Color(0xFFFFADCD)
private val StudyDoneSecondaryText = Color(0xFF4B2552)
private val StudyDoneCardBackground = Color(0xFFFFF7FB)
private val StudyDoneInsetBackground = Color(0xFFFFFFFF)
private val StudyDoneMuted = Color(0xFF6C5674)

data class StudyDoneScreenModel(
    val modeLabel: String,
    val title: String,
    val headline: String?,
    val body: String,
    val summaryLines: List<String>,
    val showDoneActions: Boolean,
    val availableStudyMoreNewCards: Int,
    val showBackHome: Boolean,
    val backHomePrimary: Boolean,
    val onStudyMore: Runnable,
    val onContinueAll: Runnable,
    val onBackHome: Runnable,
)

internal fun studyDoneScreenView(
    context: Context,
    model: StudyDoneScreenModel
): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                StudyDoneScreen(model)
            }
        }
    }
}

@Composable
fun StudyDoneActions(
    availableStudyMoreNewCards: Int,
    onStudyMore: () -> Unit,
    onContinueAll: () -> Unit,
    onBackHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (availableStudyMoreNewCards > 0) {
            StudyPrimaryButton(
                label = "Study more new cards",
                onClick = onStudyMore
            )
        }
        if (availableStudyMoreNewCards > 0) {
            StudySecondaryButton(
                label = MainActivityBase.LABEL_CONTINUE_ALL_KANJI,
                onClick = onContinueAll
            )
        } else {
            StudyPrimaryButton(
                label = MainActivityBase.LABEL_CONTINUE_ALL_KANJI,
                onClick = onContinueAll
            )
        }
        StudySecondaryButton(
            label = MainActivityBase.LABEL_BACK_HOME,
            onClick = onBackHome
        )
    }
}

@Composable
fun StudyDoneScreen(model: StudyDoneScreenModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = StudyDoneCardBackground,
        border = BorderStroke(1.dp, StudyDonePrimaryBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudyModePill(model.modeLabel)
            Text(
                text = model.title,
                color = StudyDoneSecondaryText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            model.headline?.let { headline ->
                Text(
                    text = headline,
                    color = StudyDoneSecondaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = model.body,
                color = StudyDoneMuted,
                fontSize = 17.sp
            )
            if (model.summaryLines.isNotEmpty()) {
                StudyDoneSummary(lines = model.summaryLines)
            }
            if (model.showDoneActions) {
                StudyDoneActions(
                    availableStudyMoreNewCards = model.availableStudyMoreNewCards,
                    onStudyMore = { model.onStudyMore.run() },
                    onContinueAll = { model.onContinueAll.run() },
                    onBackHome = { model.onBackHome.run() }
                )
            } else if (model.showBackHome) {
                if (model.backHomePrimary) {
                    StudyPrimaryButton(
                        label = MainActivityBase.LABEL_BACK_HOME,
                        onClick = { model.onBackHome.run() }
                    )
                } else {
                    StudySecondaryButton(
                        label = MainActivityBase.LABEL_BACK_HOME,
                        onClick = { model.onBackHome.run() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyModePill(label: String) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, StudyDonePrimaryBorder)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = StudyDonePrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudyDoneSummary(lines: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = StudyDoneInsetBackground,
        border = BorderStroke(1.dp, StudyDonePrimaryBorder.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    color = if (index == 0) StudyDoneSecondaryText else StudyDoneMuted,
                    fontSize = if (index == 0) 20.sp else 15.sp,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun StudyPrimaryButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudyDonePrimary,
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, StudyDonePrimaryBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudySecondaryButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudyDonePrimaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = StudyDoneSecondaryText
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = StudyDoneSecondaryText,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

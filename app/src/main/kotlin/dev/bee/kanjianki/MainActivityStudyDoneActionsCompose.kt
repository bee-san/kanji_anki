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

internal fun studyDoneActionsView(
    context: Context,
    availableStudyMoreNewCards: Int,
    onStudyMore: Runnable,
    onContinueAll: Runnable,
    onBackHome: Runnable
): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    StudyDoneActions(
                        availableStudyMoreNewCards = availableStudyMoreNewCards,
                        onStudyMore = { onStudyMore.run() },
                        onContinueAll = { onContinueAll.run() },
                        onBackHome = { onBackHome.run() }
                    )
                }
            }
        }
    }
}

internal fun studyBackHomeButtonView(
    context: Context,
    primary: Boolean,
    onBackHome: Runnable
): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    if (primary) {
                        StudyPrimaryButton(
                            label = MainActivityBase.LABEL_BACK_HOME,
                            onClick = { onBackHome.run() }
                        )
                    } else {
                        StudySecondaryButton(
                            label = MainActivityBase.LABEL_BACK_HOME,
                            onClick = { onBackHome.run() }
                        )
                    }
                }
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

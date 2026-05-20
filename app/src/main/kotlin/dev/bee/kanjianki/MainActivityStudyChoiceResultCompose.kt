@file:JvmName("MainActivityStudyChoiceResultCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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

private val StudyChoiceResultPrimary = Color(MainActivityUiSupport.STUDY_PINK_DARK)
private val StudyChoiceResultPrimaryBorder = Color(MainActivityUiSupport.STUDY_BORDER)

internal fun meaningKanjiChoiceResultActionBarView(
    context: Context,
    status: String,
    statusColor: Int,
    onNext: Runnable,
): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                MeaningChoiceResultActionBar(
                    status = status,
                    statusColor = statusColor,
                    onNext = { onNext.run() }
                )
            }
        }
    }
}

@Composable
internal fun MeaningChoiceResultActionBar(
    status: String,
    statusColor: Int,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(bottom = 8.dp),
            color = Color(statusColor),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
        )
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StudyChoiceResultPrimary,
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, StudyChoiceResultPrimaryBorder),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Next",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

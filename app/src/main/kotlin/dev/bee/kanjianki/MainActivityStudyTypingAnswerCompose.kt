@file:JvmName("MainActivityStudyTypingAnswerCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val TypingAnswerMuted = Color(MainActivityUiSupport.STUDY_HERO_MUTED)

internal fun typingMeaningAnswerView(
    activity: MainActivityStudy,
    input: EditText,
): View {
    return ComposeView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                TypingMeaningAnswer(label = MainActivityBase.LABEL_MEANING, input = input)
            }
        }
    }
}

@Composable
internal fun TypingMeaningAnswer(label: String, input: EditText) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = TypingAnswerMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
        )
        AndroidView(
            factory = { input },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        )
    }
}

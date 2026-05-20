@file:JvmName("MainActivityStudyTypingAnswerCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TypingAnswerMuted = Color(MainActivityUiSupport.STUDY_HERO_MUTED)
private val TypingAnswerText = Color(MainActivityUiSupport.STUDY_PLUM)
private val TypingAnswerBorder = Color(MainActivityUiSupport.STUDY_BORDER)

class TypingAnswerState @JvmOverloads constructor(initialText: String = "") {
    private var value by mutableStateOf(initialText)

    private var boundsInWindow: Rect? = null

    internal val text: String
        get() = value

    fun getText(): CharSequence {
        return value
    }

    fun containsWindowPoint(x: Float, y: Float): Boolean {
        val bounds = boundsInWindow ?: return false
        return x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
    }

    internal fun updateText(value: String) {
        this.value = value
    }

    internal fun updateBounds(bounds: Rect) {
        boundsInWindow = bounds
    }
}

@Composable
internal fun TypingMeaningAnswer(label: String, state: TypingAnswerState) {
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
        BasicTextField(
            value = state.text,
            onValueChange = state::updateText,
            singleLine = true,
            textStyle = TextStyle(
                color = TypingAnswerText,
                fontSize = 20.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .onGloballyPositioned { coordinates ->
                    state.updateBounds(coordinates.boundsInWindow())
                },
            decorationBox = { innerTextField ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, TypingAnswerBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (state.text.isEmpty()) {
                            Text(
                                text = label,
                                color = TypingAnswerMuted,
                                fontSize = 20.sp,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

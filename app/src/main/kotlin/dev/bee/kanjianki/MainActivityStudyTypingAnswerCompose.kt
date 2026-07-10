@file:JvmName("MainActivityStudyTypingAnswerCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TypingAnswerMuted: Color @Composable get() = KaniTheme.colors.muted
private val TypingAnswerText: Color @Composable get() = KaniTheme.colors.plum
private val TypingAnswerBorder: Color @Composable get() = KaniTheme.colors.border

internal fun isTypingMeaningSubmitKey(action: Int, keyCode: Int): Boolean {
    return action == AndroidKeyEvent.ACTION_UP &&
        (keyCode == AndroidKeyEvent.KEYCODE_ENTER || keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)
}

@Composable
internal fun TypingMeaningAnswer(
    label: String,
    state: TypingAnswerState,
    onDone: Runnable? = null,
) {
    val submitAnswer = {
        onDone?.run()
    }
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(state) {
        onDispose { state.clearBounds() }
    }
    LaunchedEffect(state) {
        // Every typing rung requires an answer, so focus the field (and show the
        // keyboard) as soon as the card appears instead of requiring a tap.
        focusRequester.requestFocus()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicTextField(
            value = state.text,
            onValueChange = state::updateText,
            singleLine = true,
            textStyle = TextStyle(
                color = TypingAnswerText,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onDone = { submitAnswer() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isTypingMeaningSubmitKey(native.action, native.keyCode)) {
                        submitAnswer()
                        true
                    } else {
                        false
                    }
                }
                .onGloballyPositioned { coordinates ->
                    state.updateBounds(coordinates.boundsInWindow())
                },
            decorationBox = { innerTextField ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = KaniUiTokens.StudyShapeMedium,
                    color = KaniTheme.colors.surface,
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
                                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
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

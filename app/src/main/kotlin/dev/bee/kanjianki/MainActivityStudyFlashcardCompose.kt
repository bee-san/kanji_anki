@file:JvmName("MainActivityStudyFlashcardCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudyActionPinkDark = Color(0xFFDA3A7A)
private val StudyActionBorder = Color(0xFFFFADCD)
private val StudyActionFailFill = Color(0xFFFFF5FA)

internal class FlashcardActionBarState(
    revealed: Boolean,
    val onReveal: Runnable,
    val onFail: Runnable,
    val onPass: Runnable,
) {
    var revealed by mutableStateOf(revealed)
}

@Composable
fun StudyFlashcardActionBar(
    revealed: Boolean,
    onReveal: () -> Unit,
    onFail: () -> Unit,
    onPass: () -> Unit,
) {
    if (!revealed) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StudyRevealButton(onReveal = onReveal)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudyFailButton(
                onClick = onFail,
                modifier = Modifier.weight(1f)
            )
            StudyPassButton(
                onClick = onPass,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StudyRevealButton(onReveal: () -> Unit) {
    Button(
        onClick = onReveal,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudyActionPinkDark,
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, StudyActionBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_eye_24),
            contentDescription = null,
            tint = Color.White
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Reveal",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudyFailButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 62.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudyActionBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StudyActionFailFill,
            contentColor = StudyActionPinkDark
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = "Fail",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = StudyActionPinkDark,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudyPassButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 62.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudyActionPinkDark,
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, StudyActionBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = MainActivityBase.LABEL_PASS,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@file:JvmName("MainActivityStudyFlashcardCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    StudyPrimaryActionButton(
        label = "Reveal",
        onClick = onReveal,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_eye_24),
            contentDescription = null,
            tint = Color.White
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun StudyFailButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    StudySecondaryActionButton(MainActivityBase.LABEL_FAIL, onClick, modifier)
}

@Composable
private fun StudyPassButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    StudyPrimaryActionButton(MainActivityBase.LABEL_PASS, onClick, modifier)
}

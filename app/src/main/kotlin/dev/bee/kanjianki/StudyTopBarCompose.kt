@file:JvmName("StudyTopBarCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val StudyHeroPlum = Color(0xFF7A245D)
private val StudyHeroPink = Color(0xFFF82D72)
private val StudyHeroPinkDark = Color(0xFFE62A6D)
private val StudyHeroTrack = Color(0xFFFBDDEC)
private val StudyTopBarButtonFill = Color(0xFFFFF2F8)
private val StudyTopBarButtonShape = RoundedCornerShape(28.dp)
object StudyTopBarDescriptions {
    const val PROGRESS = "Study progress"
}

private val StudyTopBarProgressTextStyle = TextStyle(
    fontSize = 18.sp,
    fontWeight = FontWeight.Bold,
    color = StudyHeroPlum,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

fun studyTopBarView(
    context: Context,
    completed: Int,
    target: Int,
    fraction: Float,
    closeAction: Runnable,
    settingsAction: Runnable
): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, context.dp(18))
        }
        setContent {
            MaterialTheme {
                StudyTopBar(
                    completed = completed,
                    target = target,
                    fraction = fraction,
                    onClose = closeAction::run,
                    onSettings = settingsAction::run
                )
            }
        }
    }
}

@Composable
fun StudyTopBar(
    completed: Int,
    target: Int,
    fraction: Float,
    onClose: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudyTopBarIconButton(
                iconRes = R.drawable.ic_close_24,
                description = "Close study",
                onClick = onClose
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$completed / $target",
                    modifier = Modifier.fillMaxWidth(),
                    style = StudyTopBarProgressTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.height(8.dp))
                StudyProgressPill(fraction = fraction)
            }
            Spacer(modifier = Modifier.width(10.dp))
            StudyTopBarIconButton(
                iconRes = R.drawable.ic_settings_24,
                description = "Settings",
                onClick = onSettings
            )
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

@Composable
private fun StudyTopBarIconButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        shape = StudyTopBarButtonShape,
        color = StudyTopBarButtonFill,
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = StudyHeroPinkDark
            )
        }
    }
}

@Composable
private fun StudyProgressPill(fraction: Float) {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .semantics {
                contentDescription = StudyTopBarDescriptions.PROGRESS
                progressBarRangeInfo = ProgressBarRangeInfo(clampedFraction, 0f..1f)
            }
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = StudyHeroTrack,
            cornerRadius = CornerRadius(radius, radius)
        )
        if (clampedFraction > 0f) {
            drawRoundRect(
                color = StudyHeroPink,
                size = size.copy(width = size.width * clampedFraction),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@file:JvmName("StudyTopBarCompose")

package dev.bee.kanjianki

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
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
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy

private val StudyHeroPlum: Color @Composable get() = KaniTheme.colors.plum
private val StudyHeroPink: Color @Composable get() = KaniTheme.colors.primary
private val StudyHeroPinkDark: Color @Composable get() = KaniTheme.colors.primary
private val StudyHeroTrack: Color @Composable get() = KaniTheme.colors.track
private val StudyTopBarButtonFill: Color @Composable get() = KaniTheme.colors.pill
private val StudyTopBarButtonShape = RoundedCornerShape(28.dp)
internal val StudyTopBarButtonElevation = 0.dp
object StudyTopBarDescriptions {
    val PROGRESS: String
        get() = StudyTextCopy.studyProgressDescription()
}

private val StudyTopBarProgressTextStyle: TextStyle
    @Composable get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = StudyHeroPlum,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

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
                description = StudyTextCopy.closeStudyLabel(),
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
                description = SettingsTextCopy.settingsTitle(),
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun StudyTopBarIconButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = { withButtonTrace("$description button") { onClick() } },
        modifier = Modifier
            .size(56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        shape = StudyTopBarButtonShape,
        color = StudyTopBarButtonFill,
        shadowElevation = StudyTopBarButtonElevation
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
    val animatedFraction by animateFloatAsState(
        targetValue = clampedFraction,
        label = "study-progress-fill"
    )
    val trackColor = StudyHeroTrack
    val fillColor = StudyHeroPink
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
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius)
        )
        if (animatedFraction > 0f) {
            drawRoundRect(
                color = fillColor,
                size = size.copy(width = size.width * animatedFraction),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

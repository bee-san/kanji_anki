@file:JvmName("MainActivityHomeOverviewCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import android.widget.LinearLayout

private val HomeInk = Color(0xFF2D1635)
private val HomeMuted = Color(0xFF6C5674)

internal fun homeHeaderView(home: MainActivityHome): View {
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                HomeHeader(
                    title = HomeTextCopy.appTitle(),
                    subtitle = HomeTextCopy.appSubtitle()
                )
            }
        }
    }
}

internal fun homeStudyCtaView(home: MainActivityHome): View {
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, home.dp(94))
        setContent {
            MaterialTheme {
                HomeStudyCta(
                    title = MainActivityBase.LABEL_STUDY_NOW,
                    subtitle = HomeTextCopy.studySupportText(),
                    onClick = home::startFocusedStudy
                )
            }
        }
    }
}

internal fun metricCardView(
    home: MainActivityHome,
    iconRes: Int,
    accent: Int,
    label: String,
    value: String,
    body: String?,
    action: Runnable?
): View {
    return ComposeView(home).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(home.dp(4), 0, home.dp(4), 0)
        }
        if (action != null) {
            setOnClickListener { action.run() }
        }
        setContent {
            MaterialTheme {
                HomeMetricCard(
                    iconRes = iconRes,
                    accent = accent,
                    label = label,
                    value = value,
                    body = body
                )
            }
        }
    }
}

@Composable
fun HomeHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = HomeInk,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = subtitle,
                color = HomeMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(110.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun HomeStudyCta(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val noFontPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF749C),
                        Color(0xFFFF3A70)
                    )
                )
            )
            .border(2.dp, Color(0xFFFFBED6), shape)
            .semantics {
                contentDescription = title
            }
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 26.dp, end = 92.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = noFontPadding
            )
            Text(
                text = subtitle,
                color = Color(0xFFFFF5FA),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
                style = noFontPadding
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 22.dp)
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_forward_24),
                contentDescription = null,
                tint = Color(0xFFFF3A70)
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_sparkle_24),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 78.dp)
                .size(18.dp)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_sparkle_24),
            contentDescription = null,
            tint = Color(0xFFFFD36A),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 15.dp, bottom = 14.dp)
                .size(14.dp)
        )
    }
}

@Composable
fun HomeMetricCard(
    iconRes: Int,
    accent: Int,
    label: String,
    value: String,
    body: String?
) {
    val shape = RoundedCornerShape(8.dp)
    val accentColor = androidColor(accent)
    val borderColor = androidColor(HomeMetricCardBorder.softened(accent))
    val labelStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, borderColor, shape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(22.dp)
                    .padding(bottom = 5.dp)
            )
            Text(
                text = label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = labelStyle
            )
            Text(
                text = value,
                color = HomeInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
                style = labelStyle
            )
            if (!body.isNullOrEmpty()) {
                Text(
                    text = StudyTextCopy.compact(body, 18),
                    color = HomeMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                    style = labelStyle
                )
            }
        }
    }
}

private object HomeMetricCardBorder {
    fun softened(accent: Int): Int {
        return when (accent) {
            MainActivityBase.CORAL -> android.graphics.Color.rgb(255, 235, 243)
            MainActivityBase.TEAL -> android.graphics.Color.rgb(230, 250, 251)
            MainActivityBase.GOLD, android.graphics.Color.rgb(247, 159, 0) -> android.graphics.Color.rgb(255, 247, 220)
            MainActivityBase.BLUE, MainActivityBase.LILAC -> android.graphics.Color.rgb(242, 238, 255)
            else -> android.graphics.Color.rgb(248, 238, 245)
        }
    }
}

private fun androidColor(argb: Int): Color {
    return Color(argb.toLong() and 0xFFFFFFFFL)
}

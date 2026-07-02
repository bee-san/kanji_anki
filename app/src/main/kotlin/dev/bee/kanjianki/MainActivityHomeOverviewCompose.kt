@file:JvmName("MainActivityHomeOverviewCompose")

package dev.bee.kanjianki

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy

private val HomeInk: Color @Composable get() = KaniTheme.colors.ink
private val HomeMuted: Color @Composable get() = KaniTheme.colors.muted

internal fun homePrimaryCtaTestTag(label: String): String = "home-primary-cta-$label"

internal fun homeStudyCtaTestTag(title: String): String = "home-study-cta-$title"

internal val HomeStudyCtaMinHeight = 110.dp
internal val HomeStudyCtaCornerRadius = 30.dp
internal val HomeStudyCtaLabelStartPadding = 32.dp
internal val HomeStudyCtaLabelEndPadding = 124.dp
internal val HomeStudyCtaLabelVerticalPadding = 26.dp
internal val HomeStudyCtaArrowCircleSize = 60.dp
internal val HomeStudyCtaArrowEndPadding = 26.dp
internal val HomeStudyCtaTopSparkleSize = 20.dp
internal val HomeStudyCtaTopSparkleTopPadding = 16.dp
internal val HomeStudyCtaTopSparkleEndPadding = 112.dp
internal val HomeStudyCtaBottomSparkleSize = 16.dp
internal val HomeStudyCtaBottomSparkleStartPadding = 22.dp
internal val HomeStudyCtaBottomSparkleBottomPadding = 18.dp

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
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = HomeMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
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
fun HomePrimaryCta(
    label: String,
    color: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(HomeStudyCtaCornerRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(shape)
            .background(kaniColor(color))
            .testTag(homePrimaryCtaTestTag(label))
            .semantics {
                contentDescription = label
            }
            .clickable(
                role = Role.Button,
                onClick = {
                    withUiTrace("kani.button.home-sync-cta") {
                        onClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KaniTheme.colors.onCoral,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
fun HomeStudyCta(
    title: String,
    onClick: () -> Unit,
    dueCount: Int = 0,
) {
    val shape = RoundedCornerShape(HomeStudyCtaCornerRadius)
    val noFontPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    val dueLabel = if (dueCount > 0) "$dueCount ${HomeTextCopy.deckOverviewDueLabel()}" else null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        KaniTheme.colors.primary,
                        KaniTheme.colors.coral
                    )
                )
            )
            .border(2.dp, KaniTheme.colors.border, shape)
            .testTag(homeStudyCtaTestTag(title))
            .semantics {
                contentDescription = if (dueLabel != null) "$title, $dueLabel" else title
            }
            .clickable(
                role = Role.Button,
                onClick = {
                    withUiTrace("kani.button.home-study-cta") {
                        onClick()
                    }
                }
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = HomeStudyCtaLabelStartPadding,
                    top = HomeStudyCtaLabelVerticalPadding,
                    end = HomeStudyCtaLabelEndPadding,
                    bottom = HomeStudyCtaLabelVerticalPadding
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = KaniTheme.colors.onPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = noFontPadding
            )
            if (dueLabel != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.24f))
                ) {
                    Text(
                        text = dueLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = KaniTheme.colors.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = noFontPadding
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HomeStudyCtaArrowEndPadding)
                .size(HomeStudyCtaArrowCircleSize)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_forward_24),
                contentDescription = null,
                tint = KaniTheme.colors.primary
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_sparkle_24),
            contentDescription = null,
            tint = KaniTheme.colors.onPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = HomeStudyCtaTopSparkleTopPadding,
                    end = HomeStudyCtaTopSparkleEndPadding
                )
                .size(HomeStudyCtaTopSparkleSize)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_sparkle_24),
            contentDescription = null,
            tint = KaniTheme.colors.gold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = HomeStudyCtaBottomSparkleStartPadding,
                    bottom = HomeStudyCtaBottomSparkleBottomPadding
                )
                .size(HomeStudyCtaBottomSparkleSize)
        )
    }
}

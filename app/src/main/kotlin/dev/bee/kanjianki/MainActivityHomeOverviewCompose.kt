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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HomeInk = Color(0xFF2D1635)
private val HomeMuted = Color(0xFF6C5674)

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
fun HomePrimaryCta(
    label: String,
    color: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(shape)
            .background(Color(color.toLong() and 0xFFFFFFFFL))
            .semantics {
                contentDescription = label
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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

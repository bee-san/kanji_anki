@file:JvmName("MainActivitySettingsCategoryCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HEADER_COLLAPSED_BG = ComposeColor(0xFFFFF6FB)
private val HEADER_ICON_BG = ComposeColor(0xFFFFEDF6)
private val HEADER_COUNT_BG = ComposeColor(0xFFFFF2F8)

private const val HEADER_CORNER_RADIUS = 26
private const val HEADER_ICON_SIZE = 40
private const val HEADER_COUNT_RADIUS = 16
private const val HEADER_ICON_RADIUS = 16

@Composable
internal fun SettingsCategoryHeader(
    title: String,
    summary: String,
    iconRes: Int,
    iconTint: ComposeColor,
    borderColor: ComposeColor,
    expanded: Boolean,
    countText: String,
    titleColor: ComposeColor,
    summaryColor: ComposeColor,
    countColor: ComposeColor,
    contentDescription: String,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (expanded) ComposeColor.White else HEADER_COLLAPSED_BG,
        shape = RoundedCornerShape(HEADER_CORNER_RADIUS.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        val icon: Painter = painterResource(id = iconRes)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 14.dp, bottom = 16.dp)
                .testTag(settingsCategoryHeaderTestTag(title))
                .semantics {
                    this.contentDescription = contentDescription
                }
                .clickable(
                    role = Role.Button,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onToggle
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(HEADER_ICON_SIZE.dp)
                    .background(HEADER_ICON_BG, RoundedCornerShape(HEADER_ICON_RADIUS.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(iconTint)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary,
                    color = summaryColor,
                    fontSize = 14.sp
                )
            }

            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .padding(start = 10.dp, end = 8.dp),
                color = HEADER_COUNT_BG,
                shape = RoundedCornerShape(HEADER_COUNT_RADIUS.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    text = countText,
                    color = countColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }

            Image(
                painter = painterResource(id = R.drawable.ic_arrow_forward_24),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(if (expanded) 90f else 0f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(iconTint)
            )
        }
    }
}


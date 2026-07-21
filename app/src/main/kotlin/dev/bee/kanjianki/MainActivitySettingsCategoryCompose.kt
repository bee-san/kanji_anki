@file:JvmName("MainActivitySettingsCategoryCompose")

package dev.bee.kanjianki

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

private val HEADER_COLLAPSED_BG: ComposeColor @Composable get() = KaniTheme.colors.bg
private val HEADER_ICON_BG: ComposeColor @Composable get() = KaniTheme.colors.pill
private val HEADER_COUNT_BG: ComposeColor @Composable get() = KaniTheme.colors.pill

private const val HEADER_CORNER_RADIUS = 26
private const val HEADER_ICON_SIZE = 40
private const val HEADER_COUNT_RADIUS = 16
private const val HEADER_ICON_RADIUS = 16

private data class SettingsCardChromeModel(
    val title: String,
    val summary: String,
    val iconRes: Int,
    val iconTint: ComposeColor,
    val borderColor: ComposeColor,
    val countText: String,
    val titleColor: ComposeColor,
    val summaryColor: ComposeColor,
    val countColor: ComposeColor,
    val contentDescription: String,
    val testTag: String,
)

@Composable
private fun SettingsCardChrome(
    model: SettingsCardChromeModel,
    surfaceColor: ComposeColor,
    stateDescription: String? = null,
    onClick: () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor,
        shape = RoundedCornerShape(HEADER_CORNER_RADIUS.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, model.borderColor)
    ) {
        val icon: Painter = painterResource(id = model.iconRes)
        val contentModifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 14.dp, bottom = 16.dp)
            .testTag(model.testTag)
            .semantics {
                this.contentDescription = model.contentDescription
                if (stateDescription != null) {
                    this.stateDescription = stateDescription
                }
            }
            .clickable(
                role = Role.Button,
                onClick = { onClick() }
            )
        if (LocalDensity.current.fontScale >= 1.3f) {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsCardIcon(icon, model.iconTint)
                    Spacer(modifier = Modifier.weight(1f))
                    SettingsCardCount(model, accessibilityWidth = true)
                    trailingContent()
                }
                SettingsCardCopy(model, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsCardIcon(icon, model.iconTint)
                SettingsCardCopy(model, Modifier.weight(1f))
                SettingsCardCount(model)
                trailingContent()
            }
        }
    }
}

@Composable
private fun SettingsCardIcon(icon: Painter, tint: ComposeColor) {
    Box(
        modifier = Modifier
            .size(HEADER_ICON_SIZE.dp)
            .background(HEADER_ICON_BG, RoundedCornerShape(HEADER_ICON_RADIUS.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun SettingsCardCopy(
    model: SettingsCardChromeModel,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = model.title,
            modifier = Modifier.fillMaxWidth(),
            color = model.titleColor,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        if (model.summary.isNotBlank()) {
            Text(
                text = model.summary,
                modifier = Modifier.fillMaxWidth(),
                color = model.summaryColor,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun SettingsCardCount(
    model: SettingsCardChromeModel,
    accessibilityWidth: Boolean = false,
) {
    Surface(
        modifier = if (accessibilityWidth) {
            Modifier.width(152.dp)
        } else {
            Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(start = 10.dp, end = 8.dp)
        },
        color = HEADER_COUNT_BG,
        shape = RoundedCornerShape(HEADER_COUNT_RADIUS.dp),
        border = BorderStroke(1.dp, model.borderColor),
    ) {
        Text(
            text = model.countText,
            color = model.countColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .then(if (accessibilityWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

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
    testTagKey: String,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "settings-category-chevron"
    )
    SettingsCardChrome(
        model = SettingsCardChromeModel(
            title = title,
            summary = summary,
            iconRes = iconRes,
            iconTint = iconTint,
            borderColor = borderColor,
            countText = countText,
            titleColor = titleColor,
            summaryColor = summaryColor,
            countColor = countColor,
            contentDescription = contentDescription,
            testTag = settingsCategoryHeaderTestTag(testTagKey),
        ),
        surfaceColor = if (expanded) KaniTheme.colors.surface else HEADER_COLLAPSED_BG,
        stateDescription = SettingsTextCopy.categoryStateDescription(expanded),
        onClick = { withButtonTrace(title) { onToggle() } },
        trailingContent = {
            Image(
                painter = painterResource(id = R.drawable.ic_arrow_forward_24),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(chevronRotation),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(iconTint)
            )
        },
    )
}

internal fun settingsHubCardTestTag(routeKey: String): String {
    return "settings-hub-card-$routeKey"
}

@Composable
internal fun SettingsHubCard(
    card: SettingsHubCardModel,
) {
    SettingsCardChrome(
        model = SettingsCardChromeModel(
            title = card.title,
            summary = card.summary,
            iconRes = card.iconRes,
            iconTint = KaniTheme.colors.primary,
            borderColor = KaniTheme.colors.border,
            countText = card.panelCount,
            titleColor = KaniTheme.colors.plum,
            summaryColor = KaniTheme.colors.muted,
            countColor = KaniTheme.colors.primary,
            contentDescription = card.contentDescription,
            testTag = settingsHubCardTestTag(card.routeKey),
        ),
        surfaceColor = KaniTheme.colors.surface,
        onClick = { withButtonTrace(card.title) { card.onOpen.run() } },
    )
}

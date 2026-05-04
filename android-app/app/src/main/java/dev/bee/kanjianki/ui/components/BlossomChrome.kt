package dev.bee.kanjianki.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.ui.theme.BlossomApricot
import dev.bee.kanjianki.ui.theme.BlossomApricotSoft
import dev.bee.kanjianki.ui.theme.BlossomBg
import dev.bee.kanjianki.ui.theme.BlossomBgStrong
import dev.bee.kanjianki.ui.theme.BlossomDanger
import dev.bee.kanjianki.ui.theme.BlossomDangerSoft
import dev.bee.kanjianki.ui.theme.BlossomInk
import dev.bee.kanjianki.ui.theme.BlossomInkSoft
import dev.bee.kanjianki.ui.theme.BlossomLine
import dev.bee.kanjianki.ui.theme.BlossomLineStrong
import dev.bee.kanjianki.ui.theme.BlossomMint
import dev.bee.kanjianki.ui.theme.BlossomMintSoft
import dev.bee.kanjianki.ui.theme.BlossomMuted
import dev.bee.kanjianki.ui.theme.BlossomPink
import dev.bee.kanjianki.ui.theme.BlossomPinkSoft
import dev.bee.kanjianki.ui.theme.BlossomPinkStrong
import dev.bee.kanjianki.ui.theme.BlossomRose
import dev.bee.kanjianki.ui.theme.BlossomRoseSoft
import dev.bee.kanjianki.ui.theme.BlossomSurface
import dev.bee.kanjianki.ui.theme.BlossomViolet
import dev.bee.kanjianki.ui.theme.BlossomVioletSoft

enum class BlossomTone {
    PINK,
    VIOLET,
    MINT,
    APRICOT,
    ROSE,
    DANGER,
}

private fun toneStrong(tone: BlossomTone): Color =
    when (tone) {
        BlossomTone.PINK -> BlossomPinkStrong
        BlossomTone.VIOLET -> BlossomViolet
        BlossomTone.MINT -> BlossomMint
        BlossomTone.APRICOT -> BlossomApricot
        BlossomTone.ROSE -> BlossomRose
        BlossomTone.DANGER -> BlossomDanger
    }

private fun toneSoft(tone: BlossomTone): Color =
    when (tone) {
        BlossomTone.PINK -> BlossomPinkSoft
        BlossomTone.VIOLET -> BlossomVioletSoft
        BlossomTone.MINT -> BlossomMintSoft
        BlossomTone.APRICOT -> BlossomApricotSoft
        BlossomTone.ROSE -> BlossomRoseSoft
        BlossomTone.DANGER -> BlossomDangerSoft
    }

@Composable
fun BlossomBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BlossomBg,
                        BlossomBgStrong,
                        Color(0xFFFFF0F5),
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = BlossomPink.copy(alpha = 0.16f),
                radius = size.minDimension * 0.22f,
                center = Offset(size.width * 0.1f, size.height * 0.08f),
            )
            drawCircle(
                color = BlossomRose.copy(alpha = 0.13f),
                radius = size.minDimension * 0.18f,
                center = Offset(size.width * 0.88f, size.height * 0.14f),
            )
            drawCircle(
                color = BlossomViolet.copy(alpha = 0.08f),
                radius = size.minDimension * 0.2f,
                center = Offset(size.width * 0.82f, size.height * 0.82f),
            )
            drawCircle(
                color = BlossomApricot.copy(alpha = 0.07f),
                radius = size.minDimension * 0.16f,
                center = Offset(size.width * 0.22f, size.height * 0.92f),
            )

            val petals = listOf(
                Offset(size.width * 0.14f, size.height * 0.14f),
                Offset(size.width * 0.32f, size.height * 0.08f),
                Offset(size.width * 0.52f, size.height * 0.11f),
                Offset(size.width * 0.72f, size.height * 0.09f),
                Offset(size.width * 0.88f, size.height * 0.16f),
                Offset(size.width * 0.82f, size.height * 0.58f),
            )
            val petalColors = listOf(
                BlossomRose.copy(alpha = 0.5f),
                Color(0xFFFFB3CE).copy(alpha = 0.55f),
                Color(0xFFFCAAC5).copy(alpha = 0.52f),
                Color(0xFFFF9CC0).copy(alpha = 0.44f),
            )
            petals.forEachIndexed { index, center ->
                drawCircle(
                    color = petalColors[index % petalColors.size],
                    radius = if (index % 2 == 0) 6.dp.toPx() else 8.dp.toPx(),
                    center = center,
                )
            }
        }
        content()
    }
}

@Composable
fun BlossomCard(
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.PINK,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = BlossomSurface.copy(alpha = 0.92f),
        contentColor = BlossomInk,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlossomLine),
        shadowElevation = 14.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.96f),
                            toneSoft(tone).copy(alpha = 0.9f),
                        ),
                    ),
                )
                .padding(contentPadding),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun BlossomHeroCard(
    kicker: String,
    title: String,
    body: String,
    @DrawableRes plushieRes: Int,
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.PINK,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    BlossomCard(
        modifier = modifier,
        tone = tone,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionEyebrow(kicker)
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = BlossomInk,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BlossomMuted,
                )
                footer()
            }
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(1.dp, toneSoft(tone).copy(alpha = 0.65f)),
            ) {
                Image(
                    painter = painterResource(plushieRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(112.dp)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun SectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = BlossomPinkStrong,
    )
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.PINK,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, toneSoft(tone).copy(alpha = 0.9f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = toneStrong(tone),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.APRICOT,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, toneSoft(tone).copy(alpha = 0.9f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = toneStrong(tone),
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.PINK,
    supporting: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, toneSoft(tone).copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = BlossomInkSoft,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = toneStrong(tone),
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = BlossomMuted,
                )
            }
        }
    }
}

@Composable
fun DetailLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = BlossomInkSoft,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = BlossomInk,
        )
    }
}

@Composable
fun BlossomTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.ROSE,
    selected: Boolean = false,
) {
    val fill = if (selected) toneSoft(tone).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.78f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = fill,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) toneStrong(tone).copy(alpha = 0.45f) else BlossomLineStrong,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) toneStrong(tone) else BlossomInkSoft,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlossomTagFlow(
    values: List<String>,
    modifier: Modifier = Modifier,
    tone: BlossomTone = BlossomTone.ROSE,
    emptyLabel: String = "none yet",
) {
    val chips = values.filter { it.isNotBlank() }.ifEmpty { listOf(emptyLabel) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            BlossomTag(
                text = chip,
                tone = tone,
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    body: String,
    @DrawableRes plushieRes: Int,
    modifier: Modifier = Modifier,
) {
    BlossomCard(
        modifier = modifier,
        tone = BlossomTone.ROSE,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(plushieRes),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.72f))
                    .padding(8.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = BlossomInk,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BlossomMuted,
                )
            }
        }
    }
}

@Composable
fun BlossomTopNav(
    items: List<BlossomNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BlossomCard(
        modifier = modifier,
        tone = BlossomTone.PINK,
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                val selected = selectedKey == item.key
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (selected) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        toneSoft(item.tone).copy(alpha = 0.98f),
                                        Color.White.copy(alpha = 0.9f),
                                    ),
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.85f),
                                        Color.White.copy(alpha = 0.68f),
                                    ),
                                )
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) toneStrong(item.tone).copy(alpha = 0.35f) else BlossomLine,
                            shape = RoundedCornerShape(22.dp),
                        )
                        .clickable { onSelect(item.key) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) toneStrong(item.tone) else BlossomInkSoft,
                    )
                    Text(
                        text = item.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = BlossomMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

data class BlossomNavItem(
    val key: String,
    val label: String,
    val caption: String,
    val tone: BlossomTone,
)

@Composable
fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = BlossomPinkStrong,
    contentColor = Color.White,
    disabledContainerColor = BlossomPinkSoft.copy(alpha = 0.6f),
    disabledContentColor = Color.White.copy(alpha = 0.75f),
)

@Composable
fun secondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = BlossomVioletSoft.copy(alpha = 0.95f),
    contentColor = BlossomViolet,
    disabledContainerColor = BlossomVioletSoft.copy(alpha = 0.55f),
    disabledContentColor = BlossomViolet.copy(alpha = 0.7f),
)

@Composable
fun warmButtonColors() = ButtonDefaults.buttonColors(
    containerColor = BlossomApricotSoft.copy(alpha = 0.95f),
    contentColor = BlossomApricot,
    disabledContainerColor = BlossomApricotSoft.copy(alpha = 0.55f),
    disabledContentColor = BlossomApricot.copy(alpha = 0.7f),
)

@Composable
fun ghostButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color.White.copy(alpha = 0.78f),
    contentColor = BlossomInk,
    disabledContainerColor = Color.White.copy(alpha = 0.58f),
    disabledContentColor = BlossomInkSoft.copy(alpha = 0.7f),
)

@Composable
fun blossomTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BlossomPinkStrong,
    unfocusedBorderColor = BlossomLineStrong,
    focusedLabelColor = BlossomPinkStrong,
    unfocusedLabelColor = BlossomInkSoft,
    cursorColor = BlossomPinkStrong,
    focusedContainerColor = Color.White.copy(alpha = 0.88f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.78f),
    disabledContainerColor = Color.White.copy(alpha = 0.56f),
    focusedTextColor = BlossomInk,
    unfocusedTextColor = BlossomInk,
    focusedSupportingTextColor = BlossomMuted,
    unfocusedSupportingTextColor = BlossomMuted,
)

@Composable
fun blossomSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = BlossomPinkStrong,
    checkedBorderColor = BlossomPinkStrong,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = BlossomRoseSoft.copy(alpha = 0.9f),
    uncheckedBorderColor = BlossomLineStrong,
)

@Composable
fun SpacerPetal(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(2.dp))
}

@Composable
fun DividerPetal(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BlossomLineStrong,
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
fun AccentDot(
    tone: BlossomTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(toneStrong(tone)),
    )
}

@Composable
fun LabeledStat(
    label: String,
    value: String,
    tone: BlossomTone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentDot(tone = tone)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            color = BlossomInkSoft,
        )
    }
}

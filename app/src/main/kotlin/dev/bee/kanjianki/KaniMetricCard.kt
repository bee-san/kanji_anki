package dev.bee.kanjianki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// This stable named-argument Compose API keeps metric call sites readable; grouping its
// modifier, action, or accessibility fields would only replace that clarity with a bag type.
@Suppress("kotlin:S107")
@Composable
internal fun KaniMetricCard(
    iconRes: Int,
    label: String,
    value: String,
    delta: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescriptionPrefix: String? = null,
    compactValue: Boolean = false,
    compactLayout: Boolean = false,
) {
    val description = listOfNotNull(contentDescriptionPrefix, label, value, delta).joinToString(", ")
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)),
        shape = KaniUiTokens.LeafShape,
        color = accent.copy(alpha = if (KaniTheme.colors.isDark) .18f else .10f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (compactLayout) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 6.dp else 8.dp),
            ) {
                if (compactLayout) {
                    Icon(
                        painterResource(iconRes), contentDescription = null, tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Surface(shape = CircleShape, color = accent.copy(alpha = .18f)) {
                        Icon(
                            painterResource(iconRes), contentDescription = null, tint = accent,
                            modifier = Modifier.padding(7.dp).size(18.dp),
                        )
                    }
                }
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = KaniTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value,
                style = when {
                    compactValue -> MaterialTheme.typography.titleSmall
                    value.length >= 6 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.headlineSmall
                },
                color = KaniTheme.colors.ink,
                fontWeight = FontWeight.Bold,
                maxLines = if (compactValue) 2 else 1,
                softWrap = compactValue,
                overflow = TextOverflow.Ellipsis,
                autoSize = if (compactValue) {
                    TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 1.sp)
                } else {
                    null
                },
            )
            delta?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = accent,
                    maxLines = if (compactLayout) 2 else Int.MAX_VALUE,
                    overflow = if (compactLayout) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            }
        }
    }
}

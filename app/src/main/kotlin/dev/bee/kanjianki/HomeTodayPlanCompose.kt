package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeTodayPlanCard(model: HomeTodayPlanModel) {
    val clickableModifier = if (model.onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClick = {
                withUiTrace("kani.button.home-today-plan") {
                    model.onClick.invoke()
                }
            }
        )
    } else {
        Modifier
    }
    val contentDescriptionText = buildString {
        append(model.title)
        if (model.summary.isNotBlank()) {
            append(" · ")
            append(model.summary)
        }
        model.details.forEach { detail ->
            if (detail.isNotBlank()) {
                append(" · ")
                append(detail)
            }
        }
        if (model.actionLabel != null) {
            append(" · ")
            append(model.actionLabel)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeTodayPlanTestTag())
            .semantics { contentDescription = contentDescriptionText }
            .then(clickableModifier),
        shape = RoundedCornerShape(18.dp),
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = model.title,
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = model.summary,
                color = KaniTheme.colors.ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (model.details.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    model.details.forEach { detail ->
                        Text(
                            text = detail,
                            color = KaniTheme.colors.greyText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (model.actionLabel != null) {
                Text(
                    text = model.actionLabel,
                    color = KaniTheme.colors.coral,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

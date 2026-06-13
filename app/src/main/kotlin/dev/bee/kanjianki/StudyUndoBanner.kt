package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.StudyReviewButtonCopy

object StudyUndoBannerTestTags {
    const val BANNER = "study-undo-banner"
}

@Composable
internal fun StudyUndoBanner(
    undoMessage: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StudyUndoBannerTestTags.BANNER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = undoMessage,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        StudySecondaryActionButton(
            label = StudyReviewButtonCopy.undoLabel(),
            onClick = onUndo,
            minHeight = 48.dp,
        )
    }
}

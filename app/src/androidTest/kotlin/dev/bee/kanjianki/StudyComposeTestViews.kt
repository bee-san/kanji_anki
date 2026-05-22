@file:JvmName("StudyComposeTestViews")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import dev.bee.kanjianki.core.RecordsSchedulerModels

internal fun learningPanelTestView(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession,
): View {
    val model = activity.learningPanelModel(session)
    return ComposeView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, activity.dp(12), 0, activity.dp(10))
        }
        setContent {
            MaterialTheme {
                StudyAnswerPanel(model)
            }
        }
    }
}

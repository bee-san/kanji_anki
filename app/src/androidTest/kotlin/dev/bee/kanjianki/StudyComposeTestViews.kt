@file:JvmName("StudyComposeTestViews")

package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy

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

internal fun heroKanjiPanelTestView(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession,
): View {
    val heroPanel = FlashcardHeroPanelModel(
        if (StudyTaskCopy.isWordReadingTask(session)) StudyTextCopy.wordPrompt(session) else session.item.kanji,
        if (StudyTaskCopy.isWordReadingTask(session)) 44 else 116,
        if (StudyTaskCopy.isFontRecognitionTask(session)) activity.randomFontVariantTypeface() else Typeface.DEFAULT
    )
    return ComposeView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            activity.dp(210)
        ).apply {
            setMargins(0, activity.dp(16), 0, 0)
        }
        setContent {
            MaterialTheme {
                FlashcardHeroPanel(heroPanel)
            }
        }
    }
}

internal fun flashcardAnswerPanelTestView(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession,
): View {
    val model = activity.flashcardAnswerPanelModel(session)
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

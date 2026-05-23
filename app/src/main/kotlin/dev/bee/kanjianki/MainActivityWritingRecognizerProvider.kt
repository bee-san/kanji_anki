package dev.bee.kanjianki

import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer
import dev.bee.kanjianki.study.WritingRecognizer

internal class MainActivityWritingRecognizerProvider(private val activity: MainActivityBase) {
    fun currentWritingRecognizer(): WritingRecognizer? {
        MainActivityRuntimeOverrides.writingRecognizer?.let { return it }
        activity.writingRecognizer?.let { return it }
        return try {
            activity.writingRecognizer =
                MainActivityRuntimeOverrides.writingRecognizerFactory?.create(activity.io)
                    ?: MlKitJapaneseWritingRecognizer(activity.io)
            activity.writingRecognizer
        } catch (_: RuntimeException) {
            null
        }
    }
}

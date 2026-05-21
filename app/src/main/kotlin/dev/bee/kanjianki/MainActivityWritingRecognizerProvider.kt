package dev.bee.kanjianki

import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer
import dev.bee.kanjianki.study.WritingRecognizer

internal class MainActivityWritingRecognizerProvider(private val activity: MainActivityBase) {
    fun currentWritingRecognizer(): WritingRecognizer? {
        MainActivityBase.writingRecognizerForTests?.let { return it }
        activity.writingRecognizer?.let { return it }
        return try {
            activity.writingRecognizer =
                MainActivityBase.writingRecognizerFactoryForTests?.create(activity.io)
                    ?: MlKitJapaneseWritingRecognizer(activity.io)
            activity.writingRecognizer
        } catch (_: RuntimeException) {
            null
        }
    }
}

package dev.bee.kanjianki.core.study

class RecognitionCandidate(
    text: String?,
    @JvmField val score: Float?,
) {
    @JvmField val text: String = text ?: ""
}

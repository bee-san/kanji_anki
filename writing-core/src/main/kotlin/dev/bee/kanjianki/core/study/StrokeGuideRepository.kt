package dev.bee.kanjianki.core.study

fun interface StrokeGuideRepository {
    fun guideFor(kanji: String?): StrokeGuide?
}

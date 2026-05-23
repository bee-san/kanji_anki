package dev.bee.kanjianki.core.study

import java.util.Optional

fun interface StrokeGuideProvider {
    fun guideFor(kanji: String?): Optional<StrokeGuide>
}

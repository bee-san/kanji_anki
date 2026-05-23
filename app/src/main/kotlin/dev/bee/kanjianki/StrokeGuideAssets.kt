package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.StrokeGuideParser
import java.io.InputStreamReader

internal object StrokeGuideAssets {
    @JvmStatic
    fun load(context: Context): Map<String, StrokeGuide> {
        return try {
            context.resources.openRawResource(R.raw.kanji_strokes).use { input ->
                InputStreamReader(input).use { reader ->
                    StrokeGuideParser.parse(reader)
                }
            }
        } catch (_: Exception) {
            HashMap()
        }
    }
}

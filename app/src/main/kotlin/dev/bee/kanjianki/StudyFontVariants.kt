package dev.bee.kanjianki

import android.content.Context
import android.graphics.Typeface
import java.security.SecureRandom

internal object StudyFontVariants {
    private val fontRandom = SecureRandom()

    @JvmStatic
    fun random(context: Context): Typeface {
        return forVariant(context, fontRandom.nextInt(3))
    }

    /** Stable across process restarts for the same persisted core-review count. */
    @JvmStatic
    fun deterministic(context: Context, kanji: String?, coreReviewCount: Int): Typeface {
        return forVariant(context, variantIndex(kanji, coreReviewCount))
    }

    @JvmStatic
    fun variantIndex(kanji: String?, coreReviewCount: Int): Int {
        val seed = 31 * kanji.orEmpty().hashCode() + coreReviewCount.coerceAtLeast(0)
        return Math.floorMod(seed, 3)
    }

    @JvmStatic
    fun forVariant(context: Context, variant: Int): Typeface {
        return when (variant) {
            0 -> fontResource(context, R.font.cinecaption_regular, Typeface.DEFAULT)
            1 -> fontResource(context, R.font.dotgothic16_regular, Typeface.MONOSPACE)
            else -> fontResource(context, R.font.reggae_one_regular, Typeface.SERIF)
        }
    }

    private fun fontResource(context: Context, fontRes: Int, fallback: Typeface): Typeface {
        return try {
            context.resources.getFont(fontRes)
        } catch (_: RuntimeException) {
            fallback
        }
    }
}

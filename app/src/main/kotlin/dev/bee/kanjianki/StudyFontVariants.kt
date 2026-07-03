package dev.bee.kanjianki

import android.content.Context
import android.graphics.Typeface
import java.util.Random

internal object StudyFontVariants {
    // Cosmetic font-variant pick; a plain Random avoids SecureRandom's entropy
    // blocking risk.
    @Suppress("java:S2245")
    private val fontRandom = Random()

    @JvmStatic
    fun random(context: Context): Typeface {
        return forVariant(context, fontRandom.nextInt(3))
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

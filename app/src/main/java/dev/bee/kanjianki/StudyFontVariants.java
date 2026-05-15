package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Typeface;

import java.util.concurrent.ThreadLocalRandom;

final class StudyFontVariants {
    private StudyFontVariants() {
    }

    static Typeface random(Context context) {
        return forVariant(context, ThreadLocalRandom.current().nextInt(3));
    }

    static Typeface forVariant(Context context, int variant) {
        switch (variant) {
            case 0:
                return fontResource(context, R.font.cinecaption_regular, Typeface.DEFAULT);
            case 1:
                return fontResource(context, R.font.dotgothic16_regular, Typeface.MONOSPACE);
            default:
                return fontResource(context, R.font.reggae_one_regular, Typeface.SERIF);
        }
    }

    private static Typeface fontResource(Context context, int fontRes, Typeface fallback) {
        try {
            return context.getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}

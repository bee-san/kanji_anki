package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Typeface;

import java.util.concurrent.ThreadLocalRandom;

final class StudyFontVariants {
    private StudyFontVariants() {
    }

    static Typeface random(Context context) {
        switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0:
                return Typeface.SERIF;
            case 1:
                return Typeface.DEFAULT;
            case 2:
                return Typeface.MONOSPACE;
            case 3:
                return fontResource(context, R.font.klee_one_regular, Typeface.DEFAULT);
            default:
                return fontResource(context, R.font.kaisei_tokumin_regular, Typeface.SERIF);
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

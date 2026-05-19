package dev.bee.kanjianki.core;

public final class StudyLayoutPolicy {
    private StudyLayoutPolicy() {
    }

    public static int writingPadHeightDp(int screenHeightDp) {
        if (screenHeightDp < 700) {
            return 300;
        }
        if (screenHeightDp < 820) {
            return 340;
        }
        return 390;
    }
}

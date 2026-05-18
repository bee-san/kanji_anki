package dev.bee.kanjianki.updatecore;

import java.io.File;

public final class UpdateCacheFilePolicy {
    public static final String DEFAULT_APK_NAME = "kani-update.apk";

    private UpdateCacheFilePolicy() {
    }

    public static String safeFileName(String name) {
        String safe = new File(name == null ? "" : name).getName();
        return safe.isEmpty() ? DEFAULT_APK_NAME : safe;
    }
}

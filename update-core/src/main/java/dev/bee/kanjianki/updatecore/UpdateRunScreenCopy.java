package dev.bee.kanjianki.updatecore;

public final class UpdateRunScreenCopy {
    private UpdateRunScreenCopy() {
    }

    public static Copy forRun(boolean cachedPending) {
        if (cachedPending) {
            return new Copy(
                    "Starting installer",
                    "Using the verified APK already cached by Kani.",
                    "Preparing verified APK"
            );
        }
        return new Copy(
                "Checking release",
                "Downloading metadata and verifying assets.",
                "Checking GitHub Releases"
        );
    }

    public record Copy(String title, String body, String progressLabel) {
    }
}

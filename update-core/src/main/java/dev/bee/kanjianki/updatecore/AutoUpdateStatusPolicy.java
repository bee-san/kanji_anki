package dev.bee.kanjianki.updatecore;

public final class AutoUpdateStatusPolicy {
    public static final String DEFAULT_LAST_RESULT = "No automatic update check has run yet.";

    private AutoUpdateStatusPolicy() {
    }

    public static StatusFields normalize(
            boolean enabled,
            long lastCheckAtMillis,
            String lastResult,
            String lastVersion,
            String pendingApkName,
            String pendingMessage
    ) {
        return new StatusFields(
                enabled,
                lastCheckAtMillis,
                text(lastResult),
                text(lastVersion),
                text(pendingApkName),
                text(pendingMessage)
        );
    }

    public static boolean hasPendingUpdate(String pendingApkName) {
        return !text(pendingApkName).isEmpty();
    }

    public static String text(String value) {
        return value == null ? "" : value;
    }

    public record StatusFields(
            boolean enabled,
            long lastCheckAtMillis,
            String lastResult,
            String lastVersion,
            String pendingApkName,
            String pendingMessage
    ) {
    }
}

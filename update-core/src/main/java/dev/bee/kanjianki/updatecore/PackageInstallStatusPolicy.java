package dev.bee.kanjianki.updatecore;

public final class PackageInstallStatusPolicy {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_PENDING_USER_ACTION = -1;
    public static final int ANDROID_S_API_LEVEL = 31;
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AUTOMATIC = "AUTOMATIC";
    public static final String SOURCE_CACHED = "CACHED";

    private PackageInstallStatusPolicy() {
    }

    public static InstallCallback mapInstallStatus(int status, String message) {
        if (status == STATUS_SUCCESS) {
            return new InstallCallback(false, true, "Install finished.");
        }
        if (status == STATUS_PENDING_USER_ACTION) {
            return new InstallCallback(true, false, "Android needs confirmation to finish installing.");
        }
        String suffix = message == null || message.trim().isEmpty() ? "" : ": " + message.trim();
        return new InstallCallback(false, false, "Install failed" + suffix + ".");
    }

    public static String sourceNameOrDefault(String raw) {
        if (SOURCE_MANUAL.equals(raw) || SOURCE_AUTOMATIC.equals(raw) || SOURCE_CACHED.equals(raw)) {
            return raw;
        }
        return SOURCE_AUTOMATIC;
    }

    public static boolean shouldLaunchInstallConfirmation(String sourceName) {
        String normalized = sourceNameOrDefault(sourceName);
        return SOURCE_MANUAL.equals(normalized) || SOURCE_CACHED.equals(normalized);
    }

    public static boolean shouldAllowInstallerWithoutExtraUserAction(int requestedSdk, int runtimeSdk) {
        return requestedSdk >= ANDROID_S_API_LEVEL && runtimeSdk >= ANDROID_S_API_LEVEL;
    }

    public static final class InstallCallback {
        public final boolean pendingUserAction;
        public final boolean success;
        public final String message;

        private InstallCallback(boolean pendingUserAction, boolean success, String message) {
            this.pendingUserAction = pendingUserAction;
            this.success = success;
            this.message = message;
        }

        public boolean pendingUserAction() {
            return pendingUserAction;
        }

        public boolean success() {
            return success;
        }

        public String message() {
            return message;
        }
    }
}

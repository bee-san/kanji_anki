package dev.bee.kanjianki.updatecore;

public final class PackageInstallStatusPolicy {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_PENDING_USER_ACTION = -1;

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

    public static final class InstallCallback {
        public final boolean pendingUserAction;
        public final boolean success;
        public final String message;

        private InstallCallback(boolean pendingUserAction, boolean success, String message) {
            this.pendingUserAction = pendingUserAction;
            this.success = success;
            this.message = message;
        }
    }
}

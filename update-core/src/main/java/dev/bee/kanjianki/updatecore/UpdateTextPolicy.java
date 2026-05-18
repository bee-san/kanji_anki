package dev.bee.kanjianki.updatecore;

public final class UpdateTextPolicy {
    public static final String DEFAULT_PENDING_UPDATE_MESSAGE =
            "Open Kani to finish installing the verified update.";

    private UpdateTextPolicy() {
    }

    public static String readableMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return error.getClass().getSimpleName();
    }

    public static String notificationBody(String version, String message) {
        String body = version == null || version.isEmpty()
                ? message
                : "Version " + version.replaceFirst("^v", "") + " is verified and ready.";
        if (body == null || body.trim().isEmpty()) {
            body = DEFAULT_PENDING_UPDATE_MESSAGE;
        }
        return body;
    }
}

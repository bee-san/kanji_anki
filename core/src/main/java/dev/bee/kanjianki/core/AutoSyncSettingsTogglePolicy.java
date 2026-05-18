package dev.bee.kanjianki.core;

public final class AutoSyncSettingsTogglePolicy {
    public static final String ENABLED_MESSAGE = "Daily Anki sync turned on.";
    public static final String DISABLED_MESSAGE = "Daily Anki sync turned off.";

    private AutoSyncSettingsTogglePolicy() {
    }

    public static ToggleResult enable() {
        return new ToggleResult(true, ENABLED_MESSAGE);
    }

    public static ToggleResult disable() {
        return new ToggleResult(false, DISABLED_MESSAGE);
    }

    public record ToggleResult(boolean enabled, String message) {
    }
}

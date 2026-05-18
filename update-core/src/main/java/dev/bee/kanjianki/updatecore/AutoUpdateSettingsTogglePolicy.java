package dev.bee.kanjianki.updatecore;

public final class AutoUpdateSettingsTogglePolicy {
    public static final String ENABLED_MESSAGE = "Automatic updates turned on.";
    public static final String DISABLED_MESSAGE = "Automatic updates turned off.";

    private AutoUpdateSettingsTogglePolicy() {
    }

    public static ToggleResult toggle(boolean currentlyEnabled) {
        boolean enabled = !currentlyEnabled;
        return new ToggleResult(enabled, enabled ? ENABLED_MESSAGE : DISABLED_MESSAGE);
    }

    public record ToggleResult(boolean enabled, String message) {
    }
}

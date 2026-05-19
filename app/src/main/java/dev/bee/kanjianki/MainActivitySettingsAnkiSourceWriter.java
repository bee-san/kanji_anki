package dev.bee.kanjianki;

final class MainActivitySettingsAnkiSourceWriter implements SettingsWriteActions.SettingWriter {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceWriter(MainActivitySettings activity) {
        this.activity = activity;
    }

    @Override
    public void putIntSetting(String key, int value) {
        activity.store.putIntSetting(key, value);
    }

    @Override
    public void putStringSetting(String key, String value) {
        activity.store.putStringSetting(key, value);
    }

    @Override
    public void putDoubleSetting(String key, double value) {
        activity.store.putDoubleSetting(key, value);
    }
}

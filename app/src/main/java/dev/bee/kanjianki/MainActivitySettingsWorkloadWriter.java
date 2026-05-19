package dev.bee.kanjianki;

final class MainActivitySettingsWorkloadWriter implements SettingsWriteActions.WorkloadSettingsWriter {
    private final MainActivitySettings activity;

    MainActivitySettingsWorkloadWriter(MainActivitySettings activity) {
        this.activity = activity;
    }

    @Override
    public void saveAdaptiveLoadMode(String mode) {
        activity.store.saveAdaptiveLoadMode(mode);
    }

    @Override
    public void saveAdaptiveLoadWorkPercent(int workloadPercent) {
        activity.store.saveAdaptiveLoadWorkPercent(workloadPercent);
    }

    @Override
    public void saveAdaptiveLoadMaxItems(int maxItems) {
        activity.store.saveAdaptiveLoadMaxItems(maxItems);
    }
}

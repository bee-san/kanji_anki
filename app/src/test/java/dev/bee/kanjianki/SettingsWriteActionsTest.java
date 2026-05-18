package dev.bee.kanjianki;

import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;
import dev.bee.kanjianki.sync.SyncSettings;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SettingsWriteActionsTest {
    @Test
    public void saveLadderThresholdsWritesPrimaryAndCompatibilityKeys() {
        Map<String, Integer> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveLadderThresholds(
                StudyLadderThresholdPolicy.saveRequest("21", "3"),
                settings::put
        );

        assertEquals(4, settings.size());
        assertEquals(Integer.valueOf(21), settings.get(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY));
        assertEquals(Integer.valueOf(3), settings.get(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY));
        assertEquals(Integer.valueOf(3), settings.get(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY));
        assertEquals(Integer.valueOf(3), settings.get(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY));
    }

    @Test
    public void saveLadderThresholdsIgnoresInvalidRequests() {
        Map<String, Integer> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveLadderThresholds(
                StudyLadderThresholdPolicy.saveRequest("0", "3"),
                settings::put
        );

        assertTrue(settings.isEmpty());
    }
}

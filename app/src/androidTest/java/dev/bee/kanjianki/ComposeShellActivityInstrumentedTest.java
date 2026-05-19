package dev.bee.kanjianki;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public final class ComposeShellActivityInstrumentedTest {
    @Test
    public void launchSetsTheComposeShellTitle() {
        try (ActivityScenario<ComposeShellActivity> scenario = ActivityScenario.launch(ComposeShellActivity.class)) {
            scenario.onActivity(activity -> assertEquals("Compose shell", activity.getTitle().toString()));
        }
    }
}

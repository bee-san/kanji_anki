package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoUpdateRunPolicyTest {
    @Test
    public void enabledAutoUpdateWithoutPendingInstallShouldRun() {
        assertTrue(AutoUpdateRunPolicy.shouldRun(true, false));
    }

    @Test
    public void disabledAutoUpdateShouldNotRun() {
        assertFalse(AutoUpdateRunPolicy.shouldRun(false, false));
    }

    @Test
    public void pendingInstallShouldNotRunAnotherCheck() {
        assertFalse(AutoUpdateRunPolicy.shouldRun(true, true));
    }
}

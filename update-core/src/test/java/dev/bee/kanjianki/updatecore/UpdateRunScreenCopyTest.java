package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class UpdateRunScreenCopyTest {
    @Test
    public void cachedPendingRunPreservesInstallerCopy() {
        UpdateRunScreenCopy.Copy copy = UpdateRunScreenCopy.forRun(true);

        assertEquals("Starting installer", copy.title());
        assertEquals("Using the verified APK already cached by Kani.", copy.body());
        assertEquals("Preparing verified APK", copy.progressLabel());
    }

    @Test
    public void manualRunPreservesReleaseCheckCopy() {
        UpdateRunScreenCopy.Copy copy = UpdateRunScreenCopy.forRun(false);

        assertEquals("Checking release", copy.title());
        assertEquals("Downloading metadata and verifying assets.", copy.body());
        assertEquals("Checking GitHub Releases", copy.progressLabel());
    }
}

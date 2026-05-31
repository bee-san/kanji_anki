package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class UpdateCacheFilePolicyTest {
    @Test
    public void safeFileNameStripsPathTraversal() {
        assertEquals("kani-0.4.3.apk", UpdateCacheFilePolicy.safeFileName("../release/kani-0.4.3.apk"));
        assertEquals("kani-0.4.3.apk", UpdateCacheFilePolicy.safeFileName("../../kani-0.4.3.apk"));
    }

    @Test
    public void safeFileNameDefaultsMissingNames() {
        assertEquals(UpdateCacheFilePolicy.DEFAULT_APK_NAME, UpdateCacheFilePolicy.safeFileName(""));
        assertEquals(UpdateCacheFilePolicy.DEFAULT_APK_NAME, UpdateCacheFilePolicy.safeFileName(null));
    }
}

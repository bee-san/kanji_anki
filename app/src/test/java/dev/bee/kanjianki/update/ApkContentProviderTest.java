package dev.bee.kanjianki.update;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ApkContentProviderTest {
    @Test
    public void contentResolverOpensCachedApkReadOnly() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File apk = new File(new File(context.getCacheDir(), "updates"), "kani-test.apk");
        if (!apk.getParentFile().exists()) {
            assertTrue(apk.getParentFile().mkdirs());
        }
        try (FileOutputStream output = new FileOutputStream(apk)) {
            output.write(new byte[]{1, 2, 3});
        }

        Uri uri = ApkContentProvider.uriFor(context, apk.getName());
        assertEquals("application/vnd.android.package-archive", context.getContentResolver().getType(uri));
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
            assertNotNull(descriptor);
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void contentResolverRejectsWriteMode() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getContentResolver().openFileDescriptor(ApkContentProvider.uriFor(context, "kani-test.apk"), "w");
    }

    @Test
    public void providerDoesNotSupportMutatingOperations() {
        ApkContentProvider provider = new ApkContentProvider();
        assertNull(provider.query(Uri.EMPTY, null, null, null, null));
        assertNull(provider.insert(Uri.EMPTY, null));
        assertEquals(0, provider.delete(Uri.EMPTY, null, null));
        assertEquals(0, provider.update(Uri.EMPTY, null, null, null));
    }
}

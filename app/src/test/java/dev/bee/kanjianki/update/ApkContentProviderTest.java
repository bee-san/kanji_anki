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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ApkContentProviderTest {
    @Test
    public void contentResolverReturnsApkMimeType() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File apk = createCachedApk(context);

        Uri uri = ApkContentProvider.uriFor(context, apk.getName());
        assertEquals("application/vnd.android.package-archive", context.getContentResolver().getType(uri));
    }

    @Test
    public void contentResolverOpensCachedApkReadOnly() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File apk = createCachedApk(context);

        Uri uri = ApkContentProvider.uriFor(context, apk.getName());
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
    public void providerDoesNotSupportQueries() {
        ApkContentProvider provider = new ApkContentProvider();

        assertNull(provider.query(Uri.EMPTY, null, null, null, null));
    }

    @Test
    public void providerDoesNotSupportInserts() {
        ApkContentProvider provider = new ApkContentProvider();

        assertNull(provider.insert(Uri.EMPTY, null));
    }

    @Test
    public void providerDoesNotSupportDeletes() {
        ApkContentProvider provider = new ApkContentProvider();

        assertEquals(0, provider.delete(Uri.EMPTY, null, null));
    }

    @Test
    public void providerDoesNotSupportUpdates() {
        ApkContentProvider provider = new ApkContentProvider();

        assertEquals(0, provider.update(Uri.EMPTY, null, null, null));
    }

    private static File createCachedApk(Context context) throws IOException {
        File apk = new File(new File(context.getCacheDir(), "updates"), "kani-test.apk");
        File parent = apk.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Could not create update cache directory");
        }
        try (FileOutputStream output = new FileOutputStream(apk)) {
            output.write(new byte[]{1, 2, 3});
        }
        return apk;
    }
}

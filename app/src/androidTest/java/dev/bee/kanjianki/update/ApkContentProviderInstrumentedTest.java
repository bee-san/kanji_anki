package dev.bee.kanjianki.update;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public final class ApkContentProviderInstrumentedTest {
    private Context context;
    private File updatesDir;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        updatesDir = new File(context.getCacheDir(), "updates");
        updatesDir.mkdirs();
    }

    @After
    public void tearDown() {
        deleteRecursively(updatesDir);
        new File(context.getCacheDir(), "outside.apk").delete();
    }

    @Test
    public void opensCachedApkReadOnly() throws Exception {
        File apk = new File(updatesDir, "kani-test.apk");
        try (FileOutputStream output = new FileOutputStream(apk)) {
            output.write(new byte[]{1, 2, 3});
        }

        try (ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(ApkContentProvider.uriFor(context, apk.getName()), "r")) {
            assertNotNull(fd);
            assertEquals(3L, fd.getStatSize());
        }
    }

    @Test
    public void providerReportsApkMimeTypeAndNoMutableOperations() {
        Uri uri = ApkContentProvider.uriFor(context, "kani-test.apk");

        assertEquals("application/vnd.android.package-archive", context.getContentResolver().getType(uri));
        assertNull(context.getContentResolver().query(uri, null, null, null, null));
        assertNull(context.getContentResolver().insert(uri, new ContentValues()));
        assertEquals(0, context.getContentResolver().delete(uri, null, null));
        assertEquals(0, context.getContentResolver().update(uri, new ContentValues(), null, null));
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsWriteMode() throws Exception {
        context.getContentResolver().openFileDescriptor(ApkContentProvider.uriFor(context, "kani-test.apk"), "w");
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsMissingCachedApk() throws Exception {
        context.getContentResolver().openFileDescriptor(ApkContentProvider.uriFor(context, "missing.apk"), "r");
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsOpenWhenProviderHasNoAttachedContext() throws Exception {
        new ApkContentProvider().openFile(Uri.parse("content://dev.bee.kanjianki.apk/kani.apk"), "r");
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsUriWithoutFileName() throws Exception {
        context.getContentResolver().openFileDescriptor(Uri.parse("content://" + context.getPackageName() + ".apk"), "r");
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsDirectoryInsideUpdatesCache() throws Exception {
        File directory = new File(updatesDir, "nested");
        directory.mkdirs();

        context.getContentResolver().openFileDescriptor(ApkContentProvider.uriFor(context, directory.getName()), "r");
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsPathTraversalOutsideUpdatesDirectory() throws Exception {
        File outside = new File(context.getCacheDir(), "outside.apk");
        try (FileOutputStream output = new FileOutputStream(outside)) {
            output.write(new byte[]{4, 5, 6});
        }
        Uri traversal = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".apk")
                .encodedPath("%2E%2E%2Foutside.apk")
                .build();

        context.getContentResolver().openFileDescriptor(traversal, "r");
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

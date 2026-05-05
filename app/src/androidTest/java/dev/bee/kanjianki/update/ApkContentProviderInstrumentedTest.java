package dev.bee.kanjianki.update;

import android.content.Context;
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

    @Test(expected = FileNotFoundException.class)
    public void rejectsWriteMode() throws Exception {
        context.getContentResolver().openFileDescriptor(ApkContentProvider.uriFor(context, "kani-test.apk"), "w");
    }

    @Test(expected = FileNotFoundException.class)
    public void rejectsPathTraversalOutsideUpdatesDirectory() throws Exception {
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

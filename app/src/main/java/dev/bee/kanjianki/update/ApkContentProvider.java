package dev.bee.kanjianki.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public final class ApkContentProvider extends ContentProvider {
    public static Uri uriFor(Context context, String fileName) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".apk")
                .appendPath(fileName)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider.");
        }
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("No context.");
        }
        String fileName = uri.getLastPathSegment();
        File file = new File(new File(context.getCacheDir(), "updates"), fileName == null ? "" : fileName);
        File updatesDir = new File(context.getCacheDir(), "updates");
        try {
            String canonical = file.getCanonicalPath();
            String updatesPath = updatesDir.getCanonicalPath();
            if ((!canonical.equals(updatesPath) && !canonical.startsWith(updatesPath + File.separator)) || !file.isFile()) {
                throw new FileNotFoundException("APK not found.");
            }
        } catch (Exception error) {
            throw new FileNotFoundException(error.getMessage());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

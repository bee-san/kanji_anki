package dev.bee.kanjianki.update

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class ApkContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = APK_MIME_TYPE

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw FileNotFoundException("Read-only provider.")
        }
        val context = context ?: throw FileNotFoundException("No context.")
        val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME)
        val file = File(updatesDir, uri.lastPathSegment ?: "")
        try {
            val canonical = file.canonicalPath
            val updatesPath = updatesDir.canonicalPath
            if ((!canonical.equals(updatesPath) && !canonical.startsWith(updatesPath + File.separator)) || !file.isFile) {
                throw FileNotFoundException("APK not found.")
            }
        } catch (error: Exception) {
            throw FileNotFoundException(error.message)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val UPDATES_DIR_NAME = "updates"

        @JvmStatic
        fun uriFor(context: Context, fileName: String?): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".apk")
                .appendPath(fileName)
                .build()
        }
    }
}

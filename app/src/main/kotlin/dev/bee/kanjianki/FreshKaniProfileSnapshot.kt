package dev.bee.kanjianki

import android.content.Context
import android.content.ContextWrapper
import dev.bee.kanjianki.data.LocalStoreSchema
import java.io.File
import java.io.IOException
import java.util.UUID

internal object FreshKaniProfileSnapshot {
    @Throws(IOException::class)
    fun create(context: Context, destination: File) {
        if (destination.exists()) throw IOException("Fresh profile destination already exists")
        val parent = destination.parentFile
            ?: throw IOException("Fresh profile destination has no parent directory")
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
            throw IOException("Unable to create fresh profile directory")
        }
        val source = File(
            context.cacheDir,
            "kani-fresh-profile-${UUID.randomUUID()}.db",
        )
        val isolatedContext = IsolatedDatabaseContext(context.applicationContext, source)
        try {
            AppLocalStoreFactory.create(isolatedContext).use { freshStore ->
                check(freshStore.writableDatabase.version == LocalStoreSchema.DB_VERSION)
                freshStore.snapshotInto(destination)
            }
        } finally {
            deleteDatabaseFiles(source)
        }
    }

    private fun deleteDatabaseFiles(database: File) {
        listOf(
            database,
            File(database.absolutePath + "-wal"),
            File(database.absolutePath + "-shm"),
            File(database.absolutePath + "-journal"),
        ).forEach { file ->
            if (file.exists() && !file.delete()) file.deleteOnExit()
        }
    }

    private class IsolatedDatabaseContext(
        base: Context,
        private val database: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File =
            if (name == LocalStoreSchema.DB_NAME) database else super.getDatabasePath(name)
    }
}

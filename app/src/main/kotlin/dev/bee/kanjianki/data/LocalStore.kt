package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

internal class LocalStore(context: Context?) : LocalStoreSync(context) {
    private val mnemonicNotes = LocalStoreMnemonicNotes(this)

    fun kanjiMnemonicNote(kanji: String?): String = mnemonicNotes.read(kanji)

    fun saveKanjiMnemonicNote(kanji: String?, note: String?, updatedAtMillis: Long) {
        mnemonicNotes.save(kanji, note, updatedAtMillis)
    }

    private fun warn(message: String) {
        try {
            Log.w("LocalStore", message)
        } catch (_: RuntimeException) {
            // Android Log is unavailable in local JVM tests.
        }
    }

    /**
     * Produce a WAL-safe, transactionally consistent snapshot of the database at
     * [dest] using this helper connection.
     *
     * `VACUUM INTO` (SQLite 3.27+) writes a fully checkpointed, defragmented copy that
     * includes committed WAL content, without blocking readers. When it is not
     * available (older bundled SQLite) or fails, fall back to folding the WAL into the
     * main file via a TRUNCATE checkpoint on this same connection and copying the main
     * database file. [dest] must not already exist for the VACUUM path.
     */
    @Throws(IOException::class)
    fun snapshotInto(dbFile: File, dest: File) {
        if (tryVacuumInto(dest)) {
            return
        }
        checkpointAndCopy(dbFile, dest)
    }

    private fun tryVacuumInto(dest: File): Boolean {
        return try {
            // VACUUM INTO refuses to overwrite an existing file; the caller uses a fresh
            // timestamped path, but guard anyway so a stale partial cannot block it.
            if (dest.exists() && !dest.delete()) {
                return false
            }
            writableDatabase.execSQL("VACUUM INTO ?", arrayOf<Any>(dest.absolutePath))
            true
        } catch (_: SQLiteException) {
            // Older SQLite without VACUUM INTO, or a transient failure: fall back.
            if (dest.exists() && !dest.delete()) {
                warn("Could not delete partial VACUUM output before fallback: ${dest.name}")
            }
            false
        }
    }

    @Throws(IOException::class)
    private fun checkpointAndCopy(dbFile: File, dest: File) {
        try {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close()
        } catch (_: SQLiteException) {
            // Best effort: even without a fresh checkpoint the main file copy is usable.
        }
        FileInputStream(dbFile).use { inStream ->
            FileOutputStream(dest).use { outStream ->
                inStream.channel.use { inChannel ->
                    outStream.channel.use { outChannel ->
                        val size = inChannel.size()
                        var transferred = 0L
                        while (transferred < size) {
                            transferred += inChannel.transferTo(transferred, size - transferred, outChannel)
                        }
                        outChannel.force(true)
                    }
                }
            }
        }
    }
}

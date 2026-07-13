package dev.bee.kanjianki.data

import android.content.Context
import android.os.Build
import dev.bee.kanjianki.backup.WalSafeSnapshotOperations
import java.io.File
import java.io.IOException

internal class LocalStore(context: Context?) : LocalStoreSync(context) {
    private val mnemonicNotes = LocalStoreMnemonicNotes(this)

    fun kanjiMnemonicNote(kanji: String?): String = mnemonicNotes.read(kanji)

    fun saveKanjiMnemonicNote(kanji: String?, note: String?, updatedAtMillis: Long) {
        mnemonicNotes.save(kanji, note, updatedAtMillis)
    }

    /**
     * Produce a WAL-safe, transactionally consistent snapshot at [dest] using this
     * helper connection.
     *
     * `VACUUM INTO` (SQLite 3.27+) writes a fully checkpointed, defragmented copy that
     * includes committed WAL content. Stock Android first provides a new-enough SQLite
     * version on API 30; older platforms and every operation failure are rejected rather
     * than degrading to an unsafe main-file copy. [dest] must not already exist.
     */
    @Throws(IOException::class)
    fun snapshotInto(dest: File) {
        WalSafeSnapshotOperations.create(dest, Build.VERSION.SDK_INT) { destination ->
            writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf<Any>(destination.absolutePath),
            )
        }
    }
}

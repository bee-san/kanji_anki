package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.ManualKanjiSource
import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException

internal class LocalStore(
    context: Context?,
    diagnosticLogger: DiagnosticLogger = NoOpDiagnosticLogger,
) : LocalStoreSync(context, diagnosticLogger), DatabaseSnapshotter {
    private val mnemonicNotes = LocalStoreMnemonicNotes(this)
    private val missingKanji = MissingKanjiStore(
        this,
        manualSourcesChanged = ::onManualKanjiSourcesChanged,
    )

    fun kanjiMnemonicNote(kanji: String?): String = mnemonicNotes.read(kanji)

    fun saveKanjiMnemonicNote(kanji: String?, note: String?, updatedAtMillis: Long) {
        mnemonicNotes.save(kanji, note, updatedAtMillis)
    }

    fun missingKanjiStore(): MissingKanjiStore = missingKanji

    override fun manualKanjiSources(admittedOnly: Boolean): List<ManualKanjiSource> {
        return if (admittedOnly) {
            missingKanji.admittedManualSources()
        } else {
            missingKanji.manualSources()
        }
    }

    override fun manualKanjiSource(literal: String): ManualKanjiSource? {
        return missingKanji.manualSource(literal)
    }

    private fun onManualKanjiSourcesChanged() {
        clearDashboardRowsCache()
        clearStudyItemsCache()
    }

    /**
     * Detaches this helper from the database file it has open and drops every cached
     * projection and settings snapshot.
     *
     * Instrumentation deletes `kanji_anki_simple.db` between tests. Before the process
     * container owned the store, each activity opened its own helper, so the next test
     * always got a helper that ran `onCreate` against the fresh file. A process-cached
     * helper instead keeps its connection pool: on real Android the pool reopens the
     * unlinked path as an *empty* database without rerunning `onCreate`, so every query
     * fails with `no such table`. Closing here forces the next `getWritableDatabase` to
     * reopen and recreate the schema.
     *
     * `SQLiteOpenHelper.close()` is idempotent and permits reopening, so the container
     * can keep handing out this same instance.
     */
    internal fun resetForTestDatabaseReplacement() {
        close()
        clearAllProjectionCachesForTest()
        settingsStore().invalidate()
    }

    /**
     * Produce a WAL-safe, transactionally consistent snapshot at [destination] using this
     * helper connection.
     *
     * `VACUUM INTO` (SQLite 3.27+) writes a fully checkpointed, defragmented copy that
     * includes committed WAL content. Stock Android first provides a new-enough SQLite
     * version on API 30; older platforms and every operation failure are rejected rather
     * than degrading to an unsafe main-file copy. [destination] must not already exist.
     */
    @Throws(IOException::class)
    override fun snapshotInto(destination: File) {
        WalSafeSnapshotOperations.create(destination, Build.VERSION.SDK_INT) { snapshot ->
            writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf<Any>(snapshot.absolutePath),
            )
        }
    }
}

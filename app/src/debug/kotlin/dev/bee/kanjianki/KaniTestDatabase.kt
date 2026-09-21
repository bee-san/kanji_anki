package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.data.LocalStoreSchema

/**
 * Deletes the Kani database for a test and detaches the process-owned store from the
 * file it had open.
 *
 * Instrumentation resets state by deleting `kanji_anki_simple.db` in `@Before`/`@After`.
 * That was safe while each activity opened its own [dev.bee.kanjianki.data.LocalStore],
 * but the process container (Goal 170) now caches one helper for the whole
 * instrumentation run. A cached helper keeps its connection pool across the deletion; on
 * real Android the pool reopens the unlinked path as an empty database and does not
 * rerun `onCreate`, so subsequent queries fail with `no such table: <name>`.
 *
 * Tests must call this instead of `Context.deleteDatabase` directly. It is order-safe:
 * the store is detached before *and* after deletion, so neither a lingering connection
 * nor a lazily reopened one can hold the stale file.
 *
 * It also removes SQLite's `-journal`, `-wal`, and `-shm` sidecars, which
 * `deleteDatabase` can leave behind. Those are why "delete the database" was not enough
 * to isolate a test: a reopened store can recover cached settings from a surviving
 * journal, and `File.createNewFile()` returns false on a path whose directory still holds
 * one. Both showed up as unrelated-looking CI failures — a widget reader reporting the
 * wrong state, then a downgrade notice appearing on a supposedly fresh store — that
 * passed in isolation and failed only in the full suite, and only once an unrelated
 * change reordered it.
 */
object KaniTestDatabase {
    @JvmStatic
    fun delete(context: Context) {
        detachProcessStore(context)
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        deleteSidecars(context)
        detachProcessStore(context)
    }

    /**
     * Removes what SQLite writes beside the database and `deleteDatabase` may not.
     *
     * Best-effort by design: a sidecar that never existed is the normal case, and failing
     * here would turn successful isolation into a test error.
     */
    private fun deleteSidecars(context: Context) {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        val parent = databaseFile.parentFile ?: return
        for (suffix in SQLITE_SIDECARS) {
            runCatching { java.io.File(parent, databaseFile.name + suffix).delete() }
        }
    }

    private val SQLITE_SIDECARS = listOf("-journal", "-wal", "-shm")

    private fun detachProcessStore(context: Context) {
        // The container only exists once KaniApplication.onCreate has run its restore gate.
        // Unit-style hosts and pre-startup calls legitimately have no container yet.
        val application = context.applicationContext as? KaniApplication ?: return
        runCatching { application.container.localStore.resetForTestDatabaseReplacement() }
    }
}

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
 */
object KaniTestDatabase {
    @JvmStatic
    fun delete(context: Context) {
        detachProcessStore(context)
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        detachProcessStore(context)
    }

    private fun detachProcessStore(context: Context) {
        // The container only exists once KaniApplication.onCreate has run its restore gate.
        // Unit-style hosts and pre-startup calls legitimately have no container yet.
        val application = context.applicationContext as? KaniApplication ?: return
        runCatching { application.container.localStore.resetForTestDatabaseReplacement() }
    }
}

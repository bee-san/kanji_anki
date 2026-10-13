package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException

/**
 * Execute [block] and wrap the result. `SQLiteDatabaseLockedException` is
 * classified as transient (retry-eligible); other `SQLiteException` or
 * `IllegalStateException` (database already closed) is permanent.
 */
inline fun <T> safeStoreCall(block: () -> T): StoreResult<T> {
    return try {
        StoreResult.ok(block())
    } catch (e: SQLiteDatabaseLockedException) {
        StoreResult.transient(e)
    } catch (e: SQLiteException) {
        StoreResult.permanent(e)
    } catch (e: IllegalStateException) {
        StoreResult.permanent(e)
    }
}

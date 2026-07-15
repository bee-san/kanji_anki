package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import dev.bee.kanjianki.core.StoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafeStoreCallTest {

    @Test
    fun successReturnsOk() {
        val result = safeStoreCall { 42 }
        assertTrue(result is StoreResult.Ok)
        assertEquals(42, result.valueOrNull())
    }

    @Test
    fun lockedExceptionReturnsTransient() {
        val result = safeStoreCall<Int> {
            throw SQLiteDatabaseLockedException("database is locked")
        }
        assertTrue(result is StoreResult.TransientError)
        assertTrue((result as StoreResult.TransientError).cause is SQLiteDatabaseLockedException)
    }

    @Test
    fun sqliteExceptionReturnsPermanent() {
        val result = safeStoreCall<Int> {
            throw SQLiteException("no such table: foo")
        }
        assertTrue(result is StoreResult.PermanentError)
        assertTrue((result as StoreResult.PermanentError).cause is SQLiteException)
    }

    @Test
    fun illegalStateExceptionReturnsPermanent() {
        val result = safeStoreCall<Int> {
            throw IllegalStateException("database already closed")
        }
        assertTrue(result is StoreResult.PermanentError)
        assertTrue((result as StoreResult.PermanentError).cause is IllegalStateException)
    }

    @Test
    fun otherExceptionsPropagateUnwrapped() {
        var caught = false
        try {
            safeStoreCall<Int> {
                throw NullPointerException("unexpected")
            }
        } catch (e: NullPointerException) {
            caught = true
        }
        assertTrue(caught)
    }
}

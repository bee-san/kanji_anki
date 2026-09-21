package dev.bee.kanjianki.data.sql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlContractsTest {
    @Test
    fun contractEnumsPinPortableSqlNamesAndValueKinds() {
        assertEquals(
            listOf("busy_timeout", "foreign_keys", "journal_mode", "synchronous", "user_version"),
            SqlPragma.entries.map(SqlPragma::sqlName),
        )
        assertEquals(
            listOf("NULL", "INTEGER", "REAL", "TEXT", "BLOB"),
            SqlValueType.entries.map(Enum<*>::name),
        )
        assertEquals(
            listOf("IMMEDIATE", "DEFERRED"),
            SqlTransactionMode.entries.map(Enum<*>::name),
        )
        assertEquals(
            listOf("PRIMARY_KEY", "UNIQUE", "NOT_NULL", "FOREIGN_KEY", "CHECK", "UNKNOWN"),
            SqlConstraintKind.entries.map(Enum<*>::name),
        )
    }

    @Test
    fun exceptionTypesRetainCauseAndConstraintClassification() {
        val cause = IllegalStateException("native failure")
        val exceptions = listOf(
            SqlException("base"),
            SqlBusyException("busy", cause),
            SqlConnectionClosedException("closed", cause),
            SqlConnectionLostException("lost", cause),
            SqlWriterUnavailableException("writer", cause),
            SqlReentrancyException("reentrant"),
        )

        assertNull(exceptions.first().cause)
        assertNull(exceptions.last().cause)
        exceptions.subList(1, 5).forEach { assertSame(cause, it.cause) }
        assertTrue(exceptions.all { it.message!!.isNotBlank() })

        val constraint = SqlConstraintException(
            SqlConstraintKind.UNIQUE,
            "duplicate",
            cause,
        )
        assertEquals(SqlConstraintKind.UNIQUE, constraint.kind)
        assertSame(cause, constraint.cause)
    }
}

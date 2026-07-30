package dev.bee.kanjianki.data.sql

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DedicatedWriterSqlDatabaseTest {
    private val databases = mutableListOf<DedicatedWriterSqlDatabase>()

    @After
    fun closeDatabases() {
        databases.asReversed().forEach(DedicatedWriterSqlDatabase::close)
    }

    @Test
    fun writesUseOneConfiguredConnectionAndDedicatedThread() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)
        val callbackThreads = mutableListOf<Thread>()

        val first = database.write {
            callbackThreads += Thread.currentThread()
            changes() + lastInsertRowId()
        }
        val second = database.write {
            callbackThreads += Thread.currentThread()
            9L
        }

        assertEquals(12L, first)
        assertEquals(9L, second)
        assertEquals(1, driver.connections.size)
        assertEquals(listOf(SqlConnectionMode.READ_WRITE), driver.connectionModes)
        assertSame(callbackThreads[0], callbackThreads[1])
        assertEquals("test-sql-writer", callbackThreads[0].name)
        assertEquals(
            listOf(
                "pragma:busy_timeout=41",
                "pragma:journal_mode=WAL",
                "pragma:foreign_keys=1",
                "pragma:synchronous=FULL",
                "begin:IMMEDIATE",
                "changes",
                "lastInsertRowId",
                "commit",
                "begin:IMMEDIATE",
                "commit",
            ),
            driver.connections.single().events,
        )
    }

    @Test
    fun concurrentWritesAreSerialized() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()

        val first = async(Dispatchers.Default) {
            database.write {
                maximumActive.updateAndGet { maxOf(it, active.incrementAndGet()) }
                firstStarted.countDown()
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
                active.decrementAndGet()
            }
        }
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
        val second = async(Dispatchers.Default) {
            database.write {
                maximumActive.updateAndGet { maxOf(it, active.incrementAndGet()) }
                active.decrementAndGet()
            }
        }
        releaseFirst.countDown()
        first.await()
        second.await()

        assertEquals(1, maximumActive.get())
        assertEquals(2, driver.connections.single().events.count { it == "begin:IMMEDIATE" })
    }

    @Test
    fun failedWriteRollsBackAndNestedFailureUsesRollbackThenRelease() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)

        val outerFailure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                database.write {
                    throw IllegalStateException("outer")
                }
            }
        }
        assertEquals("outer", outerFailure.message)

        database.write {
            try {
                savepoint {
                    execute("nested work")
                    throw IllegalArgumentException("nested")
                }
            } catch (_: IllegalArgumentException) {
                execute("outer work")
            }
        }

        assertEquals(
            listOf(
                "begin:IMMEDIATE",
                "rollback",
                "begin:IMMEDIATE",
                "execute:SAVEPOINT kani_sp_1",
                "execute:nested work",
                "execute:ROLLBACK TO kani_sp_1",
                "execute:RELEASE kani_sp_1",
                "execute:outer work",
                "commit",
            ),
            driver.connections.single().events.drop(4),
        )
    }

    @Test
    fun successfulNestedSavepointsHaveUniqueNames() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)

        database.write {
            savepoint { execute("first") }
            savepoint { execute("second") }
        }

        assertTrue(driver.connections.single().events.contains("execute:SAVEPOINT kani_sp_1"))
        assertTrue(driver.connections.single().events.contains("execute:RELEASE kani_sp_1"))
        assertTrue(driver.connections.single().events.contains("execute:SAVEPOINT kani_sp_2"))
        assertTrue(driver.connections.single().events.contains("execute:RELEASE kani_sp_2"))
    }

    @Test
    fun readSnapshotUsesIndependentDeferredConnectionAndAlwaysClosesIt() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)
        database.write { execute("writer") }

        val snapshotThread = database.readSnapshot {
            execute("reader")
            Thread.currentThread()
        }
        assertNotSame(Thread.currentThread(), snapshotThread)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                database.readSnapshot {
                    throw IllegalArgumentException("reader failed")
                }
            }
        }
        assertEquals("reader failed", failure.message)

        assertEquals(3, driver.connections.size)
        assertEquals(
            listOf(
                SqlConnectionMode.READ_WRITE,
                SqlConnectionMode.READ_ONLY,
                SqlConnectionMode.READ_ONLY,
            ),
            driver.connectionModes,
        )
        assertEquals(
            listOf(
                "pragma:busy_timeout=41",
                "pragma:foreign_keys=1",
                "begin:DEFERRED",
                "execute:reader",
                "commit",
                "close",
            ),
            driver.connections[1].events,
        )
        assertEquals(
            listOf(
                "pragma:busy_timeout=41",
                "pragma:foreign_keys=1",
                "begin:DEFERRED",
                "rollback",
                "close",
            ),
            driver.connections[2].events,
        )
    }

    @Test
    fun queuedCancellationDoesNotStartAWrite() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val cancelledCallbackRan = AtomicBoolean()

        val first = launch(Dispatchers.Default) {
            database.write {
                firstStarted.countDown()
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
        val cancelled = launch(Dispatchers.Default) {
            database.write {
                cancelledCallbackRan.set(true)
            }
        }
        cancelled.cancel()
        releaseFirst.countDown()
        cancelled.join()
        first.join()

        assertFalse(cancelledCallbackRan.get())
        assertEquals(1, driver.connections.single().events.count { it == "begin:IMMEDIATE" })
    }

    @Test
    fun cancellingReadInterruptsAndRollsBackItsConnection() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)

        val job = launch(Dispatchers.Default) {
            database.readSnapshot {
                callbackStarted.countDown()
                assertTrue(releaseCallback.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
        job.cancel()
        assertTrue(driver.connections.single().events.contains("interrupt"))
        releaseCallback.countDown()
        job.join()
        assertTrue(driver.connectionClosed.await(5, TimeUnit.SECONDS))

        val events = driver.connections.single().events
        assertTrue(events.contains("rollback"))
        assertTrue(events.contains("close"))
        assertFalse(events.contains("commit"))
    }

    @Test
    fun cancellationAfterCommitDoesNotUndoTheWrite() = runBlocking {
        val commitEntered = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val driver = RecordingDriver(
            onCommit = {
                commitEntered.countDown()
                assertTrue(releaseCommit.await(5, TimeUnit.SECONDS))
            },
        )
        val database = database(driver)
        val callbackRan = AtomicBoolean()

        val job = launch(Dispatchers.Default) {
            database.write {
                callbackRan.set(true)
            }
        }
        assertTrue(commitEntered.await(5, TimeUnit.SECONDS))
        job.cancel()
        releaseCommit.countDown()
        job.join()

        assertTrue(callbackRan.get())
        assertTrue(driver.connections.single().events.contains("commit"))
        assertFalse(driver.connections.single().events.contains("rollback"))
        assertTrue(job.isCancelled)
    }

    @Test
    fun reentrantSuspendableOperationFailsInsteadOfDeadlocking() = runBlocking {
        val database = database(RecordingDriver())

        database.write {
            val failure = assertThrows(SqlReentrancyException::class.java) {
                val result = runBlocking {
                    database.readSnapshot { Unit }
                }
                assertEquals(Unit, result)
            }
            assertTrue(failure.message!!.contains("re-entered"))
        }
    }

    @Test
    fun connectionLossPoisonsWriterAndClosesItsConnection() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)

        assertThrows(SqlConnectionLostException::class.java) {
            runBlocking {
                database.write {
                    throw SqlConnectionLostException("writer died")
                }
            }
        }
        val unavailable = assertThrows(SqlWriterUnavailableException::class.java) {
            val result = runBlocking {
                database.write { Unit }
            }
            assertEquals(Unit, result)
        }

        assertTrue(
            "unexpected writer cause: ${unavailable.cause}",
            generateSequence(unavailable as Throwable?) { it.cause }
                .any { it is SqlConnectionLostException },
        )
        assertTrue(driver.connections.single().events.contains("close"))
    }

    @Test
    fun fatalWriterFailurePoisonsSubsequentWrites() = runBlocking {
        val database = database(RecordingDriver())

        assertThrows(LinkageError::class.java) {
            runBlocking {
                database.write {
                    throw LinkageError("writer thread failed")
                }
            }
        }
        val unavailable = assertThrows(SqlWriterUnavailableException::class.java) {
            val result = runBlocking { database.write { Unit } }
            assertEquals(Unit, result)
        }

        assertTrue(
            generateSequence(unavailable as Throwable?) { it.cause }
                .any { it is LinkageError },
        )
    }

    @Test
    fun closeWaitsForAnActiveWriteBeforeClosingResources() = runBlocking {
        val driver = RecordingDriver()
        val database = database(driver)
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val closeFinished = CountDownLatch(1)

        val write = launch(Dispatchers.Default) {
            database.write {
                writeStarted.countDown()
                assertTrue(releaseWrite.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS))
        val closer = Thread {
            database.close()
            closeFinished.countDown()
        }.apply(Thread::start)

        assertFalse(closeFinished.await(100, TimeUnit.MILLISECONDS))
        releaseWrite.countDown()
        write.join()
        assertTrue(closeFinished.await(5, TimeUnit.SECONDS))
        closer.join(5_000L)
        assertFalse(closer.isAlive)
        assertEquals(1, driver.closeCount.get())
        assertTrue(driver.connections.single().events.takeLast(2).contains("close"))
    }

    @Test
    fun closeIsIdempotentAndRejectsNewOperations() {
        val driver = RecordingDriver()
        val database = database(driver)
        runBlocking { database.write { Unit } }

        database.close()
        database.close()

        assertEquals(1, driver.closeCount.get())
        assertEquals(1, driver.connections.single().events.count { it == "close" })
        assertThrows(SqlConnectionClosedException::class.java) {
            val result = runBlocking { database.write { Unit } }
            assertEquals(Unit, result)
        }
    }

    @Test
    fun configurationRejectsInvalidTimeoutAndThreadName() {
        assertThrows(IllegalArgumentException::class.java) {
            SqlDatabaseConfiguration(busyTimeoutMillis = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SqlDatabaseConfiguration(busyTimeoutMillis = Int.MAX_VALUE.toLong() + 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SqlDatabaseConfiguration(writerThreadName = " ")
        }
        assertEquals(
            SqlSynchronousMode.NORMAL,
            SqlDatabaseConfiguration().synchronousMode,
        )
    }

    private fun database(driver: RecordingDriver): DedicatedWriterSqlDatabase =
        DedicatedWriterSqlDatabase(
            driver = driver,
            configuration = SqlDatabaseConfiguration(
                busyTimeoutMillis = 41L,
                synchronousMode = SqlSynchronousMode.FULL,
                writerThreadName = "test-sql-writer",
            ),
        ).also(databases::add)

    private class RecordingDriver(
        private val onCommit: () -> Unit = {},
    ) : SqlDriver {
        val connections = Collections.synchronizedList(mutableListOf<RecordingConnection>())
        val connectionModes = Collections.synchronizedList(mutableListOf<SqlConnectionMode>())
        val closeCount = AtomicInteger()
        val connectionClosed = CountDownLatch(1)

        override fun openConnection(mode: SqlConnectionMode): SqlConnection =
            RecordingConnection(
                id = connections.size + 1,
                onCommit = onCommit,
                onClose = connectionClosed::countDown,
            ).also {
                connectionModes += mode
                connections += it
            }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class RecordingConnection(
        private val id: Int,
        private val onCommit: () -> Unit,
        private val onClose: () -> Unit,
    ) : SqlConnection {
        val events = Collections.synchronizedList(mutableListOf<String>())
        override var isOpen: Boolean = true
            private set
        override val pragmas: SqlPragmaAccess = RecordingPragmas(events)

        override fun beginTransaction(mode: SqlTransactionMode) {
            events += "begin:$mode"
        }

        override fun commitTransaction() {
            events += "commit"
            onCommit()
        }

        override fun rollbackTransaction() {
            events += "rollback"
        }

        override fun interrupt() {
            events += "interrupt"
        }

        override fun close() {
            if (isOpen) {
                isOpen = false
                events += "close"
                onClose()
            }
        }

        override fun prepare(sql: String): SqlStatement =
            throw UnsupportedOperationException(sql)

        override fun execute(sql: String) {
            events += "execute:$sql"
        }

        override fun changes(): Long {
            events += "changes"
            return 5L
        }

        override fun lastInsertRowId(): Long {
            events += "lastInsertRowId"
            return 7L
        }

        override fun toString(): String = "RecordingConnection($id)"
    }

    private class RecordingPragmas(
        private val events: MutableList<String>,
    ) : SqlPragmaAccess {
        override fun readLong(pragma: SqlPragma): Long =
            throw UnsupportedOperationException(pragma.sqlName)

        override fun readText(pragma: SqlPragma): String =
            throw UnsupportedOperationException(pragma.sqlName)

        override fun writeLong(pragma: SqlPragma, value: Long) {
            events += "pragma:${pragma.sqlName}=$value"
        }

        override fun writeText(pragma: SqlPragma, value: String) {
            events += "pragma:${pragma.sqlName}=$value"
        }
    }
}

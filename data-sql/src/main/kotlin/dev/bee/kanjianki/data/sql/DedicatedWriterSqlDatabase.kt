package dev.bee.kanjianki.data.sql

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class SqlDatabaseConfiguration(
    val busyTimeoutMillis: Long = 5_000L,
    val synchronousMode: SqlSynchronousMode = SqlSynchronousMode.NORMAL,
    val writerThreadName: String = "kani-sql-writer",
) {
    init {
        require(busyTimeoutMillis in 0..Int.MAX_VALUE.toLong()) {
            "busyTimeoutMillis must fit SQLite's signed 32-bit timeout"
        }
        require(writerThreadName.isNotBlank()) {
            "writerThreadName must not be blank"
        }
    }
}

enum class SqlSynchronousMode {
    FULL,
    NORMAL,
}

/**
 * Owns one physical writer connection on one thread. Read snapshots use an
 * independent connection so WAL readers do not queue behind a long write.
 */
class DedicatedWriterSqlDatabase(
    private val driver: SqlDriver,
    private val configuration: SqlDatabaseConfiguration = SqlDatabaseConfiguration(),
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    threadFactory: ThreadFactory = namedDaemonThreadFactory(configuration.writerThreadName),
) : SqlDatabase {
    private val writerThread = AtomicReference<Thread?>()
    private val writerDispatcher: ExecutorCoroutineDispatcher =
        java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            threadFactory.newThread {
                writerThread.set(Thread.currentThread())
                runnable.run()
            }
        }.asCoroutineDispatcher()
    private val lifecycleLock = ReentrantLock()
    private val operationsDrained = lifecycleLock.newCondition()
    private var activeOperations = 0
    private var closed = false

    @Volatile
    private var writerLostCause: Throwable? = null

    private var writerConnection: SqlConnection? = null
    private var writerOperationActive = false

    override suspend fun <T> write(block: SqlTransactionScope.() -> T): T {
        rejectWriterReentrancy()
        beginOperation()
        try {
            coroutineContext.ensureActive()
            return withContext(writerDispatcher) {
                coroutineContext.ensureActive()
                withContext(NonCancellable) {
                    runWrite(block)
                }
            }
        } finally {
            endOperation()
        }
    }

    override suspend fun <T> readSnapshot(block: SqlReadScope.() -> T): T {
        rejectWriterReentrancy()
        coroutineContext.ensureActive()
        beginOperation()
        return dispatchReadSnapshot(block)
    }

    override fun close() {
        rejectWriterReentrancy()
        lifecycleLock.withLock {
            if (closed) {
                return
            }
            closed = true
            operationsDrained.awaitUninterruptiblyUntil { activeOperations == 0 }
        }

        var closeFailure: Throwable? = null
        runBlocking {
            withContext(NonCancellable + writerDispatcher) {
                writerConnection?.let { connection ->
                    closeFailure = closeCatching(connection, closeFailure)
                    writerConnection = null
                }
            }
        }
        writerDispatcher.close()
        closeFailure = closeCatching(driver, closeFailure)
        closeFailure?.let { failure ->
            throw SqlException("Failed to close SQL database", failure)
        }
    }

    private fun <T> runWrite(block: SqlTransactionScope.() -> T): T {
        writerLostCause?.let { cause ->
            throw SqlWriterUnavailableException("SQL writer is unavailable", cause)
        }
        check(writerThread.get() === Thread.currentThread()) {
            "Writer work escaped its dedicated thread"
        }
        writerOperationActive = true
        try {
            val connection = writerConnection()
            connection.beginTransaction(SqlTransactionMode.IMMEDIATE)
            val scope = TransactionScope(connection)
            try {
                val result = scope.block()
                connection.commitTransaction()
                return result
            } catch (failure: Throwable) {
                rollbackAfterFailure(connection, failure)
                throw failure
            }
        } catch (failure: Throwable) {
            if (isWriterLoss(failure)) {
                markWriterLost(failure)
            }
            throw failure
        } finally {
            writerOperationActive = false
        }
    }

    private suspend fun <T> dispatchReadSnapshot(
        block: SqlReadScope.() -> T,
    ): T = suspendCancellableCoroutine { continuation ->
        val connectionRef = AtomicReference<SqlConnection?>()
        val operationEnded = AtomicBoolean()
        continuation.invokeOnCancellation {
            connectionRef.get()?.interrupt()
        }

        try {
            readDispatcher.dispatch(continuation.context) {
                if (!continuation.isActive) {
                    endOperationOnce(operationEnded)
                    return@dispatch
                }
                var connection: SqlConnection? = null
                var outcome: Result<T>? = null
                try {
                    connection = driver.openConnection()
                    connectionRef.set(connection)
                    if (!continuation.isActive) {
                        connection.interrupt()
                    } else {
                        outcome = runCatching {
                            runReadSnapshot(
                                connection = connection,
                                isActive = { continuation.isActive },
                                block = block,
                            )
                        }
                    }
                } catch (failure: Throwable) {
                    outcome = Result.failure(failure)
                } finally {
                    connectionRef.set(null)
                    if (connection != null) {
                        try {
                            connection.close()
                        } catch (closeFailure: Throwable) {
                            val priorFailure = outcome?.exceptionOrNull()
                            if (priorFailure == null) {
                                outcome = Result.failure(closeFailure)
                            } else {
                                priorFailure.addSuppressed(closeFailure)
                            }
                        }
                    }
                    endOperationOnce(operationEnded)
                }
                outcome?.let(continuation::resumeWith)
            }
        } catch (dispatchFailure: Throwable) {
            endOperationOnce(operationEnded)
            continuation.resumeWith(Result.failure(dispatchFailure))
        }
    }

    private fun <T> runReadSnapshot(
        connection: SqlConnection,
        isActive: () -> Boolean,
        block: SqlReadScope.() -> T,
    ): T {
        configureReader(connection)
        connection.beginTransaction(SqlTransactionMode.DEFERRED)
        try {
            val result = ReadScope(connection).block()
            if (!isActive()) {
                throw CancellationException("SQL read snapshot was cancelled")
            }
            connection.commitTransaction()
            return result
        } catch (failure: Throwable) {
            rollbackAfterFailure(connection, failure)
            throw failure
        }
    }

    private fun writerConnection(): SqlConnection {
        writerConnection?.let { return it }
        val opened = driver.openConnection()
        try {
            configureWriter(opened)
        } catch (failure: Throwable) {
            closeCatching(opened, failure)
            throw failure
        }
        writerConnection = opened
        return opened
    }

    private fun configureWriter(connection: SqlConnection) {
        connection.pragmas.writeLong(
            SqlPragma.BUSY_TIMEOUT,
            configuration.busyTimeoutMillis,
        )
        connection.pragmas.writeText(SqlPragma.JOURNAL_MODE, "WAL")
        connection.pragmas.writeLong(SqlPragma.FOREIGN_KEYS, 1L)
        connection.pragmas.writeText(
            SqlPragma.SYNCHRONOUS,
            configuration.synchronousMode.name,
        )
    }

    private fun configureReader(connection: SqlConnection) {
        connection.pragmas.writeLong(
            SqlPragma.BUSY_TIMEOUT,
            configuration.busyTimeoutMillis,
        )
        connection.pragmas.writeLong(SqlPragma.FOREIGN_KEYS, 1L)
    }

    private fun rollbackAfterFailure(
        connection: SqlConnection,
        failure: Throwable,
    ) {
        try {
            connection.rollbackTransaction()
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
            if (isWriterLoss(rollbackFailure)) {
                markWriterLost(rollbackFailure)
            }
        }
    }

    private fun markWriterLost(cause: Throwable) {
        if (writerLostCause == null) {
            writerLostCause = cause
        }
        writerConnection?.let { connection ->
            writerConnection = null
            closeCatching(connection, cause)
        }
    }

    private fun beginOperation() {
        lifecycleLock.withLock {
            if (closed) {
                throw SqlConnectionClosedException("SQL database is closed")
            }
            activeOperations += 1
        }
    }

    private fun endOperation() {
        lifecycleLock.withLock {
            activeOperations -= 1
            check(activeOperations >= 0) {
                "SQL database operation count underflow"
            }
            if (activeOperations == 0) {
                operationsDrained.signalAll()
            }
        }
    }

    private fun endOperationOnce(ended: AtomicBoolean) {
        if (ended.compareAndSet(false, true)) {
            endOperation()
        }
    }

    private fun rejectWriterReentrancy() {
        if (writerThread.get() === Thread.currentThread() && writerOperationActive) {
            throw SqlReentrancyException(
                "Suspendable SQL operations cannot be re-entered from a transaction callback",
            )
        }
    }

    private class ReadScope(
        connection: SqlConnection,
    ) : SqlReadScope, SqlSession by connection

    private class TransactionScope(
        private val connection: SqlConnection,
    ) : SqlTransactionScope, SqlSession by connection {
        private var nextSavepoint = 0L

        override fun <T> savepoint(block: SqlTransactionScope.() -> T): T {
            nextSavepoint += 1
            val name = "kani_sp_$nextSavepoint"
            connection.execute("SAVEPOINT $name")
            try {
                val result = block()
                connection.execute("RELEASE $name")
                return result
            } catch (failure: Throwable) {
                try {
                    connection.execute("ROLLBACK TO $name")
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressed(rollbackFailure)
                }
                try {
                    connection.execute("RELEASE $name")
                } catch (releaseFailure: Throwable) {
                    failure.addSuppressed(releaseFailure)
                }
                throw failure
            }
        }
    }

    companion object {
        private fun namedDaemonThreadFactory(name: String): ThreadFactory =
            ThreadFactory { runnable ->
                Thread(runnable, name).apply {
                    isDaemon = true
                }
            }

        private fun isWriterLoss(failure: Throwable): Boolean =
            failure is SqlConnectionLostException ||
                failure is VirtualMachineError ||
                failure is LinkageError ||
                failure is ThreadDeath

        private fun closeCatching(
            closeable: AutoCloseable,
            priorFailure: Throwable?,
        ): Throwable? =
            try {
                closeable.close()
                priorFailure
            } catch (closeFailure: Throwable) {
                if (priorFailure == null) {
                    closeFailure
                } else {
                    priorFailure.addSuppressed(closeFailure)
                    priorFailure
                }
            }

        private inline fun java.util.concurrent.locks.Condition.awaitUninterruptiblyUntil(
            predicate: () -> Boolean,
        ) {
            while (!predicate()) {
                awaitUninterruptibly()
            }
        }
    }
}

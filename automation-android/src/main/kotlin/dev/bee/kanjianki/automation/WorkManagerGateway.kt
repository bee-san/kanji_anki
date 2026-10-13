package dev.bee.kanjianki.automation

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun interface PendingWorkOperation {
    fun await()
}

interface WorkManagerGateway {
    fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ): PendingWorkOperation

    fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): PendingWorkOperation

    fun cancelUniqueWork(uniqueWorkName: String): PendingWorkOperation
}

class AndroidWorkManagerGateway(
    context: Context,
    private val persistenceTimeoutSeconds: Long = DEFAULT_PERSISTENCE_TIMEOUT_SECONDS,
) : WorkManagerGateway {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    init {
        require(persistenceTimeoutSeconds > 0L) {
            "WorkManager persistence timeout must be positive"
        }
    }

    override fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ): PendingWorkOperation =
        workManager.enqueueUniquePeriodicWork(uniqueWorkName, policy, request).pendingOperation()

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): PendingWorkOperation =
        workManager.enqueueUniqueWork(uniqueWorkName, policy, request).pendingOperation()

    override fun cancelUniqueWork(uniqueWorkName: String): PendingWorkOperation =
        workManager.cancelUniqueWork(uniqueWorkName).pendingOperation()

    private fun Operation.pendingOperation(): PendingWorkOperation = PendingWorkOperation {
        try {
            result[persistenceTimeoutSeconds, TimeUnit.SECONDS]
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    companion object {
        const val DEFAULT_PERSISTENCE_TIMEOUT_SECONDS: Long = 15L
    }
}

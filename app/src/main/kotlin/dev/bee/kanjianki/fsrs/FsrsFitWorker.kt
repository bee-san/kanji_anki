package dev.bee.kanjianki.fsrs

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.core.FsrsWeightFitter
import dev.bee.kanjianki.data.FsrsFitSummary
import dev.bee.kanjianki.data.FsrsFitSummaryCodec
import dev.bee.kanjianki.data.FsrsTrainingDataQueries
import dev.bee.kanjianki.data.LocalStore
import java.util.concurrent.atomic.AtomicBoolean

internal object FsrsFitExecutionGate {
    private val running = AtomicBoolean(false)

    fun tryAcquire(): Boolean = running.compareAndSet(false, true)

    fun release() {
        running.set(false)
    }
}

class FsrsFitWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        if (!FsrsFitExecutionGate.tryAcquire()) return Result.retry()
        return try {
            LocalStore(applicationContext).use { store ->
                if (!store.fsrsPersonalizationEnabled()) {
                    Result.success()
                } else try {
                    FsrsFitRunner.run(
                        store = store,
                        fittedAtMillis = System.currentTimeMillis(),
                        fitter = FsrsWeightFitter(),
                        shouldStop = { isStopped },
                    )
                    Result.success()
                } catch (_: RuntimeException) {
                    runCatching { FsrsFitRunner.recordFailure(store, System.currentTimeMillis()) }
                    Result.failure()
                }
            }
        } finally {
            FsrsFitExecutionGate.release()
        }
    }
}

internal object FsrsFitRunner {
    internal fun interface FitOperation {
        fun fit(sequences: List<dev.bee.kanjianki.core.FsrsReplaySequence>, shouldStop: () -> Boolean): FsrsWeightFitter.Result
    }

    fun run(
        store: LocalStore,
        fittedAtMillis: Long,
        fitter: FsrsWeightFitter,
        shouldStop: () -> Boolean = { false },
    ): FsrsWeightFitter.Result = run(
        store,
        fittedAtMillis,
        FitOperation { sequences, stop -> fitter.fit(sequences, stop) },
        shouldStop,
    )

    internal fun run(
        store: LocalStore,
        fittedAtMillis: Long,
        fitOperation: FitOperation,
        shouldStop: () -> Boolean = { false },
    ): FsrsWeightFitter.Result {
        val sequences = FsrsTrainingDataQueries(store.readableDatabase).sequences()
        val fitted = fitOperation.fit(sequences, shouldStop)
        val disabled = fitted.copy(
            adopted = false,
            reason = FsrsWeightFitter.REASON_DISABLED_DURING_FIT,
        )
        val adopted = store.commitFsrsFitOutcome(
            weightsToAdopt = fitted.weights.takeIf { fitted.adopted },
            summaryJson = summaryJson(fitted, fittedAtMillis),
            disabledSummaryJson = summaryJson(disabled, fittedAtMillis),
            preserveExistingWeights = fitted.reason == FsrsWeightFitter.REASON_CANCELLED,
        )
        return if (fitted.adopted && !adopted) disabled else fitted
    }

    internal fun recordFailure(store: LocalStore, fittedAtMillis: Long) {
        val summary = FsrsFitSummary(
            sampleCount = 0,
            trainingSampleCount = 0,
            validationSampleCount = 0,
            defaultTrainingLoss = null,
            defaultValidationLoss = null,
            fittedTrainingLoss = null,
            fittedValidationLoss = null,
            adopted = false,
            reason = FsrsWeightFitter.REASON_FAILED,
            fittedAtMillis = fittedAtMillis,
        )
        store.commitFsrsFitOutcome(
            weightsToAdopt = null,
            summaryJson = FsrsFitSummaryCodec.encode(summary),
            disabledSummaryJson = null,
            preserveExistingWeights = true,
        )
    }

    private fun summaryJson(result: FsrsWeightFitter.Result, fittedAtMillis: Long): String =
        FsrsFitSummaryCodec.encode(FsrsFitSummary(
            sampleCount = result.sampleCount,
            trainingSampleCount = result.trainingSampleCount,
            validationSampleCount = result.validationSampleCount,
            defaultTrainingLoss = result.defaultTrainingLoss.finiteOrNull(),
            defaultValidationLoss = result.defaultValidationLoss.finiteOrNull(),
            fittedTrainingLoss = result.fittedTrainingLoss.finiteOrNull(),
            fittedValidationLoss = result.fittedValidationLoss.finiteOrNull(),
            adopted = result.adopted,
            reason = result.reason,
            fittedAtMillis = fittedAtMillis,
        ))

    private fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }
}

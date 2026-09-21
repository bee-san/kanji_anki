package dev.bee.kanjianki.study

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.vision.digitalink.common.Point
import com.google.mlkit.vision.digitalink.common.RecognitionCandidate
import com.google.mlkit.vision.digitalink.common.RecognitionResult as MlKitRecognitionResult
import com.google.mlkit.vision.digitalink.common.Stroke
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor

class MlKitJapaneseWritingRecognizerTest {
    @Test
    fun modelStatusReportsDownloadedAndMissingStates() {
        val backend = RecordingBackend()
        val recognizer = MlKitJapaneseWritingRecognizer(backend)

        val missing = recognizer.modelStatus().join()
        backend.downloaded = true
        val ready = recognizer.modelStatus().join()

        assertEquals("JA", missing.modelName)
        assertEquals("ja", missing.languageTag)
        assertFalse(missing.downloaded)
        assertEquals("Handwriting checker needs download.", missing.message)
        assertTrue(ready.downloaded)
        assertEquals("Handwriting checker is ready.", ready.message)
        assertEquals(2, backend.statusCalls)
    }

    @Test
    fun downloadModelShortCircuitsWhenModelIsAlreadyDownloaded() {
        val backend = RecordingBackend().apply {
            downloaded = true
        }
        val recognizer = MlKitJapaneseWritingRecognizer(backend)

        val status = recognizer.downloadModel().join()

        assertTrue(status.downloaded)
        assertEquals("Handwriting checker is ready.", status.message)
        assertEquals(1, backend.statusCalls)
        assertEquals(0, backend.downloadCalls)
    }

    @Test
    fun downloadModelDownloadsWhenModelIsMissing() {
        val backend = RecordingBackend()
        val recognizer = MlKitJapaneseWritingRecognizer(backend)

        val status = recognizer.downloadModel().join()

        assertTrue(status.downloaded)
        assertEquals("Handwriting checker downloaded.", status.message)
        assertEquals(1, backend.statusCalls)
        assertEquals(1, backend.downloadCalls)
    }

    @Test
    fun recognizeFailsBeforeCallingMlKitWhenModelIsMissing() {
        val backend = RecordingBackend()
        val recognizer = MlKitJapaneseWritingRecognizer(backend)

        val result = recognizer.recognize(simpleWriting())
        val failure = joinFailure(result)

        assertTrue(failure is IllegalStateException)
        assertEquals("Handwriting checker is not downloaded.", failure.message)
        assertEquals(1, backend.statusCalls)
        assertEquals(0, backend.plainRecognitionCalls)
        assertEquals(0, backend.contextRecognitionCalls)
    }

    @Test
    fun recognizeUsesPlainRequestAndConvertsCapturedPoints() {
        val backend = RecordingBackend().apply {
            downloaded = true
            recognitionTask = RecordingTask.succeeded(result("水", 0.92f))
        }
        val recognizer = MlKitJapaneseWritingRecognizer(backend)
        val writing = CapturedWriting(
            listOf(
                stroke(
                    point(1.5f, 2.5f),
                    CapturedStroke.Point(3.5f, 4.5f, 70L),
                ),
            ),
        )

        val result = recognizer.recognize(writing).join()

        assertEquals("水", result.topText())
        assertEquals(0.92f, result.candidates[0].score)
        assertEquals(1, backend.plainRecognitionCalls)
        assertEquals(0, backend.contextRecognitionCalls)
        val stroke = backend.plainInk!!.strokes[0]
        assertEquals(2, stroke.pointsInGlobalCoordinates.size)
        assertPoint(stroke.pointsInGlobalCoordinates[0], 1.5f, 2.5f, null)
        assertPoint(stroke.pointsInGlobalCoordinates[1], 3.5f, 4.5f, 70L)
    }

    @Test
    fun recognizeUsesContextRequestWhenPreContextIsPresent() {
        val backend = RecordingBackend().apply {
            downloaded = true
        }
        val recognizer = MlKitJapaneseWritingRecognizer(backend)
        val writing = CapturedWriting(
            listOf(stroke(point(1f, 1f))),
            null,
            null,
            "before",
        )

        recognizer.recognize(writing).join()

        assertEquals(0, backend.plainRecognitionCalls)
        assertEquals(1, backend.contextRecognitionCalls)
        assertEquals("before", backend.context!!.preContext)
        assertNull(backend.context!!.writingArea)
    }

    @Test
    fun recognizePreparesWritingAreaContext() {
        val backend = RecordingBackend().apply {
            downloaded = true
        }
        val recognizer = MlKitJapaneseWritingRecognizer(backend)
        val writing = CapturedWriting(
            listOf(
                stroke(
                    CapturedStroke.Point(10f, 10f, 10L),
                    CapturedStroke.Point(20f, 20f, 20L),
                ),
            ),
            300f,
            200f,
            "discarded by existing preparation path",
        )

        recognizer.recognize(writing).join()

        assertEquals(0, backend.plainRecognitionCalls)
        assertEquals(1, backend.contextRecognitionCalls)
        assertNotNull(backend.context!!.writingArea)
        assertEquals("", backend.context!!.preContext)
        assertEquals(1000f, backend.context!!.writingArea!!.width)
        assertEquals(1000f, backend.context!!.writingArea!!.height)
        val stroke = backend.contextInk!!.strokes[0]
        assertPoint(stroke.pointsInGlobalCoordinates[0], 140f, 140f, 10L)
        assertPoint(stroke.pointsInGlobalCoordinates[1], 860f, 860f, 20L)
    }

    @Test
    fun taskBridgeCompletesOnSuccessFailureAndCancellation() {
        val success = RecordingTask<String>()
        val successFuture = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(success)
        success.succeed("done")
        assertEquals("done", successFuture.join())

        val failure = RecordingTask<String>()
        val failureFuture = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(failure)
        val error = IllegalArgumentException("bad task")
        failure.fail(error)
        assertSame(error, joinFailure(failureFuture))

        val canceled = RecordingTask<String>()
        val canceledFuture = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(canceled)
        canceled.cancel()
        val cancelFailure = joinFailure(canceledFuture)
        assertTrue(cancelFailure is CancellationException)
        assertEquals("Handwriting checker task was canceled.", cancelFailure.message)
    }

    @Test
    fun googleTaskBridgesACompletedPlayServicesTask() {
        val future = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(
            MlKitJapaneseWritingRecognizer.GoogleTask(Tasks.forResult("done")),
        )

        assertEquals("done", future.join())
    }

    @Test
    fun closeDelegatesToBackend() {
        val backend = RecordingBackend()
        val recognizer = MlKitJapaneseWritingRecognizer(backend)

        recognizer.close()

        assertEquals(1, backend.closeCalls)
    }

    @Test
    fun constructorRejectsNonPositiveMaxResultCount() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            newRecognizerWithMaxResultCount(0)
        }
        assertEquals("maxResultCount must be positive.", error.message)
    }

    @Test
    fun constructorsRejectNullCollaborators() {
        val downloadConditions = assertThrows(NullPointerException::class.java) {
            newRecognizerWithDownloadConditions(null)
        }
        assertEquals("downloadConditions", downloadConditions.message)

        val backend = assertThrows(NullPointerException::class.java) {
            MlKitJapaneseWritingRecognizer(null as MlKitJapaneseWritingRecognizer.RecognitionBackend?)
        }
        assertEquals("backend", backend.message)
    }
}

private fun newRecognizerWithMaxResultCount(maxResultCount: Int): MlKitJapaneseWritingRecognizer {
    return MlKitJapaneseWritingRecognizer(Runnable::run, maxResultCount, DownloadConditions.Builder().build())
}

private fun newRecognizerWithDownloadConditions(downloadConditions: DownloadConditions?): MlKitJapaneseWritingRecognizer {
    return MlKitJapaneseWritingRecognizer(Runnable::run, 1, downloadConditions)
}

private fun simpleWriting(): CapturedWriting {
    return CapturedWriting(listOf(stroke(point(1f, 1f))))
}

private fun stroke(vararg points: CapturedStroke.Point): CapturedStroke {
    return CapturedStroke(points.toList())
}

private fun point(x: Float, y: Float): CapturedStroke.Point {
    return CapturedStroke.Point(x, y)
}

private fun result(text: String, score: Float): MlKitRecognitionResult {
    return MlKitRecognitionResult(listOf(RecognitionCandidate(text, score)))
}

private fun assertPoint(point: Point, x: Float, y: Float, timestamp: Long?) {
    assertEquals(x, point.x, 0f)
    assertEquals(y, point.y, 0f)
    assertEquals(timestamp, point.timestamp)
}

private fun joinFailure(future: CompletableFuture<*>): Throwable {
    try {
        future.join()
        throw AssertionError("Expected future to fail.")
    } catch (error: CompletionException) {
        return error.cause ?: error
    } catch (error: CancellationException) {
        return error
    }
}

private class RecordingBackend : MlKitJapaneseWritingRecognizer.RecognitionBackend {
    var downloaded = false
    var statusCalls = 0
    var downloadCalls = 0
    var plainRecognitionCalls = 0
    var contextRecognitionCalls = 0
    var closeCalls = 0
    var plainInk: Ink? = null
    var contextInk: Ink? = null
    var context: RecognitionContext? = null
    var downloadTask: RecordingTask<Void> = RecordingTask.succeeded(null)
    var recognitionTask: RecordingTask<MlKitRecognitionResult> = RecordingTask.succeeded(result("火", 0.5f))

    override fun isModelDownloaded(): MlKitJapaneseWritingRecognizer.MlKitTask<Boolean> {
        statusCalls++
        return RecordingTask.succeeded(downloaded)
    }

    override fun downloadModel(): MlKitJapaneseWritingRecognizer.MlKitTask<Void> {
        downloadCalls++
        return downloadTask
    }

    override fun recognize(ink: Ink): MlKitJapaneseWritingRecognizer.MlKitTask<MlKitRecognitionResult> {
        plainRecognitionCalls++
        plainInk = ink
        return recognitionTask
    }

    override fun recognize(
        ink: Ink,
        context: RecognitionContext,
    ): MlKitJapaneseWritingRecognizer.MlKitTask<MlKitRecognitionResult> {
        contextRecognitionCalls++
        contextInk = ink
        this.context = context
        return recognitionTask
    }

    override fun close() {
        closeCalls++
    }
}

private class RecordingTask<T> : MlKitJapaneseWritingRecognizer.MlKitTask<T> {
    private var successExecutor: Executor? = null
    private var successListener: MlKitJapaneseWritingRecognizer.SuccessListener<in T>? = null
    private var failureExecutor: Executor? = null
    private var failureListener: MlKitJapaneseWritingRecognizer.FailureListener? = null
    private var canceledExecutor: Executor? = null
    private var canceledListener: Runnable? = null
    private var state = State.PENDING
    private var value: T? = null
    private var error: Exception? = null

    companion object {
        fun <T> succeeded(value: T?): RecordingTask<T> {
            return RecordingTask<T>().apply {
                succeed(value)
            }
        }
    }

    override fun addOnSuccessListener(
        executor: Executor,
        listener: MlKitJapaneseWritingRecognizer.SuccessListener<in T>,
    ) {
        successExecutor = executor
        successListener = listener
        if (state == State.SUCCESS) {
            dispatchSuccess()
        }
    }

    override fun addOnFailureListener(executor: Executor, listener: MlKitJapaneseWritingRecognizer.FailureListener) {
        failureExecutor = executor
        failureListener = listener
        if (state == State.FAILURE) {
            dispatchFailure()
        }
    }

    override fun addOnCanceledListener(executor: Executor, listener: Runnable) {
        canceledExecutor = executor
        canceledListener = listener
        if (state == State.CANCELED) {
            dispatchCanceled()
        }
    }

    fun succeed(nextValue: T?) {
        state = State.SUCCESS
        value = nextValue
        dispatchSuccess()
    }

    fun fail(nextError: Exception) {
        state = State.FAILURE
        error = nextError
        dispatchFailure()
    }

    fun cancel() {
        state = State.CANCELED
        dispatchCanceled()
    }

    private fun dispatchSuccess() {
        val executor = successExecutor ?: return
        val listener = successListener ?: return
        executor.execute {
            @Suppress("UNCHECKED_CAST")
            listener.onSuccess(value as T)
        }
    }

    private fun dispatchFailure() {
        val executor = failureExecutor ?: return
        val listener = failureListener ?: return
        val error = error ?: return
        executor.execute {
            listener.onFailure(error)
        }
    }

    private fun dispatchCanceled() {
        val executor = canceledExecutor ?: return
        val listener = canceledListener ?: return
        executor.execute(listener)
    }

    private enum class State {
        PENDING,
        SUCCESS,
        FAILURE,
        CANCELED,
    }
}

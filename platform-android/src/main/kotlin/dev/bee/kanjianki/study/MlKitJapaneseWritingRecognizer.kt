package dev.bee.kanjianki.study

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.common.RecognitionCandidate
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import java.util.Objects
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import com.google.mlkit.vision.digitalink.common.RecognitionResult as MlKitRecognitionResult

class MlKitJapaneseWritingRecognizer : WritingRecognizer {
    private val backend: RecognitionBackend

    constructor() : this(null, DEFAULT_MAX_RESULT_COUNT, DownloadConditions.Builder().build())

    constructor(recognitionExecutor: Executor?) : this(
        recognitionExecutor,
        DEFAULT_MAX_RESULT_COUNT,
        DownloadConditions.Builder().build()
    )

    constructor(
        recognitionExecutor: Executor?,
        maxResultCount: Int,
        downloadConditions: DownloadConditions?,
    ) {
        require(maxResultCount > 0) { "maxResultCount must be positive." }
        val conditions = Objects.requireNonNull<DownloadConditions>(downloadConditions, "downloadConditions")
        backend = GoogleRecognitionBackend.create(
            recognitionExecutor,
            maxResultCount,
            conditions
        )
    }

    constructor(backend: RecognitionBackend?) {
        this.backend = Objects.requireNonNull<RecognitionBackend>(backend, "backend")
    }

    override fun modelStatus(): CompletableFuture<WritingRecognizer.ModelStatus> {
        return TaskBridge.toFuture(backend.isModelDownloaded())
            .thenApply { downloaded ->
                status(
                    downloaded,
                    if (downloaded) {
                        "Handwriting checker is ready."
                    } else {
                        "Handwriting checker needs download."
                    }
                )
            }
    }

    override fun downloadModel(): CompletableFuture<WritingRecognizer.ModelStatus> {
        return modelStatus().thenCompose { status ->
            if (status.downloaded) {
                CompletableFuture.completedFuture(status)
            } else {
                TaskBridge.toFuture(backend.downloadModel())
                    .thenApply { status(true, "Handwriting checker downloaded.") }
            }
        }
    }

    override fun recognize(writing: CapturedWriting?): CompletableFuture<WritingRecognizer.RecognitionResult> {
        Objects.requireNonNull(writing, "writing")
        val prepared = if (writing!!.hasWritingArea()) {
            CapturedWriting.prepareForRecognition(
                writing.strokes,
                writing.writingAreaWidth!!,
                writing.writingAreaHeight!!
            )
        } else {
            writing
        }
        return modelStatus().thenCompose { status ->
            if (!status.downloaded) {
                failedFuture(IllegalStateException("Handwriting checker is not downloaded."))
            } else {
                val context = recognitionContext(prepared)
                if (context == null) {
                    TaskBridge.toFuture(backend.recognize(ink(prepared)))
                        .thenApply(MlKitJapaneseWritingRecognizer::result)
                } else {
                    TaskBridge.toFuture(backend.recognize(ink(prepared), context))
                        .thenApply(MlKitJapaneseWritingRecognizer::result)
                }
            }
        }
    }

    override fun close() {
        backend.close()
    }

    interface RecognitionBackend : AutoCloseable {
        fun isModelDownloaded(): MlKitTask<Boolean>

        fun downloadModel(): MlKitTask<Void>

        fun recognize(ink: Ink): MlKitTask<MlKitRecognitionResult>

        fun recognize(ink: Ink, context: RecognitionContext): MlKitTask<MlKitRecognitionResult>

        override fun close()
    }

    interface MlKitTask<T> {
        fun addOnSuccessListener(executor: Executor, listener: SuccessListener<in T>)

        fun addOnFailureListener(executor: Executor, listener: FailureListener)

        fun addOnCanceledListener(executor: Executor, listener: Runnable)
    }

    fun interface SuccessListener<T> {
        fun onSuccess(result: T)
    }

    fun interface FailureListener {
        fun onFailure(error: Exception)
    }

    object TaskBridge {
        @JvmStatic
        fun <T> toFuture(task: MlKitTask<T>): CompletableFuture<T> {
            val future = CompletableFuture<T>()
            task.addOnSuccessListener(DIRECT_EXECUTOR, SuccessListener { result -> future.complete(result) })
            task.addOnFailureListener(DIRECT_EXECUTOR, FailureListener { error ->
                future.completeExceptionally(error)
            })
            task.addOnCanceledListener(DIRECT_EXECUTOR, Runnable {
                future.completeExceptionally(
                    CancellationException("Handwriting checker task was canceled.")
                )
            })
            return future
        }
    }

    class GoogleRecognitionBackend private constructor(
        private val model: DigitalInkRecognitionModel,
        private val modelManager: RemoteModelManager,
        private val recognizer: DigitalInkRecognizer,
        private val downloadConditions: DownloadConditions,
    ) : RecognitionBackend {
        override fun isModelDownloaded(): MlKitTask<Boolean> {
            return GoogleTask(modelManager.isModelDownloaded(model))
        }

        override fun downloadModel(): MlKitTask<Void> {
            return GoogleTask(modelManager.download(model, downloadConditions))
        }

        override fun recognize(ink: Ink): MlKitTask<MlKitRecognitionResult> {
            return GoogleTask(recognizer.recognize(ink))
        }

        override fun recognize(ink: Ink, context: RecognitionContext): MlKitTask<MlKitRecognitionResult> {
            return GoogleTask(recognizer.recognize(ink, context))
        }

        override fun close() {
            recognizer.close()
        }

        companion object {
            @JvmStatic
            fun create(
                recognitionExecutor: Executor?,
                maxResultCount: Int,
                downloadConditions: DownloadConditions,
            ): GoogleRecognitionBackend {
                val model = DigitalInkRecognitionModel.builder(MODEL_IDENTIFIER).build()
                val options = DigitalInkRecognizerOptions.builder(model)
                    .setMaxResultCount(maxResultCount)
                if (recognitionExecutor != null) {
                    options.setExecutor(recognitionExecutor)
                }
                return GoogleRecognitionBackend(
                    model,
                    RemoteModelManager.getInstance(),
                    DigitalInkRecognition.getClient(options.build()),
                    downloadConditions
                )
            }
        }
    }

    class GoogleTask<T>(task: Task<T>) : MlKitTask<T> {
        private val task: Task<T> = Objects.requireNonNull(task, "task")

        override fun addOnSuccessListener(executor: Executor, listener: SuccessListener<in T>) {
            task.addOnSuccessListener(executor) { result -> listener.onSuccess(result) }
        }

        override fun addOnFailureListener(executor: Executor, listener: FailureListener) {
            task.addOnFailureListener(executor) { error -> listener.onFailure(error) }
        }

        override fun addOnCanceledListener(executor: Executor, listener: Runnable) {
            task.addOnCanceledListener(executor) { listener.run() }
        }
    }

    companion object {
        const val MODEL_NAME: String = "JA"
        private const val DEFAULT_MAX_RESULT_COUNT = 5
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }
        private val MODEL_IDENTIFIER = DigitalInkRecognitionModelIdentifier.JA

        @JvmStatic
        private fun status(downloaded: Boolean, message: String): WritingRecognizer.ModelStatus {
            return WritingRecognizer.ModelStatus(
                MODEL_NAME,
                MODEL_IDENTIFIER.languageTag,
                downloaded,
                message
            )
        }

        private fun ink(writing: CapturedWriting): Ink {
            val ink = Ink.builder()
            for (capturedStroke in writing.strokes) {
                val stroke = Ink.Stroke.builder()
                for (point in capturedStroke.points) {
                    if (point.timestampMillis == null) {
                        stroke.addPoint(Ink.Point.create(point.x, point.y))
                    } else {
                        stroke.addPoint(Ink.Point.create(point.x, point.y, point.timestampMillis!!))
                    }
                }
                ink.addStroke(stroke.build())
            }
            return ink.build()
        }

        private fun recognitionContext(writing: CapturedWriting): RecognitionContext? {
            if (!writing.hasRecognitionContext()) {
                return null
            }
            val builder = RecognitionContext.builder()
                .setPreContext(writing.preContext)
            if (writing.hasWritingArea()) {
                builder.setWritingArea(WritingArea(writing.writingAreaWidth!!, writing.writingAreaHeight!!))
            }
            return builder.build()
        }

        @JvmStatic
        fun result(result: MlKitRecognitionResult): WritingRecognizer.RecognitionResult {
            val candidates = ArrayList<WritingRecognizer.Candidate>()
            for (candidate: RecognitionCandidate in result.candidates) {
                candidates.add(WritingRecognizer.Candidate(candidate.text, candidate.score))
            }
            return WritingRecognizer.RecognitionResult(candidates)
        }

        private fun <T> failedFuture(error: Throwable): CompletableFuture<T> {
            val future = CompletableFuture<T>()
            future.completeExceptionally(error)
            return future
        }
    }
}

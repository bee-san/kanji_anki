package dev.bee.kanjianki.study;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.common.RecognitionCandidate;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.recognition.Ink;
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext;
import com.google.mlkit.vision.digitalink.recognition.WritingArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MlKitJapaneseWritingRecognizer implements WritingRecognizer {
    public static final String MODEL_NAME = "JA";
    private static final int DEFAULT_MAX_RESULT_COUNT = 5;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final DigitalInkRecognitionModelIdentifier MODEL_IDENTIFIER = DigitalInkRecognitionModelIdentifier.JA;

    private final DigitalInkRecognitionModel model;
    private final RemoteModelManager modelManager;
    private final DigitalInkRecognizer recognizer;
    private final DownloadConditions downloadConditions;

    public MlKitJapaneseWritingRecognizer() {
        this(null, DEFAULT_MAX_RESULT_COUNT, new DownloadConditions.Builder().build());
    }

    public MlKitJapaneseWritingRecognizer(Executor recognitionExecutor) {
        this(recognitionExecutor, DEFAULT_MAX_RESULT_COUNT, new DownloadConditions.Builder().build());
    }

    public MlKitJapaneseWritingRecognizer(Executor recognitionExecutor, int maxResultCount, DownloadConditions downloadConditions) {
        if (maxResultCount <= 0) {
            throw new IllegalArgumentException("maxResultCount must be positive.");
        }
        this.model = DigitalInkRecognitionModel.builder(MODEL_IDENTIFIER).build();
        this.modelManager = RemoteModelManager.getInstance();
        this.downloadConditions = Objects.requireNonNull(downloadConditions, "downloadConditions");

        DigitalInkRecognizerOptions.Builder options = DigitalInkRecognizerOptions.builder(model)
                .setMaxResultCount(maxResultCount);
        if (recognitionExecutor != null) {
            options.setExecutor(recognitionExecutor);
        }
        this.recognizer = DigitalInkRecognition.getClient(options.build());
    }

    @Override
    public CompletableFuture<ModelStatus> modelStatus() {
        return toFuture(modelManager.isModelDownloaded(model))
                .thenApply(downloaded -> status(downloaded, downloaded
                        ? "Handwriting checker is ready."
                        : "Handwriting checker needs download."));
    }

    @Override
    public CompletableFuture<ModelStatus> downloadModel() {
        return modelStatus().thenCompose(status -> {
            if (status.downloaded) {
                return CompletableFuture.completedFuture(status);
            }
            return toFuture(modelManager.download(model, downloadConditions))
                    .thenApply(ignored -> status(true, "Handwriting checker downloaded."));
        });
    }

    @Override
    public CompletableFuture<RecognitionResult> recognize(CapturedWriting writing) {
        Objects.requireNonNull(writing, "writing");
        CapturedWriting prepared = writing.hasWritingArea()
                ? CapturedWriting.prepareForRecognition(writing.strokes, writing.writingAreaWidth, writing.writingAreaHeight)
                : writing;
        return modelStatus().thenCompose(status -> {
            if (!status.downloaded) {
                return failedFuture(new IllegalStateException("Handwriting checker is not downloaded."));
            }
            Task<com.google.mlkit.vision.digitalink.common.RecognitionResult> task;
            RecognitionContext context = recognitionContext(prepared);
            if (context == null) {
                task = recognizer.recognize(ink(prepared));
            } else {
                task = recognizer.recognize(ink(prepared), context);
            }
            return toFuture(task).thenApply(MlKitJapaneseWritingRecognizer::result);
        });
    }

    @Override
    public void close() {
        recognizer.close();
    }

    private static ModelStatus status(boolean downloaded, String message) {
        return new ModelStatus(MODEL_NAME, MODEL_IDENTIFIER.getLanguageTag(), downloaded, message);
    }

    private static Ink ink(CapturedWriting writing) {
        Ink.Builder ink = Ink.builder();
        for (CapturedStroke capturedStroke : writing.strokes) {
            Ink.Stroke.Builder stroke = Ink.Stroke.builder();
            for (CapturedStroke.Point point : capturedStroke.points) {
                if (point.timestampMillis == null) {
                    stroke.addPoint(Ink.Point.create(point.x, point.y));
                } else {
                    stroke.addPoint(Ink.Point.create(point.x, point.y, point.timestampMillis));
                }
            }
            ink.addStroke(stroke.build());
        }
        return ink.build();
    }

    private static RecognitionContext recognitionContext(CapturedWriting writing) {
        if (!writing.hasRecognitionContext()) {
            return null;
        }
        RecognitionContext.Builder builder = RecognitionContext.builder()
                .setPreContext(writing.preContext);
        if (writing.hasWritingArea()) {
            builder.setWritingArea(new WritingArea(writing.writingAreaWidth, writing.writingAreaHeight));
        }
        return builder.build();
    }

    private static RecognitionResult result(com.google.mlkit.vision.digitalink.common.RecognitionResult result) {
        List<Candidate> candidates = new ArrayList<>();
        for (RecognitionCandidate candidate : result.getCandidates()) {
            candidates.add(new Candidate(candidate.getText(), candidate.getScore()));
        }
        return new RecognitionResult(candidates);
    }

    private static <T> CompletableFuture<T> toFuture(Task<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        task.addOnSuccessListener(DIRECT_EXECUTOR, future::complete);
        task.addOnFailureListener(DIRECT_EXECUTOR, future::completeExceptionally);
        task.addOnCanceledListener(DIRECT_EXECUTOR, () ->
                future.completeExceptionally(new CancellationException("Handwriting checker task was canceled.")));
        return future;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}

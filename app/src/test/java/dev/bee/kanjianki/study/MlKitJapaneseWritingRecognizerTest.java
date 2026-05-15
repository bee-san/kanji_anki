package dev.bee.kanjianki.study;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.vision.digitalink.common.Point;
import com.google.mlkit.vision.digitalink.common.RecognitionCandidate;
import com.google.mlkit.vision.digitalink.common.Stroke;
import com.google.mlkit.vision.digitalink.recognition.Ink;
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class MlKitJapaneseWritingRecognizerTest {
    @Test
    public void modelStatusReportsDownloadedAndMissingStates() {
        RecordingBackend backend = new RecordingBackend();
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);

        WritingRecognizer.ModelStatus missing = recognizer.modelStatus().join();
        backend.downloaded = true;
        WritingRecognizer.ModelStatus ready = recognizer.modelStatus().join();

        assertEquals("JA", missing.modelName);
        assertEquals("ja", missing.languageTag);
        assertFalse(missing.downloaded);
        assertEquals("Handwriting checker needs download.", missing.message);
        assertTrue(ready.downloaded);
        assertEquals("Handwriting checker is ready.", ready.message);
        assertEquals(2, backend.statusCalls);
    }

    @Test
    public void downloadModelShortCircuitsWhenModelIsAlreadyDownloaded() {
        RecordingBackend backend = new RecordingBackend();
        backend.downloaded = true;
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);

        WritingRecognizer.ModelStatus status = recognizer.downloadModel().join();

        assertTrue(status.downloaded);
        assertEquals("Handwriting checker is ready.", status.message);
        assertEquals(1, backend.statusCalls);
        assertEquals(0, backend.downloadCalls);
    }

    @Test
    public void downloadModelDownloadsWhenModelIsMissing() {
        RecordingBackend backend = new RecordingBackend();
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);

        WritingRecognizer.ModelStatus status = recognizer.downloadModel().join();

        assertTrue(status.downloaded);
        assertEquals("Handwriting checker downloaded.", status.message);
        assertEquals(1, backend.statusCalls);
        assertEquals(1, backend.downloadCalls);
    }

    @Test
    public void recognizeFailsBeforeCallingMlKitWhenModelIsMissing() {
        RecordingBackend backend = new RecordingBackend();
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);

        CompletableFuture<WritingRecognizer.RecognitionResult> result = recognizer.recognize(simpleWriting());
        Throwable failure = joinFailure(result);

        assertTrue(failure instanceof IllegalStateException);
        assertEquals("Handwriting checker is not downloaded.", failure.getMessage());
        assertEquals(1, backend.statusCalls);
        assertEquals(0, backend.plainRecognitionCalls);
        assertEquals(0, backend.contextRecognitionCalls);
    }

    @Test
    public void recognizeUsesPlainRequestAndConvertsCapturedPoints() {
        RecordingBackend backend = new RecordingBackend();
        backend.downloaded = true;
        backend.recognitionTask = RecordingTask.succeeded(result("水", 0.92f));
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);
        CapturedWriting writing = new CapturedWriting(Collections.singletonList(stroke(
                point(1.5f, 2.5f),
                new CapturedStroke.Point(3.5f, 4.5f, 70L)
        )));

        WritingRecognizer.RecognitionResult result = recognizer.recognize(writing).join();

        assertEquals("水", result.topText());
        assertEquals(Float.valueOf(0.92f), result.candidates.get(0).score);
        assertEquals(1, backend.plainRecognitionCalls);
        assertEquals(0, backend.contextRecognitionCalls);
        Stroke stroke = backend.plainInk.getStrokes().get(0);
        assertEquals(2, stroke.getPointsInGlobalCoordinates().size());
        assertPoint(stroke.getPointsInGlobalCoordinates().get(0), 1.5f, 2.5f, null);
        assertPoint(stroke.getPointsInGlobalCoordinates().get(1), 3.5f, 4.5f, 70L);
    }

    @Test
    public void recognizeUsesContextRequestWhenPreContextIsPresent() {
        RecordingBackend backend = new RecordingBackend();
        backend.downloaded = true;
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);
        CapturedWriting writing = new CapturedWriting(
                Collections.singletonList(stroke(point(1f, 1f))),
                null,
                null,
                "before"
        );

        recognizer.recognize(writing).join();

        assertEquals(0, backend.plainRecognitionCalls);
        assertEquals(1, backend.contextRecognitionCalls);
        assertEquals("before", backend.context.getPreContext());
        assertNull(backend.context.getWritingArea());
    }

    @Test
    public void recognizePreparesWritingAreaContext() {
        RecordingBackend backend = new RecordingBackend();
        backend.downloaded = true;
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);
        CapturedWriting writing = new CapturedWriting(
                Collections.singletonList(stroke(
                        new CapturedStroke.Point(10f, 10f, 10L),
                        new CapturedStroke.Point(20f, 20f, 20L)
                )),
                300f,
                200f,
                "discarded by existing preparation path"
        );

        recognizer.recognize(writing).join();

        assertEquals(0, backend.plainRecognitionCalls);
        assertEquals(1, backend.contextRecognitionCalls);
        assertNotNull(backend.context.getWritingArea());
        assertEquals("", backend.context.getPreContext());
        assertEquals(1000f, backend.context.getWritingArea().getWidth(), 0f);
        assertEquals(1000f, backend.context.getWritingArea().getHeight(), 0f);
        Stroke stroke = backend.contextInk.getStrokes().get(0);
        assertPoint(stroke.getPointsInGlobalCoordinates().get(0), 140f, 140f, 10L);
        assertPoint(stroke.getPointsInGlobalCoordinates().get(1), 860f, 860f, 20L);
    }

    @Test
    public void taskBridgeCompletesOnSuccessFailureAndCancellation() {
        RecordingTask<String> success = new RecordingTask<>();
        CompletableFuture<String> successFuture = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(success);
        success.succeed("done");
        assertEquals("done", successFuture.join());

        RecordingTask<String> failure = new RecordingTask<>();
        CompletableFuture<String> failureFuture = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(failure);
        IllegalArgumentException error = new IllegalArgumentException("bad task");
        failure.fail(error);
        assertSame(error, joinFailure(failureFuture));

        RecordingTask<String> canceled = new RecordingTask<>();
        CompletableFuture<String> canceledFuture = MlKitJapaneseWritingRecognizer.TaskBridge.toFuture(canceled);
        canceled.cancel();
        Throwable cancelFailure = joinFailure(canceledFuture);
        assertTrue(cancelFailure instanceof CancellationException);
        assertEquals("Handwriting checker task was canceled.", cancelFailure.getMessage());
    }

    @Test
    public void closeDelegatesToBackend() {
        RecordingBackend backend = new RecordingBackend();
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer(backend);

        recognizer.close();

        assertEquals(1, backend.closeCalls);
    }

    @Test
    public void constructorRejectsNonPositiveMaxResultCount() {
        try {
            new MlKitJapaneseWritingRecognizer(Runnable::run, 0, new DownloadConditions.Builder().build());
            fail("Expected maxResultCount validation failure.");
        } catch (IllegalArgumentException error) {
            assertEquals("maxResultCount must be positive.", error.getMessage());
        }
    }

    @Test
    public void constructorsRejectNullCollaborators() {
        try {
            new MlKitJapaneseWritingRecognizer(Runnable::run, 1, null);
            fail("Expected download condition validation failure.");
        } catch (NullPointerException error) {
            assertEquals("downloadConditions", error.getMessage());
        }

        try {
            new MlKitJapaneseWritingRecognizer((MlKitJapaneseWritingRecognizer.RecognitionBackend) null);
            fail("Expected backend validation failure.");
        } catch (NullPointerException error) {
            assertEquals("backend", error.getMessage());
        }
    }

    private static CapturedWriting simpleWriting() {
        return new CapturedWriting(Collections.singletonList(stroke(point(1f, 1f))));
    }

    private static CapturedStroke stroke(CapturedStroke.Point... points) {
        return new CapturedStroke(Arrays.asList(points));
    }

    private static CapturedStroke.Point point(float x, float y) {
        return new CapturedStroke.Point(x, y);
    }

    private static com.google.mlkit.vision.digitalink.common.RecognitionResult result(String text, float score) {
        return new com.google.mlkit.vision.digitalink.common.RecognitionResult(Collections.singletonList(
                new RecognitionCandidate(text, score)
        ));
    }

    private static void assertPoint(Point point, float x, float y, Long timestamp) {
        assertEquals(x, point.getX(), 0f);
        assertEquals(y, point.getY(), 0f);
        assertEquals(timestamp, point.getTimestamp());
    }

    private static Throwable joinFailure(CompletableFuture<?> future) {
        try {
            future.join();
            fail("Expected future to fail.");
            return null;
        } catch (CompletionException error) {
            return error.getCause();
        } catch (CancellationException error) {
            return error;
        }
    }

    private static final class RecordingBackend implements MlKitJapaneseWritingRecognizer.RecognitionBackend {
        boolean downloaded;
        int statusCalls;
        int downloadCalls;
        int plainRecognitionCalls;
        int contextRecognitionCalls;
        int closeCalls;
        Ink plainInk;
        Ink contextInk;
        RecognitionContext context;
        RecordingTask<Void> downloadTask = RecordingTask.succeeded(null);
        RecordingTask<com.google.mlkit.vision.digitalink.common.RecognitionResult> recognitionTask =
                RecordingTask.succeeded(result("火", 0.5f));

        @Override
        public MlKitJapaneseWritingRecognizer.MlKitTask<Boolean> isModelDownloaded() {
            statusCalls++;
            return RecordingTask.succeeded(downloaded);
        }

        @Override
        public MlKitJapaneseWritingRecognizer.MlKitTask<Void> downloadModel() {
            downloadCalls++;
            return downloadTask;
        }

        @Override
        public MlKitJapaneseWritingRecognizer.MlKitTask<com.google.mlkit.vision.digitalink.common.RecognitionResult> recognize(Ink ink) {
            plainRecognitionCalls++;
            plainInk = ink;
            return recognitionTask;
        }

        @Override
        public MlKitJapaneseWritingRecognizer.MlKitTask<com.google.mlkit.vision.digitalink.common.RecognitionResult> recognize(
                Ink ink,
                RecognitionContext context
        ) {
            contextRecognitionCalls++;
            contextInk = ink;
            this.context = context;
            return recognitionTask;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static final class RecordingTask<T> implements MlKitJapaneseWritingRecognizer.MlKitTask<T> {
        private Executor successExecutor;
        private MlKitJapaneseWritingRecognizer.SuccessListener<? super T> successListener;
        private Executor failureExecutor;
        private MlKitJapaneseWritingRecognizer.FailureListener failureListener;
        private Executor canceledExecutor;
        private Runnable canceledListener;
        private State state = State.PENDING;
        private T value;
        private Exception error;

        static <T> RecordingTask<T> succeeded(T value) {
            RecordingTask<T> task = new RecordingTask<>();
            task.succeed(value);
            return task;
        }

        @Override
        public void addOnSuccessListener(
                Executor executor,
                MlKitJapaneseWritingRecognizer.SuccessListener<? super T> listener
        ) {
            successExecutor = executor;
            successListener = listener;
            if (state == State.SUCCESS) {
                dispatchSuccess();
            }
        }

        @Override
        public void addOnFailureListener(Executor executor, MlKitJapaneseWritingRecognizer.FailureListener listener) {
            failureExecutor = executor;
            failureListener = listener;
            if (state == State.FAILURE) {
                dispatchFailure();
            }
        }

        @Override
        public void addOnCanceledListener(Executor executor, Runnable listener) {
            canceledExecutor = executor;
            canceledListener = listener;
            if (state == State.CANCELED) {
                dispatchCanceled();
            }
        }

        void succeed(T nextValue) {
            state = State.SUCCESS;
            value = nextValue;
            dispatchSuccess();
        }

        void fail(Exception nextError) {
            state = State.FAILURE;
            error = nextError;
            dispatchFailure();
        }

        void cancel() {
            state = State.CANCELED;
            dispatchCanceled();
        }

        private void dispatchSuccess() {
            if (successListener != null) {
                successExecutor.execute(() -> successListener.onSuccess(value));
            }
        }

        private void dispatchFailure() {
            if (failureListener != null) {
                failureExecutor.execute(() -> failureListener.onFailure(error));
            }
        }

        private void dispatchCanceled() {
            if (canceledListener != null) {
                canceledExecutor.execute(canceledListener);
            }
        }
    }

    private enum State {
        PENDING,
        SUCCESS,
        FAILURE,
        CANCELED
    }
}

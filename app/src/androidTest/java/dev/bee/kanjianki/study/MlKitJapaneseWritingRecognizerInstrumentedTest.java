package dev.bee.kanjianki.study;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.vision.digitalink.recognition.Ink;
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class MlKitJapaneseWritingRecognizerInstrumentedTest {
    @Test
    public void defaultConstructorCreatesAndClosesGoogleRecognizer() {
        MlKitJapaneseWritingRecognizer recognizer = new MlKitJapaneseWritingRecognizer();

        try {
            assertNotNull(recognizer);
        } finally {
            recognizer.close();
        }
    }

    @Test
    public void googleBackendWrapsDownloadAndRecognitionTasks() {
        MlKitJapaneseWritingRecognizer.GoogleRecognitionBackend backend =
                MlKitJapaneseWritingRecognizer.GoogleRecognitionBackend.create(
                        Runnable::run,
                        3,
                        new DownloadConditions.Builder().build()
                );
        Ink ink = Ink.builder()
                .addStroke(Ink.Stroke.builder()
                        .addPoint(Ink.Point.create(1f, 1f))
                        .addPoint(Ink.Point.create(2f, 2f, 20L))
                        .build())
                .build();
        RecognitionContext context = RecognitionContext.builder()
                .setPreContext("前")
                .build();

        try {
            assertNotNull(backend.downloadModel());
            assertNotNull(backend.recognize(ink));
            assertNotNull(backend.recognize(ink, context));
        } finally {
            backend.close();
        }
    }
}

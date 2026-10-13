package dev.bee.kanjianki.study

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MlKitJapaneseWritingRecognizerInstrumentedTest {
    @Test
    fun defaultConstructorCreatesAndClosesGoogleRecognizer() {
        MlKitJapaneseWritingRecognizer().use { recognizer ->
            assertNotNull(recognizer)
        }
    }

    @Test
    fun googleBackendWrapsDownloadAndRecognitionTasks() {
        val backend = MlKitJapaneseWritingRecognizer.GoogleRecognitionBackend.create(
            Runnable::run,
            3,
            DownloadConditions.Builder().build(),
        )
        val ink = Ink.builder()
            .addStroke(
                Ink.Stroke.builder()
                    .addPoint(Ink.Point.create(1f, 1f))
                    .addPoint(Ink.Point.create(2f, 2f, 20L))
                    .build(),
            )
            .build()
        val context = RecognitionContext.builder()
            .setPreContext("前")
            .build()

        backend.use { recognizer ->
            assertNotNull(recognizer.downloadModel())
            assertNotNull(recognizer.recognize(ink))
            assertNotNull(recognizer.recognize(ink, context))
        }
    }
}

package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeDiagnosisFormatterTest {
    @Test
    fun formatsCurrentTutorDiagnosisLinesExactly() {
        val diagnosis = StrokeDiagnosis.builder()
            .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
            .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 2)
            .add(StrokeDiagnosis.Label.MISSING_STROKE, 3)
            .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 4)
            .add(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 0)
            .build()

        assertEquals(
            listOf(
                "Stroke 1: likely wrong order",
                "Stroke 2: likely wrong direction",
                "Stroke 3: may be missing",
                "Stroke 4: shape looks rough",
                "Recognized, but the stroke path was messy",
            ).joinToString("\n"),
            StrokeDiagnosisFormatter.text(analysisWith(diagnosis, WritingAnalysis.Status.CLOSE)),
        )
    }

    @Test
    fun hidesDiagnosisForNonActionableAnalysisStates() {
        val diagnosis = StrokeDiagnosis.builder()
            .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
            .build()

        assertFalse(StrokeDiagnosisFormatter.canShow(null))
        assertFalse(
            StrokeDiagnosisFormatter.canShow(
                WritingAnalysis(
                    WritingAnalysis.Status.PASS,
                    "good",
                    true,
                    "",
                    emptyList<RecognitionCandidate>(),
                    null,
                ),
            ),
        )
        assertFalse(StrokeDiagnosisFormatter.canShow(analysisWith(StrokeDiagnosis.empty(), WritingAnalysis.Status.PASS)))
        assertFalse(
            StrokeDiagnosisFormatter.canShow(
                WritingAnalysis(
                    WritingAnalysis.Status.NO_INK,
                    "again",
                    false,
                    "",
                    emptyList<RecognitionCandidate>(),
                    cleanResult(diagnosis),
                ),
            ),
        )
        assertFalse(
            StrokeDiagnosisFormatter.canShow(
                WritingAnalysis(
                    WritingAnalysis.Status.MODEL_UNAVAILABLE,
                    "again",
                    false,
                    "",
                    emptyList<RecognitionCandidate>(),
                    cleanResult(diagnosis),
                ),
            ),
        )
        assertFalse(
            StrokeDiagnosisFormatter.canShow(
                WritingAnalysis(
                    WritingAnalysis.Status.NO_STROKE_DATA,
                    "again",
                    false,
                    "",
                    emptyList<RecognitionCandidate>(),
                    cleanResult(diagnosis),
                ),
            ),
        )
        assertFalse(
            StrokeDiagnosisFormatter.canShow(
                WritingAnalysis(
                    WritingAnalysis.Status.RECOGNITION_ERROR,
                    "again",
                    false,
                    "",
                    emptyList<RecognitionCandidate>(),
                    cleanResult(diagnosis),
                ),
            ),
        )
        assertFalse(
            StrokeDiagnosisFormatter.canShow(
                WritingAnalysis(
                    WritingAnalysis.Status.CLOSE,
                    "hard",
                    true,
                    "",
                    emptyList<RecognitionCandidate>(),
                    StrokeOrderEvaluator.evaluate(null, sample()),
                ),
            ),
        )
    }

    @Test
    fun showsDiagnosisForPassCloseAndWrongWhenGuideAndDiagnosisExist() {
        val diagnosis = StrokeDiagnosis.builder()
            .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 1)
            .build()

        assertTrue(StrokeDiagnosisFormatter.canShow(analysisWith(diagnosis, WritingAnalysis.Status.PASS)))
        assertTrue(StrokeDiagnosisFormatter.canShow(analysisWith(diagnosis, WritingAnalysis.Status.CLOSE)))
        assertTrue(StrokeDiagnosisFormatter.canShow(analysisWith(diagnosis, WritingAnalysis.Status.WRONG)))
    }

    @Test
    fun lineHelpersHandleNulls() {
        assertEquals("", StrokeDiagnosisFormatter.line(null))
        assertEquals("", StrokeDiagnosisFormatter.strokeLine(null, "ignored"))
    }

    private fun analysisWith(diagnosis: StrokeDiagnosis, status: WritingAnalysis.Status): WritingAnalysis {
        return WritingAnalysis(
            status,
            "hard",
            status != WritingAnalysis.Status.WRONG,
            "",
            emptyList<RecognitionCandidate>(),
            cleanResult(diagnosis),
        )
    }

    private fun cleanResult(diagnosis: StrokeDiagnosis): StrokeOrderEvaluator.StrokeOrderResult {
        return StrokeOrderEvaluator.evaluate(guide(), sample()).withDiagnosis(diagnosis)
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(
                InkStroke(listOf(InkPoint(0.1f, 0.1f, 0), InkPoint(0.9f, 0.1f, 1))),
                InkStroke(listOf(InkPoint(0.1f, 0.3f, 0), InkPoint(0.9f, 0.3f, 1))),
            ),
        )
    }

    private fun sample(): WritingSample {
        return WritingSample(
            listOf(
                InkStroke(listOf(InkPoint(10f, 10f, 0), InkPoint(90f, 10f, 1))),
                InkStroke(listOf(InkPoint(10f, 30f, 0), InkPoint(90f, 30f, 1))),
            ),
            100f,
            100f,
        )
    }
}

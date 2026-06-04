package dev.bee.kanjianki.study

import dev.bee.kanjianki.core.study.InkPoint
import dev.bee.kanjianki.core.study.InkStroke
import dev.bee.kanjianki.core.study.RecognitionCandidate
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.core.study.WritingAnalysisEngine
import dev.bee.kanjianki.core.study.WritingSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWritingModelsTest {
    @Test
    fun recognitionCandidatesCanDriveCoreWritingAnalysis() {
        val result = WritingRecognizer.RecognitionResult(
            listOf(
                WritingRecognizer.Candidate("校", 0.61f),
                WritingRecognizer.Candidate(" 拉\uFE0F ", 0.94f),
            ),
        )

        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            writingSample(),
            strokeGuide(),
            recognitionCandidates(result),
        )

        assertEquals("校", result.topText())
        assertEquals(WritingAnalysis.Status.PASS, analysis.status)
        assertTrue(analysis.writingPassed)
        assertEquals("good", analysis.rating)
        assertEquals(2, analysis.candidates.size)
        assertEquals(" 拉\uFE0F ", analysis.candidates[1].text)
    }

    private fun recognitionCandidates(result: WritingRecognizer.RecognitionResult): List<RecognitionCandidate> {
        return result.candidates.map { RecognitionCandidate(it.text, it.score) }
    }

    private fun strokeGuide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(
                inkStroke(0.1f, 0.1f, 0.9f, 0.1f),
                inkStroke(0.1f, 0.3f, 0.9f, 0.3f),
            ),
        )
    }

    private fun writingSample(): WritingSample {
        return WritingSample(
            listOf(
                inkStroke(10f, 10f, 90f, 10f),
                inkStroke(10f, 30f, 90f, 30f),
            ),
            100f,
            100f,
        )
    }

    private fun inkStroke(x1: Float, y1: Float, x2: Float, y2: Float): InkStroke {
        return InkStroke(listOf(InkPoint(x1, y1, 0L), InkPoint(x2, y2, 1L)))
    }
}

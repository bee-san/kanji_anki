package dev.bee.kanjianki.study

import dev.bee.kanjianki.core.study.RecognitionCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingRecognizerTest {
    @Test
    fun modelStatusStoresDownloadState() {
        val status = WritingRecognizer.ModelStatus(
            "Digital Ink",
            "ja-JP",
            true,
            "Ready"
        )

        assertEquals("Digital Ink", status.modelName)
        assertEquals("ja-JP", status.languageTag)
        assertTrue(status.downloaded)
        assertEquals("Ready", status.message)
    }

    @Test
    fun recognitionResultAndCandidateHandleEmptyMlKitOutputSafely() {
        val empty = WritingRecognizer.RecognitionResult(emptyList())
        val candidate = WritingRecognizer.Candidate(null, 0.4f)

        assertEquals("", empty.topText())
        assertEquals("", candidate.text)
        assertEquals(java.lang.Float.valueOf(0.4f), candidate.score)
    }

    @Test
    fun recognitionResultCopiesAndFreezesCandidates() {
        val source = arrayListOf(WritingRecognizer.Candidate("校", 0.61f))

        val result = WritingRecognizer.RecognitionResult(source)
        source.add(WritingRecognizer.Candidate("拉", 0.94f))

        assertEquals("校", result.topText())
        assertEquals(1, result.candidates.size)
        val appendedCandidate = WritingRecognizer.Candidate("雑", null)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.candidates as MutableList<WritingRecognizer.Candidate>).add(appendedCandidate)
        }
    }

    @Test
    fun recognitionResultReturnsFirstCandidateText() {
        val result = WritingRecognizer.RecognitionResult(
            listOf(
                WritingRecognizer.Candidate("校", 0.61f),
                WritingRecognizer.Candidate("拉", 0.94f)
            )
        )

        assertEquals("校", result.topText())
    }

    @Test
    fun recognitionCandidatesConvertToAnalysisCandidates() {
        val result = WritingRecognizer.RecognitionResult(
            listOf(
                WritingRecognizer.Candidate("校", 0.61f),
                WritingRecognizer.Candidate("拉", null)
            )
        )

        val candidates = WritingRecognizer.recognitionCandidates(result)
        val nullResult = WritingRecognizer.recognitionCandidates(null)

        assertEquals(2, candidates.size)
        assertEquals("校", candidates[0].text)
        assertEquals(java.lang.Float.valueOf(0.61f), candidates[0].score)
        assertEquals("拉", candidates[1].text)
        assertNull(candidates[1].score)
        assertEquals(0, nullResult.size)
    }
}

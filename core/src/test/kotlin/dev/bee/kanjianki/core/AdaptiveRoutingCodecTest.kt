package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRoutingCodecTest {
    @Test
    fun answerEvidenceRoundTripsEscapedExactAnswerDetails() {
        val evidence = AnswerEvidence(
            coreSkill = CoreSkill.CONTEXTUAL_READING,
            failureKind = FailureKind.WRONG_READING,
            evidenceSource = EvidenceSource.OBJECTIVE_CHOICE,
            presentationVariant = PresentationVariant.SENTENCE_CONTEXT,
            selectedAnswer = "だつ\nしゅつ",
            correctAnswer = "だっ\"しゅつ",
            renderedExpression = "脱出\\する",
            renderedReading = "脱出[だっしゅつ]",
            confusedWith = "脱\u0001出",
        )

        val encoded = AnswerEvidenceCodec.encode(evidence)

        assertTrue(encoded.startsWith("{\"v\":1,"))
        assertFalse(encoded.contains('\n'))
        assertEquals(evidence, AnswerEvidenceCodec.decode(encoded))
    }

    @Test
    fun adaptiveRouteStateRoundTripsEveryPersistedRoutingFact() {
        val evidence = AnswerEvidence(
            CoreSkill.RECOGNITION,
            FailureKind.VISUAL_CONFUSION,
            EvidenceSource.SELF_REPORT,
            PresentationVariant.FONT_GLYPH,
            "提",
            "拉",
            "拉",
            "らつ",
            "提",
        )
        val state = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            recognitionReviewCount = 4,
            contextualReadingReviewCount = 2,
            activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.WRITE_KANJI),
            repairTaskIndex = 1,
            repairStepMinutes = listOf(10, 60),
            repairDueAtMillis = 1_000L,
            coreDueAtMillis = 2_000L,
            recurringFailure = FailureKind.VISUAL_CONFUSION,
            recurringFailureCount = 3,
            repairAttemptCount = 2,
            repairStartedAtMillis = 500L,
            revalidationPending = true,
            answerEvidence = evidence,
        )

        assertEquals(state, AdaptiveRouteStateCodec.decode(AdaptiveRouteStateCodec.encode(state)))
    }

    @Test
    fun codecsFailOpenForBlankMalformedAndFutureVersions() {
        assertEquals("", AnswerEvidenceCodec.encode(null))
        assertEquals("", AdaptiveRouteStateCodec.encode(null))
        assertNull(AnswerEvidenceCodec.decode(null))
        assertNull(AnswerEvidenceCodec.decode("{"))
        assertNull(AnswerEvidenceCodec.decode("{\"v\":2}"))
        assertNull(AdaptiveRouteStateCodec.decode(""))
        assertNull(AdaptiveRouteStateCodec.decode("{\"v\":1,\"c\":\"future\"}"))
        assertNull(AdaptiveRouteStateCodec.decode("{\"v\":2,\"c\":\"recognition\"}"))
    }

    @Test
    fun decoderIgnoresUnknownFieldsAndSanitizesInvalidCounters() {
        val decoded = AdaptiveRouteStateCodec.decode(
            "{\"v\":1,\"c\":\"recognition\",\"rr\":-9,\"t\":[\"meaning_kanji\"]," +
                "\"i\":99,\"m\":[0,-1,10],\"f\":\"future_failure\",\"n\":2,\"future\":true}",
        )

        assertEquals(0, decoded!!.recognitionReviewCount)
        assertEquals(0, decoded.repairTaskIndex)
        assertEquals(listOf(10), decoded.repairStepMinutes)
        assertEquals(FailureKind.UNKNOWN, decoded.recurringFailure)
        assertEquals(2, decoded.recurringFailureCount)
    }
}

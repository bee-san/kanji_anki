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
    fun adaptiveRouteRejectsUnknownRepairTaskWires() {
        assertNull(
            AdaptiveRouteStateCodec.decode(
                "{\"v\":1,\"c\":\"recognition\",\"t\":[\"future_repair\"]}",
            ),
        )
        assertNull(
            AdaptiveRouteStateCodec.decode(
                "{\"v\":1,\"c\":\"recognition\",\"t\":[\"meaning_kanji\",\"future_repair\"]}",
            ),
        )

        assertEquals(
            emptyList<String>(),
            AdaptiveRouteStateCodec.decode(
                "{\"v\":1,\"c\":\"recognition\",\"t\":[\"\"]}",
            )!!.activeRepairTasks,
        )
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

    @Test
    fun stringListRoundTripsAndEscapes() {
        val encoded = StringListJsonCodec.encode(listOf("split", "裂\"れつ", "破\\裂"))
        assertEquals(listOf("split", "裂\"れつ", "破\\裂"), StringListJsonCodec.decode(encoded))
    }

    @Test
    fun stringListTrimsBlankEntriesAndDeduplicates() {
        val encoded = StringListJsonCodec.encode(listOf("  pain  ", "", "pain", "escape"))
        assertEquals(listOf("pain", "escape"), StringListJsonCodec.decode(encoded))
    }

    @Test
    fun stringListDecodesEmptyForNullBlankAndMalformedInput() {
        assertTrue(StringListJsonCodec.decode(null).isEmpty())
        assertTrue(StringListJsonCodec.decode("").isEmpty())
        assertTrue(StringListJsonCodec.decode("   ").isEmpty())
        assertTrue(StringListJsonCodec.decode("[not valid").isEmpty())
        assertTrue(StringListJsonCodec.decode("[\"a\"] trailing").isEmpty())
        assertEquals(listOf("a"), StringListJsonCodec.decode("[\"a\"]"))
    }

    @Test
    fun stringListEncodesAnEmptyListAsAnEmptyArray() {
        assertEquals("[]", StringListJsonCodec.encode(emptyList()))
        assertTrue(StringListJsonCodec.decode("[]").isEmpty())
    }
}

package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.testing.CrossProviderSnapshotSpec
import dev.bee.kanjianki.syncdomain.ProviderCardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the AnkiConnect reader to the shared cross-provider snapshot spec.
 *
 * The AnkiDroid half of this pairing lives in
 * `AnkiDroidCrossProviderConformanceInstrumentedTest`, which drives the same
 * [CrossProviderSnapshotSpec] rules. Both must pass for the snapshot contract to
 * mean anything: a rule only one provider satisfies is a divergence, not a rule.
 */
class AnkiConnectCrossProviderConformanceTest {
    private val normalization = CrossProviderSnapshotSpec.ProviderNormalization(
        providerName = "AnkiConnect",
        isSuspended = AnkiConnectCardNormalization::isSuspended,
        isAcceptedTemplateOrd = AnkiConnectCardNormalization::isAcceptedConfiguredOrd,
        intervalDays = AnkiConnectCardNormalization::intervalDays,
        counter = AnkiConnectCardNormalization::counter,
        signed = AnkiConnectCardNormalization::signed,
    )

    @Test
    fun normalizesEveryFieldAsTheSharedSpecRequires() {
        CrossProviderSnapshotSpec.verifyOrThrow(normalization)
    }

    /**
     * The reader must reach the normalization through [ProviderCardPolicy] rather
     * than reimplementing it. A provider-local copy is what let the two readers
     * drift on sub-day intervals in the first place, and it would pass the rule
     * check above right up until someone edited one copy.
     */
    @Test
    fun delegatesToTheSharedPolicyRatherThanACopy() {
        for (raw in PROBE_VALUES) {
            assertEquals(
                "intervalDays($raw)",
                ProviderCardPolicy.intervalDays(raw),
                AnkiConnectCardNormalization.intervalDays(raw),
            )
            assertEquals("counter($raw)", ProviderCardPolicy.counter(raw), AnkiConnectCardNormalization.counter(raw))
            assertEquals("signed($raw)", ProviderCardPolicy.signed(raw), AnkiConnectCardNormalization.signed(raw))
            assertEquals(
                "isSuspended($raw)",
                ProviderCardPolicy.isSuspendedQueue(raw),
                AnkiConnectCardNormalization.isSuspended(raw),
            )
            assertEquals(
                "isAcceptedConfiguredOrd($raw)",
                ProviderCardPolicy.isAcceptedTemplateOrd(raw),
                AnkiConnectCardNormalization.isAcceptedConfiguredOrd(raw),
            )
        }
    }

    /**
     * The spec's own failure reporting has to work, or a future divergence would be
     * silently reported as conformance. A deliberately wrong provider must be
     * rejected, and the message must name the rule.
     */
    @Test
    fun reportsAProviderThatFailsToFloorSubDayIntervals() {
        val drifted = CrossProviderSnapshotSpec.ProviderNormalization(
            providerName = "Drifted",
            isSuspended = AnkiConnectCardNormalization::isSuspended,
            isAcceptedTemplateOrd = AnkiConnectCardNormalization::isAcceptedConfiguredOrd,
            // The pre-fix AnkiDroid behavior: the raw column value, unfloored.
            intervalDays = { raw -> raw.toInt() },
            counter = AnkiConnectCardNormalization::counter,
            signed = AnkiConnectCardNormalization::signed,
        )

        val violations = CrossProviderSnapshotSpec.verify(drifted)

        assertTrue(violations.isNotEmpty())
        assertTrue(
            violations.toString(),
            violations.any { it.rule.contains("sub-day seconds") },
        )
        assertTrue(violations.all { it.providerName == "Drifted" })
    }

    /** Every allowed difference must carry a reason; a bare field name is not one. */
    @Test
    fun everyAllowedDifferenceCarriesAReasonCode() {
        val differences = CrossProviderSnapshotSpec.AllowedDifference.entries
        assertTrue(differences.isNotEmpty())
        for (difference in differences) {
            assertTrue(difference.name, difference.field.isNotBlank())
            assertTrue(difference.name, difference.reason.length > MINIMUM_REASON_LENGTH)
        }
    }

    /**
     * A field cannot be both under the exact-agreement contract and excused as an
     * allowed difference; if it were, the comparison would report conformance for a
     * field nobody actually checks.
     */
    @Test
    fun agreedFieldsAndAllowedDifferencesDoNotOverlap() {
        val excused = CrossProviderSnapshotSpec.AllowedDifference.entries
            .map { it.field.substringAfter("Card.") }
        for (field in CrossProviderSnapshotSpec.agreedCardFields) {
            assertTrue(field, excused.none { it == field })
        }
    }

    private companion object {
        /** Spans both `Int` bounds, zero, and Anki's negative seconds encoding. */
        val PROBE_VALUES = listOf(
            Long.MIN_VALUE,
            Int.MIN_VALUE.toLong(),
            -600L,
            -3L,
            -1L,
            0L,
            1L,
            30L,
            Int.MAX_VALUE.toLong(),
            Long.MAX_VALUE,
        )

        /** Long enough that a placeholder like "n/a" cannot pass for a reason. */
        const val MINIMUM_REASON_LENGTH = 40
    }
}

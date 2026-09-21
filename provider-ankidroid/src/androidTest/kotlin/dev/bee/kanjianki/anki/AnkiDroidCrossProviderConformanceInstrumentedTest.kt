package dev.bee.kanjianki.anki

import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.testing.CrossProviderSnapshotSpec
import dev.bee.kanjianki.syncdomain.ProviderCardPolicy
import dev.bee.kanjianki.testing.DeviceRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holds the AnkiDroid reader to the shared cross-provider snapshot spec.
 *
 * The AnkiConnect half is `AnkiConnectCrossProviderConformanceTest`, driving the
 * same [CrossProviderSnapshotSpec] rules. This test exists because AnkiDroid was
 * the side that diverged: `AnkiDroidCardReader` read `interval`, `reps`, and
 * `lapses` straight off the cursor as `Int`s, so Anki's negative (seconds-encoded)
 * sub-day `ivl` reached the snapshot as a *negative day count* on Android while
 * desktop floored it to zero. Maturity compares day counts against a positive
 * threshold, so the same collection produced different maturity evidence depending
 * on which provider read it.
 *
 * It is an instrumented test rather than a JVM one because `AnkiDroidCardReader`
 * needs a `Cursor`, and the point is to check the reader as it actually runs — not
 * a JVM-side transcription of what it is believed to do.
 */
@RunWith(AndroidJUnit4::class)
@DeviceRisk
class AnkiDroidCrossProviderConformanceInstrumentedTest {
    @Test
    fun normalizesEveryFieldAsTheSharedSpecRequires() {
        CrossProviderSnapshotSpec.verifyOrThrow(
            CrossProviderSnapshotSpec.ProviderNormalization(
                providerName = "AnkiDroid",
                isSuspended = ProviderCardPolicy::isSuspendedQueue,
                isAcceptedTemplateOrd = ProviderCardPolicy::isAcceptedTemplateOrd,
                intervalDays = ProviderCardPolicy::intervalDays,
                counter = ProviderCardPolicy::counter,
                signed = ProviderCardPolicy::signed,
            ),
        )
    }

    /**
     * The regression itself, checked through the real reader and a real cursor: a
     * sub-day card whose `interval` column holds Anki's negative seconds encoding
     * must arrive as zero days, not as a negative one.
     */
    @Test
    fun subDayIntervalFromTheProviderReachesTheSnapshotAsZeroDays() {
        val card = readSyntheticCard(interval = -600L, reps = 3L, lapses = 1L, queue = 1L)

        assertEquals(0, card.intervalDays)
        assertTrue("a sub-day card is not mature", !card.mature(21))
    }

    /** Negative counters cannot reach the snapshot as negative review evidence. */
    @Test
    fun negativeCountersFromTheProviderFloorAtZero() {
        val card = readSyntheticCard(interval = 30L, reps = -5L, lapses = -2L, queue = 2L)

        assertEquals(0, card.reps)
        assertEquals(0, card.lapses)
    }

    /**
     * An out-of-`Int`-range column must saturate rather than wrap. A wrapped value
     * is worse than a clamped one: it changes sign, so a huge interval would read as
     * a card that is overdue rather than one that is very mature.
     */
    @Test
    fun outOfRangeProviderValuesSaturateRatherThanWrapping() {
        val card = readSyntheticCard(
            interval = Long.MAX_VALUE,
            reps = Long.MAX_VALUE,
            lapses = Long.MAX_VALUE,
            queue = 2L,
        )

        assertEquals(Int.MAX_VALUE, card.intervalDays)
        assertEquals(Int.MAX_VALUE, card.reps)
        assertEquals(Int.MAX_VALUE, card.lapses)
    }

    /** A negative queue keeps its sign and marks the card suspended. */
    @Test
    fun buriedQueueIsSuspendedAndKeepsItsSign() {
        val card = readSyntheticCard(interval = 30L, reps = 5L, lapses = 0L, queue = -2L)

        assertEquals(-2, card.queue)
        assertTrue(card.suspended)
    }

    /**
     * Reads one card through the production `cardFromCursor` over an in-memory
     * cursor carrying [interval], [reps], [lapses], and [queue]. No resolver is
     * needed: this method only reads the row in front of it.
     */
    private fun readSyntheticCard(
        interval: Long,
        reps: Long,
        lapses: Long,
        queue: Long,
    ): RecordsSyncModels.Card {
        val cursor = MatrixCursor(
            arrayOf("note_id", "ord", "deck_id", "queue", "type", "due", "interval", "reps", "lapses"),
        )
        cursor.addRow(arrayOf<Any?>(NOTE_ID, 0, "Mining", queue, 2, 0, interval, reps, lapses))
        return cursor.use {
            it.moveToFirst()
            AnkiDroidCardReader(null).cardFromCursor(it, NOTE_ID, emptySet())
        }
    }

    private companion object {
        const val NOTE_ID = 1L
    }
}

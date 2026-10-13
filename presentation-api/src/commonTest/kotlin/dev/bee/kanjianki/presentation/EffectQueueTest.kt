package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectQueueTest {
    @Test
    fun effectsAreDeliveredOldestFirst() {
        val queue = EffectQueue()
            .enqueue(KaniEffect.ShowMessage(UiText.Key("first")))
            .enqueue(KaniEffect.ShowMessage(UiText.Key("second")))

        assertEquals(
            listOf(UiText.Key("first"), UiText.Key("second")),
            queue.pending.map { (it.effect as KaniEffect.ShowMessage).message },
        )
    }

    @Test
    fun anEffectIsDeliveredAtMostOnceEvenIfTheHostAcknowledgesTwice() {
        // A host that redelivers, or a recomposition that re-reads the queue,
        // acknowledges an id that is already gone. That must be harmless, not a
        // crash — absorbing benign duplicates is the whole point of the id.
        val queue = EffectQueue().enqueue(KaniEffect.OpenUrl("https://example.invalid"))
        val id = requireNotNull(queue.head).id

        val once = queue.consume(id)
        val twice = once.consume(id)

        assertTrue(once.isEmpty)
        assertEquals(once, twice)
    }

    @Test
    fun consumingAnIdThatWasNeverQueuedIsHarmless() {
        val queue = EffectQueue().enqueue(KaniEffect.ShowMessage(UiText.Key("x")))

        assertEquals(queue, queue.consume(9_999L))
    }

    @Test
    fun anIdIsNeverReusedAfterItsEffectIsConsumed() {
        // Reuse is how a stale acknowledgement swallows a *new* effect: the host
        // acknowledges id 1 for a snackbar it already showed and silently drops the
        // dialog that took id 1 next.
        var queue = EffectQueue()
        val seen = mutableSetOf<Long>()
        repeat(5) { index ->
            queue = queue.enqueue(KaniEffect.ShowMessage(UiText.Key("m$index")))
            val id = requireNotNull(queue.head).id
            assertTrue(seen.add(id), "id $id was reused")
            queue = queue.consume(id)
        }

        assertEquals(5, seen.size)
    }

    @Test
    fun clearingARouteDoesNotResetTheIdCounterEither() {
        // Same hazard as reuse after consumption, via the leave-the-route path.
        val queue = EffectQueue().enqueue(KaniEffect.ShowMessage(UiText.Key("dropped")))
        val droppedId = requireNotNull(queue.head).id

        val next = queue.cleared().enqueue(KaniEffect.ShowMessage(UiText.Key("fresh")))

        assertTrue(requireNotNull(next.head).id > droppedId)
    }

    @Test
    fun anEmptyQueueHasNoHead() {
        assertNull(EffectQueue().head)
        assertTrue(EffectQueue().isEmpty)
    }

    @Test
    fun aMessageActionNeedsBothALabelAndAnAction() {
        // A labelled button with nothing behind it, or an action with no way to
        // trigger it, are both unusable. Neither can be constructed.
        assertFailsWith<IllegalArgumentException> {
            KaniEffect.ShowMessage(
                message = UiText.Key("m"),
                actionLabel = UiText.Key("undo"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            KaniEffect.ShowMessage(message = UiText.Key("m"), action = KaniAction.Retry)
        }

        val valid = KaniEffect.ShowMessage(
            message = UiText.Key("m"),
            actionLabel = UiText.Key("undo"),
            action = KaniAction.Retry,
        )
        assertEquals(KaniAction.Retry, valid.action)
    }

    @Test
    fun aConfirmationCarriesWhatConfirmingWouldDoWithoutDoingIt() {
        // This gates real writes — tag write-back, restore. A test must be able to
        // assert what the confirm button *means* without performing it, which a
        // callback cannot express.
        val confirm = KaniEffect.Confirm(
            title = UiText.Key("restore.title"),
            body = UiText.Key("restore.body"),
            confirmLabel = UiText.Key("restore.confirm"),
            dismissLabel = UiText.Key("cancel"),
            confirm = KaniAction.Navigation.Open(
                KaniDestination.Settings(SettingsSection.AUTOMATION),
            ),
            isDestructive = true,
        )

        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.AUTOMATION)),
            confirm.confirm,
        )
        assertTrue(confirm.isDestructive)
    }

    @Test
    fun effectsThatNeedATargetRefuseAnEmptyOne() {
        assertFailsWith<IllegalArgumentException> { KaniEffect.OpenUrl("") }
        assertFailsWith<IllegalArgumentException> { KaniEffect.RequestFocus(" ") }
    }

    @Test
    fun anEffectIdIsAlwaysPositiveSoZeroCannotMeanBothNothingAndSomething() {
        assertFailsWith<IllegalArgumentException> {
            PendingEffect(0L, KaniEffect.ShowMessage(UiText.Key("m")))
        }
        assertFailsWith<IllegalArgumentException> {
            PendingEffect(-1L, KaniEffect.ShowMessage(UiText.Key("m")))
        }
    }

    @Test
    fun everyFilePurposeIsOneOfTheThreeHostFlows() {
        // Export, restore, and the Missing Kanji CSV fallback. A fourth would need a
        // host implementation, so the set is pinned rather than open.
        assertEquals(
            listOf(
                KaniEffect.FilePurpose.BACKUP_EXPORT,
                KaniEffect.FilePurpose.BACKUP_RESTORE,
                KaniEffect.FilePurpose.MISSING_KANJI_CSV_EXPORT,
            ),
            KaniEffect.FilePurpose.entries,
        )
    }
}

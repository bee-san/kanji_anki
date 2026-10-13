package dev.bee.kanjianki.host

import android.os.Bundle
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniDestinationCodec
import dev.bee.kanjianki.presentation.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The thin host's process-recreation contract.
 *
 * A `Bundle` needs the Android runtime, so this is Robolectric rather than a plain JVM
 * test; the logic under test is nonetheless pure. What matters here is the round trip and
 * the fail-closed paths, because saved state is untrusted input that outlives an app
 * upgrade: a destination Kani cannot rebuild must land on Home rather than half-render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidHostSavedStateTest {
    @Test
    fun everyRestorableDestinationSurvivesTheRoundTrip() {
        // One case per argument shape the codec carries, not one per route: the flags and
        // the query are what a Bundle round trip can lose.
        val destinations = listOf(
            KaniDestination.Home,
            KaniDestination.Study,
            KaniDestination.Stats,
            KaniDestination.FocusQueue,
            KaniDestination.Games,
            KaniDestination.MissingKanji,
            KaniDestination.Settings(SettingsSection.ROOT),
            KaniDestination.Settings(SettingsSection.KEYBINDINGS),
            KaniDestination.Browse(
                query = "water",
                onlySimilarKanji = true,
                allKanjiScope = true,
                showSuspended = true,
            ),
            KaniDestination.Detail(
                kanji = "水",
                fromBrowse = true,
                query = "water",
                onlySimilarKanji = true,
                allKanjiScope = true,
                showSuspended = true,
            ),
            KaniDestination.ReadOnlyDetail(kanji = "火", query = "fire"),
        )

        for (destination in destinations) {
            val bundle = Bundle()
            AndroidHostSavedState.writeDestination(bundle, destination)
            assertEquals(destination, AndroidHostSavedState.readDestination(bundle))
        }
    }

    @Test
    fun nothingIsWrittenForNoDestination() {
        val bundle = Bundle()
        AndroidHostSavedState.writeDestination(bundle, null)

        // Absent rather than present-and-empty: a host that wrote a marker for "no
        // destination" would restore something on a launch that saved nothing.
        assertEquals(false, bundle.containsKey(AndroidHostSavedState.KEY_DESTINATION_BUNDLE))
        assertNull(AndroidHostSavedState.readDestination(bundle))
    }

    @Test
    fun aRestoredKanjiIsNormalizedRatherThanTakenVerbatim() {
        // U+FA10 is the CJK *compatibility* ideograph for 塚 (U+585A): a distinct code
        // point that NFKC folds onto the ordinary kanji. Saved state can hold either, but
        // only the canonical one matches the collection, so the glyph is normalized on the
        // way in. This is the step KaniDestinationCodec cannot do: TextUtil is in :core,
        // which :presentation-api cannot see. Written as escapes rather than literals
        // because the two glyphs are visually identical -- a literal pair would read as a
        // test asserting that a value equals itself.
        val bundle = Bundle()
        AndroidHostSavedState.writeDestination(bundle, KaniDestination.Detail(kanji = "\uFA10"))

        assertEquals(
            KaniDestination.Detail(kanji = "\u585A"),
            AndroidHostSavedState.readDestination(bundle),
        )
    }

    @Test
    fun aRestoredNonKanjiRestoresNothing() {
        // Hand-built rather than written, because KaniDestination.Detail's own init only
        // rejects a blank kanji -- which is the point: the Bundle can hold what the screen
        // cannot render, so the fail-closed check has to be on the read side.
        for (corrupt in listOf("abc", "水彩", "あ", " ")) {
            val bundle = Bundle().apply {
                putBundle(
                    AndroidHostSavedState.KEY_DESTINATION_BUNDLE,
                    Bundle().apply {
                        putString(KaniDestinationCodec.KEY_DESTINATION, KaniDestination.Detail.ROUTE)
                        putString(KaniDestinationCodec.KEY_KANJI, corrupt)
                    },
                )
            }

            assertNull("'$corrupt' is not a single kanji", AndroidHostSavedState.readDestination(bundle))
        }
    }

    @Test
    fun anUnknownRouteRestoresNothing() {
        val bundle = Bundle().apply {
            putBundle(
                AndroidHostSavedState.KEY_DESTINATION_BUNDLE,
                Bundle().apply {
                    putString(KaniDestinationCodec.KEY_DESTINATION, "a-route-from-a-newer-version")
                },
            )
        }

        // The upgrade case: a build that saved a route this build does not have must land
        // on Home, not crash on the way up.
        assertNull(AndroidHostSavedState.readDestination(bundle))
    }

    @Test
    fun noSavedStateRestoresNothing() {
        assertNull(AndroidHostSavedState.readDestination(null))
        assertNull(AndroidHostSavedState.readPendingReminder(null))
        assertNull(AndroidHostSavedState.readDestination(Bundle()))
        assertNull(AndroidHostSavedState.readPendingReminder(Bundle()))
    }

    @Test
    fun anInFlightReminderChangeSurvivesTheRoundTrip() {
        // The case this state exists for: the system kills the activity while the
        // POST_NOTIFICATIONS dialog is up, so the change the user made has to outlive it.
        val pending = AndroidHostSavedState.PendingReminder(enabled = true, hour = 21, minute = 5)
        val bundle = Bundle()
        AndroidHostSavedState.writePendingReminder(bundle, pending)

        assertEquals(pending, AndroidHostSavedState.readPendingReminder(bundle))
    }

    @Test
    fun aDisabledReminderStillRoundTrips() {
        // `enabled = false` is a real value, not an absence: writing nothing for it would
        // restore the previous enabled state after a permission dialog.
        val pending = AndroidHostSavedState.PendingReminder(enabled = false, hour = 0, minute = 0)
        val bundle = Bundle()
        AndroidHostSavedState.writePendingReminder(bundle, pending)

        assertEquals(pending, AndroidHostSavedState.readPendingReminder(bundle))
    }

    @Test
    fun nothingIsWrittenForNoPendingReminder() {
        val bundle = Bundle()
        AndroidHostSavedState.writePendingReminder(bundle, null)

        assertEquals(false, bundle.containsKey(AndroidHostSavedState.KEY_PENDING_REMINDER))
        assertNull(AndroidHostSavedState.readPendingReminder(bundle))
    }

    @Test
    fun anOversizedQueryIsTruncatedRatherThanFailingTheTransaction() {
        val bundle = Bundle()
        AndroidHostSavedState.writeDestination(
            bundle,
            KaniDestination.Browse(query = "水".repeat(KaniDestinationCodec.MAX_QUERY_CHARS * 2)),
        )

        // Android's saved-state transaction is bounded, so the codec caps the query. The
        // cap belongs to the codec; this asserts the Bundle path actually goes through it.
        val restored = AndroidHostSavedState.readDestination(bundle) as KaniDestination.Browse
        assertEquals(KaniDestinationCodec.MAX_QUERY_CHARS, restored.query.length)
    }
}

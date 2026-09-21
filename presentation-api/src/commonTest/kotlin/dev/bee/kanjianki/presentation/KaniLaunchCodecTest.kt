package dev.bee.kanjianki.presentation

import dev.bee.kanjianki.presentation.KaniLaunchCodec.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaniLaunchCodecTest {
    @Test
    fun everyTargetResolvesToADestinationOnItsOwnTab() {
        // Exhaustive over the enum, so a new deep-link target cannot ship without
        // a destination. The tab assertion is what catches a target wired to a
        // plausible-looking wrong screen: Stats landing on Home would still be a
        // valid destination.
        val expected = mapOf(
            Target.KANJI_DETAIL to KaniTab.HOME,
            Target.STUDY to KaniTab.STUDY,
            Target.UPDATE to KaniTab.SETTINGS,
            Target.STATS to KaniTab.STATS,
            Target.BROWSE to KaniTab.HOME,
            Target.GAMES to KaniTab.HOME,
            Target.HOME to KaniTab.HOME,
        )
        assertEquals(Target.entries.toSet(), expected.keys, "a target has no expected tab")
        for ((target, tab) in expected) {
            val request = assertNotNull(
                KaniLaunchCodec.request(target, kanji = "脱"),
                "$target produced no request",
            )
            assertEquals(tab, request.destination.tab, "$target landed on the wrong tab")
            assertTrue(request.isUserInitiated, "$target should default to user-initiated")
        }
    }

    @Test
    fun theUpdateNotificationLandsOnTheUpdatePageNotSettingsRoot() {
        // The update notification's whole purpose is the update page. Landing on
        // Settings' root would look almost right and leave the user hunting.
        assertEquals(
            KaniDestination.Settings(SettingsSection.UPDATE),
            KaniLaunchCodec.request(Target.UPDATE)?.destination,
        )
    }

    @Test
    fun onlyStudyPreservesTheOrdinaryStudyResume() {
        // The Android host calls disableStudyOrdinaryResume() on every branch but
        // Study. Encoded here so a desktop host cannot decide otherwise: a user who
        // tapped a stats widget asked for stats, not for yesterday's session.
        for (target in Target.entries) {
            val request = assertNotNull(KaniLaunchCodec.request(target, kanji = "脱"))
            assertEquals(
                target != Target.STUDY,
                request.suppressesStudyResume,
                "$target has the wrong study-resume decision",
            )
        }
    }

    @Test
    fun theMoreSpecificTargetWinsWhateverElseArrivedWithIt() {
        // An Android intent can carry several extras at once — a notification tap
        // landing on an activity whose intent still holds a widget's extra. The
        // resolution has to be decided rather than incidental, so precedence is a
        // function of the whole set.
        assertEquals(
            Target.KANJI_DETAIL,
            KaniLaunchCodec.resolve(setOf(Target.HOME, Target.STUDY, Target.KANJI_DETAIL)),
            "naming one card must beat naming a tab",
        )
        assertEquals(
            Target.STUDY,
            KaniLaunchCodec.resolve(setOf(Target.HOME, Target.STATS, Target.STUDY)),
        )
        assertEquals(
            Target.UPDATE,
            KaniLaunchCodec.resolve(setOf(Target.HOME, Target.STATS, Target.UPDATE)),
        )
        // Home last: it is what everything else falls back to, so Home plus
        // something specific means the specific one.
        assertEquals(Target.STATS, KaniLaunchCodec.resolve(setOf(Target.HOME, Target.STATS)))
        assertEquals(Target.HOME, KaniLaunchCodec.resolve(setOf(Target.HOME)))
    }

    @Test
    fun precedenceIsTotalAndMatchesTheDeclarationOrder() {
        // Every non-empty subset resolves to exactly one target, and it is always
        // the earliest-declared member. Asserting the property rather than a
        // handful of pairs is what makes reordering the enum a test failure instead
        // of a silent behavior change.
        for (target in Target.entries) {
            for (other in Target.entries) {
                val winner = KaniLaunchCodec.resolve(setOf(target, other))
                val expected = if (target.ordinal <= other.ordinal) target else other
                assertEquals(expected, winner, "$target vs $other")
            }
        }
        assertNull(KaniLaunchCodec.resolve(emptySet()), "no targets means launch normally")
    }

    @Test
    fun aKanjiDetailRequestWithoutAKanjiIsRefusedRatherThanRedirected() {
        // Fail-closed, and deliberately not "fall back to Home": a caller that
        // gets null launches normally, which is a screen the user can reason
        // about. Silently redirecting a malformed card request would look like the
        // widget pointing at the wrong kanji.
        assertNull(KaniLaunchCodec.request(Target.KANJI_DETAIL, kanji = null))
        assertNull(KaniLaunchCodec.request(Target.KANJI_DETAIL, kanji = ""))
        assertNull(KaniLaunchCodec.request(Target.KANJI_DETAIL, kanji = "   "))
        assertEquals(
            KaniDestination.Detail(kanji = "脱"),
            KaniLaunchCodec.request(Target.KANJI_DETAIL, kanji = "脱")?.destination,
        )
    }

    @Test
    fun aNullTargetMeansLaunchNormally() {
        assertNull(KaniLaunchCodec.request(target = null))
        assertNull(KaniLaunchCodec.request(target = null, kanji = "脱"))
    }

    @Test
    fun anUnknownWireNameNamesNothing() {
        // The desktop host will parse these from an argument vector, which is
        // attacker-adjacent in the sense that a stale shortcut or a typo reaches
        // it. Unknown must be null, not the first enum entry.
        assertNull(Target.fromWireName("kanji-detial"))
        assertNull(Target.fromWireName(""))
        assertNull(Target.fromWireName(null))
        assertEquals(Target.KANJI_DETAIL, Target.fromWireName("kanji-detail"))
    }

    @Test
    fun everyTargetRoundTripsThroughTheFlatMap() {
        // The map form is how a host without an Intent carries a launch request.
        // decode(encode(x)) == x or a desktop relaunch opens the wrong screen.
        for (target in Target.entries) {
            val request = assertNotNull(KaniLaunchCodec.request(target, kanji = "脱"))
            assertEquals(
                request,
                KaniLaunchCodec.decode(KaniLaunchCodec.encode(request)),
                "$target did not survive the round trip",
            )
        }
    }

    @Test
    fun decodingRejectsMalformedMaps() {
        assertNull(KaniLaunchCodec.decode(emptyMap()))
        assertNull(KaniLaunchCodec.decode(mapOf(KaniLaunchCodec.KEY_TARGET to "nonsense")))
        assertNull(
            KaniLaunchCodec.decode(mapOf(KaniLaunchCodec.KEY_TARGET to "kanji-detail")),
            "a card request with no card must be refused",
        )
        assertEquals(
            KaniDestination.Stats,
            KaniLaunchCodec.decode(mapOf(KaniLaunchCodec.KEY_TARGET to "stats"))?.destination,
        )
    }

    @Test
    fun theKanjiKeyIsCarriedOnlyWhereItMeansSomething() {
        // A stray kanji on a Stats request would be dead weight a future reader
        // would have to guess the meaning of.
        val detail = assertNotNull(KaniLaunchCodec.request(Target.KANJI_DETAIL, kanji = "窓"))
        assertEquals("窓", KaniLaunchCodec.encode(detail)[KaniLaunchCodec.KEY_KANJI])
        val stats = assertNotNull(KaniLaunchCodec.request(Target.STATS, kanji = "窓"))
        assertNull(KaniLaunchCodec.encode(stats)[KaniLaunchCodec.KEY_KANJI])
    }

    @Test
    fun encodingIsExhaustiveOverEveryRestorableDestination() {
        // encode() classifies a destination rather than reading a stored target,
        // so it has to have an answer for every destination a host might hand it.
        // Anything unclassifiable falls to HOME, which is safe but must be
        // deliberate rather than accidental for the ones that have a real target.
        assertEquals(
            Target.BROWSE.wireName,
            KaniLaunchCodec.encode(
                KaniLaunchRequest(
                    destination = KaniDestination.Browse(query = "脱", showSuspended = true),
                    suppressesStudyResume = true,
                ),
            )[KaniLaunchCodec.KEY_TARGET],
        )
        // A Settings section that is not the update page has no deep link of its
        // own, so it lands on Home rather than pretending to be the update target.
        assertEquals(
            Target.HOME.wireName,
            KaniLaunchCodec.encode(
                KaniLaunchRequest(
                    destination = KaniDestination.Settings(SettingsSection.APPEARANCE),
                    suppressesStudyResume = true,
                ),
            )[KaniLaunchCodec.KEY_TARGET],
        )
        for (destination in ALL_DESTINATIONS) {
            val encoded = KaniLaunchCodec.encode(
                KaniLaunchRequest(destination = destination, suppressesStudyResume = true),
            )
            assertNotNull(
                Target.fromWireName(encoded[KaniLaunchCodec.KEY_TARGET]),
                "${destination.route} encoded to an unknown target",
            )
        }
    }

    private companion object {
        val ALL_DESTINATIONS: List<KaniDestination> = listOf(
            KaniDestination.Home,
            KaniDestination.Study,
            KaniDestination.Stats,
            KaniDestination.FocusQueue,
            KaniDestination.RecentMistakes,
            KaniDestination.Games,
            KaniDestination.MissingKanji,
            KaniDestination.Browse(),
            KaniDestination.Detail(kanji = "脱"),
            KaniDestination.ReadOnlyDetail(kanji = "窓"),
        ) + SettingsSection.entries.map(KaniDestination::Settings)
    }
}

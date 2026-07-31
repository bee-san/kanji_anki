package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KaniDestinationCodecTest {
    @Test
    fun everyRestorableDestinationRoundTripsThroughTheDurableMap() {
        // A host writes this map into saved instance state, a desktop session
        // file, or a deep link. Whichever container it is, decode(encode(x)) must
        // be x, or a restart silently lands the user somewhere else.
        val destinations = listOf(
            KaniDestination.Home,
            KaniDestination.Study,
            KaniDestination.Stats,
            KaniDestination.FocusQueue,
            KaniDestination.RecentMistakes,
            KaniDestination.Games,
            KaniDestination.MissingKanji,
            KaniDestination.Browse(
                query = "shell",
                onlySimilarKanji = true,
                allKanjiScope = true,
                showSuspended = true,
            ),
            KaniDestination.Detail(
                kanji = "脱",
                fromBrowse = true,
                query = "escape",
                onlySimilarKanji = true,
                allKanjiScope = false,
                showSuspended = true,
            ),
            KaniDestination.ReadOnlyDetail(kanji = "窓", query = "window"),
        ) + SettingsSection.entries.map(KaniDestination::Settings)

        for (destination in destinations) {
            assertEquals(
                destination,
                KaniDestinationCodec.decode(KaniDestinationCodec.encode(destination)),
                destination.route,
            )
        }
    }

    @Test
    fun everySettingsSectionHasItsOwnRouteAndDecodesBackToItself() {
        val routes = SettingsSection.entries.map(SettingsSection::route)

        assertEquals(routes.size, routes.toSet().size, "settings routes must be unique")
        for (section in SettingsSection.entries) {
            assertEquals(
                KaniDestination.Settings(section),
                KaniDestinationCodec.decode(
                    mapOf(KaniDestinationCodec.KEY_DESTINATION to section.route),
                ),
            )
        }
    }

    @Test
    fun anUnknownOrAbsentRouteRestoresNothingRatherThanGuessing() {
        assertNull(KaniDestinationCodec.decode(emptyMap()))
        assertNull(
            KaniDestinationCodec.decode(
                mapOf(KaniDestinationCodec.KEY_DESTINATION to "settings/dispaly-data"),
            ),
        )
        assertNull(
            KaniDestinationCodec.decode(
                mapOf(KaniDestinationCodec.KEY_DESTINATION to ""),
            ),
        )
    }

    @Test
    fun aDetailWithoutAUsableKanjiRestoresNothing() {
        // Half-restoring a detail screen would render a card for no kanji. Landing
        // on Home is the honest outcome.
        for (route in listOf(KaniDestination.Detail.ROUTE, KaniDestination.ReadOnlyDetail.ROUTE)) {
            assertNull(
                KaniDestinationCodec.decode(
                    mapOf(KaniDestinationCodec.KEY_DESTINATION to route),
                ),
                route,
            )
            assertNull(
                KaniDestinationCodec.decode(
                    mapOf(
                        KaniDestinationCodec.KEY_DESTINATION to route,
                        KaniDestinationCodec.KEY_KANJI to "   ",
                    ),
                ),
                route,
            )
        }
    }

    @Test
    fun aBlankKanjiIsRejectedAtConstructionNotJustAtDecode() {
        assertFailsWith<IllegalArgumentException> { KaniDestination.Detail(kanji = " ") }
        assertFailsWith<IllegalArgumentException> {
            KaniDestination.ReadOnlyDetail(kanji = "")
        }
    }

    @Test
    fun anUnparseableFlagFallsBackToTheNarrowerBehavior() {
        // Restoring a screen with a scope filter it cannot justify shows the user
        // more than they asked for. Every flag defaults to the narrow view.
        val decoded = KaniDestinationCodec.decode(
            mapOf(
                KaniDestinationCodec.KEY_DESTINATION to KaniDestination.Browse.ROUTE,
                KaniDestinationCodec.KEY_ONLY_SIMILAR to "True",
                KaniDestinationCodec.KEY_ALL_KANJI to "yes",
                KaniDestinationCodec.KEY_SHOW_SUSPENDED to "",
            ),
        )

        assertEquals(KaniDestination.Browse(), decoded)
    }

    @Test
    fun aQueryIsBoundedOnTheWayIntoDurableState() {
        // Android's saved state is a bounded transaction; an oversized query there
        // is a crash, not a truncation. The bound lives in the codec so no host has
        // to remember it.
        val oversized = "x".repeat(KaniDestinationCodec.MAX_QUERY_CHARS * 2)

        val encoded = KaniDestinationCodec.encode(KaniDestination.Browse(query = oversized))
        val decoded = KaniDestinationCodec.decode(encoded)

        assertEquals(
            KaniDestinationCodec.MAX_QUERY_CHARS,
            encoded.getValue(KaniDestinationCodec.KEY_QUERY).length,
        )
        assertEquals(
            KaniDestinationCodec.MAX_QUERY_CHARS,
            (decoded as KaniDestination.Browse).query.length,
        )
    }

    @Test
    fun anOversizedQueryIsAlsoBoundedComingOutOfUntrustedState() {
        // The map may not have come from encode() — a deep link is untrusted input.
        val decoded = KaniDestinationCodec.decode(
            mapOf(
                KaniDestinationCodec.KEY_DESTINATION to KaniDestination.Detail.ROUTE,
                KaniDestinationCodec.KEY_KANJI to "橋",
                KaniDestinationCodec.KEY_QUERY to "y".repeat(4_000),
            ),
        )

        assertEquals(
            KaniDestinationCodec.MAX_QUERY_CHARS,
            (decoded as KaniDestination.Detail).query.length,
        )
    }

    @Test
    fun aKanjiIsNotNormalizedHereAndTheDocumentedContractSaysSo() {
        // `TextUtil.normalizeSingleKanji` lives in :core, which this module cannot
        // see. A caller restoring untrusted state must normalize before trusting
        // the value; this test exists so nobody assumes otherwise.
        val decoded = KaniDestinationCodec.decode(
            mapOf(
                KaniDestinationCodec.KEY_DESTINATION to KaniDestination.Detail.ROUTE,
                KaniDestinationCodec.KEY_KANJI to " 脱出 ",
            ),
        )

        assertEquals(" 脱出 ", (decoded as KaniDestination.Detail).kanji)
    }

    @Test
    fun backFromADetailReachedByBrowsingReturnsToThatSearch() {
        // Losing the query on the way back turns "close this card" into "lose my
        // search".
        val detail = KaniDestination.Detail(
            kanji = "箸",
            fromBrowse = true,
            query = "chopsticks",
            onlySimilarKanji = true,
            allKanjiScope = true,
            showSuspended = true,
        )

        assertEquals(
            KaniDestination.Browse(
                query = "chopsticks",
                onlySimilarKanji = true,
                allKanjiScope = true,
                showSuspended = true,
            ),
            detail.parent,
        )
    }

    @Test
    fun backFromADetailOpenedDirectlyReturnsHome() {
        assertSame(KaniDestination.Home, KaniDestination.Detail(kanji = "傘").parent)
    }

    @Test
    fun readOnlyDetailReturnsToTheAllKanjiSearchThatFoundIt() {
        assertEquals(
            KaniDestination.Browse(query = "umbrella", allKanjiScope = true),
            KaniDestination.ReadOnlyDetail(kanji = "傘", query = "umbrella").parent,
        )
    }

    @Test
    fun settingsSubpagesWalkBackOneLevelAtATime() {
        assertEquals(
            KaniDestination.Settings(SettingsSection.AUTOMATION),
            KaniDestination.Settings(SettingsSection.UPDATE).parent,
        )
        assertEquals(
            KaniDestination.Settings(SettingsSection.DISPLAY_DATA),
            KaniDestination.Settings(SettingsSection.LICENSES).parent,
        )
        assertEquals(
            KaniDestination.Settings(SettingsSection.ROOT),
            KaniDestination.Settings(SettingsSection.APPEARANCE).parent,
        )
        assertSame(KaniDestination.Home, KaniDestination.Settings().parent)
    }

    @Test
    fun everySettingsParentChainTerminatesAtHome() {
        // A cycle here would be an infinite back stack. Walking the chain proves
        // there is not one, for every section, without trusting the enum's shape.
        for (section in SettingsSection.entries) {
            var destination: KaniDestination = KaniDestination.Settings(section)
            var steps = 0
            while (destination != KaniDestination.Home) {
                destination = requireNotNull(destination.parent) { "$section escaped" }
                steps++
                assertTrue(steps <= SettingsSection.entries.size, "$section did not terminate")
            }
        }
    }

    @Test
    fun homeIsTheOnlyDestinationWithNoParent() {
        assertNull(KaniDestination.Home.parent)
        val others = listOf(
            KaniDestination.Study,
            KaniDestination.Stats,
            KaniDestination.Stats,
            KaniDestination.FocusQueue,
            KaniDestination.RecentMistakes,
            KaniDestination.Games,
            KaniDestination.MissingKanji,
            KaniDestination.Browse(),
        )

        for (destination in others) {
            assertEquals(KaniDestination.Home, destination.parent, destination.route)
        }
    }

    @Test
    fun eachTabRootReportsThatTabAndTheRoutesMatchTheAndroidConstants() {
        // These strings are already persisted in saved state and referenced by
        // instrumentation test tags (`kani-nav-<route>`). Renaming one is a silent
        // compatibility break, so they are pinned here verbatim.
        assertEquals("home", KaniDestination.Home.route)
        assertEquals("study", KaniDestination.Study.route)
        assertEquals("stats", KaniDestination.Stats.route)
        assertEquals("settings", KaniDestination.Settings().route)
        assertEquals("focus-queue", KaniDestination.FocusQueue.route)
        assertEquals("recent-mistakes", KaniDestination.RecentMistakes.route)
        assertEquals("browse", KaniDestination.Browse.ROUTE)
        assertEquals("detail", KaniDestination.Detail.ROUTE)
        assertEquals("read-only-detail", KaniDestination.ReadOnlyDetail.ROUTE)
        assertEquals("games", KaniDestination.Games.route)
        assertEquals("missing-kanji", KaniDestination.MissingKanji.route)

        for (tab in KaniTab.entries) {
            assertEquals(tab, tab.root.tab, tab.name)
            assertEquals(tab.route, tab.root.route, tab.name)
        }
    }

    @Test
    fun homeOwnsEveryHomeReachableSecondaryScreen() {
        // Focus queue, browse, detail, games, and missing kanji are all opened from
        // Home, so selecting Home must highlight while any of them is showing.
        val homeOwned = listOf(
            KaniDestination.FocusQueue,
            KaniDestination.RecentMistakes,
            KaniDestination.Games,
            KaniDestination.MissingKanji,
            KaniDestination.Browse(),
            KaniDestination.Detail(kanji = "鍵"),
            KaniDestination.ReadOnlyDetail(kanji = "靴"),
        )

        for (destination in homeOwned) {
            assertEquals(KaniTab.HOME, destination.tab, destination.route)
        }
    }
}

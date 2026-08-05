package dev.bee.kanjianki.host

import android.content.Intent
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the launch-intent wire format, because the strings are durable.
 *
 * A `PendingIntent` in a reminder notification, or one baked into a widget the user placed
 * months ago, still carries the string it was created with — nothing re-creates it. So a
 * rename does not break a build, it silently degrades a notification tap into a plain
 * launch. The literals are therefore asserted as literals here: a test that referenced the
 * constants would happily pass through any rename, which is exactly the failure it is
 * supposed to catch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniLaunchIntentsTest {
    @Test
    fun theDurableExtraNamesAreUnchanged() {
        assertEquals("dev.bee.kanjianki.extra.OPEN_HOME", KaniLaunchIntents.EXTRA_OPEN_HOME)
        assertEquals("dev.bee.kanjianki.extra.OPEN_UPDATE", KaniLaunchIntents.EXTRA_OPEN_UPDATE)
        assertEquals("dev.bee.kanjianki.extra.OPEN_STUDY", KaniLaunchIntents.EXTRA_OPEN_STUDY)
        assertEquals("dev.bee.kanjianki.extra.OPEN_STATS", KaniLaunchIntents.EXTRA_OPEN_STATS)
        assertEquals(
            "dev.bee.kanjianki.extra.OPEN_KANJI_DETAIL",
            KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL,
        )
    }

    @Test
    fun theDurableShortcutActionsAreUnchanged() {
        assertEquals("dev.bee.kanjianki.action.OPEN_STUDY", KaniLaunchIntents.ACTION_OPEN_STUDY)
        assertEquals("dev.bee.kanjianki.action.OPEN_BROWSE", KaniLaunchIntents.ACTION_OPEN_BROWSE)
        assertEquals("dev.bee.kanjianki.action.OPEN_GAMES", KaniLaunchIntents.ACTION_OPEN_GAMES)
    }

    @Test
    fun theLegacyChainNamesTheSameStrings() {
        // The chain's constants are aliases now. If one is ever redefined independently,
        // half the PendingIntents in the app would name a string the other half cannot read.
        assertEquals(KaniLaunchIntents.EXTRA_OPEN_HOME, MainActivityBase.EXTRA_OPEN_HOME)
        assertEquals(KaniLaunchIntents.EXTRA_OPEN_UPDATE, MainActivityBase.EXTRA_OPEN_UPDATE)
        assertEquals(KaniLaunchIntents.EXTRA_OPEN_STUDY, MainActivityBase.EXTRA_OPEN_STUDY)
        assertEquals(KaniLaunchIntents.EXTRA_OPEN_STATS, MainActivityBase.EXTRA_OPEN_STATS)
        assertEquals(
            KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL,
            MainActivityBase.EXTRA_OPEN_KANJI_DETAIL,
        )
        assertEquals(KaniLaunchIntents.ACTION_OPEN_STUDY, MainActivityBase.ACTION_OPEN_STUDY)
        assertEquals(KaniLaunchIntents.ACTION_OPEN_BROWSE, MainActivityBase.ACTION_OPEN_BROWSE)
        assertEquals(KaniLaunchIntents.ACTION_OPEN_GAMES, MainActivityBase.ACTION_OPEN_GAMES)
    }

    @Test
    fun eachExtraNamesItsOwnTarget() {
        assertEquals(
            setOf(KaniLaunchCodec.Target.STUDY),
            KaniLaunchIntents.targetsIn(Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_STUDY, true)),
        )
        assertEquals(
            setOf(KaniLaunchCodec.Target.STATS),
            KaniLaunchIntents.targetsIn(Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_STATS, true)),
        )
        assertEquals(
            setOf(KaniLaunchCodec.Target.UPDATE),
            KaniLaunchIntents.targetsIn(Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_UPDATE, true)),
        )
        assertEquals(
            setOf(KaniLaunchCodec.Target.HOME),
            KaniLaunchIntents.targetsIn(Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_HOME, true)),
        )
    }

    @Test
    fun aFalseBooleanExtraNamesNothing() {
        // `putExtra(..., false)` is how a caller says "not this screen". Reading presence
        // instead of value would route every such intent to the named screen.
        val intent = Intent()
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_STUDY, false)
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_STATS, false)

        assertTrue(KaniLaunchIntents.targetsIn(intent).isEmpty())
    }

    @Test
    fun anUnusableKanjiStillNamesTheDetailTarget() {
        // Presence, not validity: the request to open a card is real even when the glyph is
        // not. What the host then does with it is decodeKaniLaunch's decision, asserted
        // below -- this reader only has to report that the extra was there.
        val intent = Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL, "not-a-kanji")

        assertEquals(setOf(KaniLaunchCodec.Target.KANJI_DETAIL), KaniLaunchIntents.targetsIn(intent))
    }

    @Test
    fun severalExtrasAtOnceAreAllReportedForTheCodecToArbitrate() {
        // The reader must not pick a winner: precedence is the shared codec's, so that both
        // hosts resolve the same collision the same way.
        val intent = Intent()
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_STUDY, true)
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_STATS, true)
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL, "水")

        assertEquals(
            setOf(
                KaniLaunchCodec.Target.STUDY,
                KaniLaunchCodec.Target.STATS,
                KaniLaunchCodec.Target.KANJI_DETAIL,
            ),
            KaniLaunchIntents.targetsIn(intent),
        )
    }

    @Test
    fun onlyTheThreeAllowlistedActionsPickAScreen() {
        assertEquals(
            KaniLaunchCodec.Target.STUDY,
            KaniLaunchIntents.shortcutTarget(KaniLaunchIntents.ACTION_OPEN_STUDY),
        )
        assertEquals(
            KaniLaunchCodec.Target.BROWSE,
            KaniLaunchIntents.shortcutTarget(KaniLaunchIntents.ACTION_OPEN_BROWSE),
        )
        assertEquals(
            KaniLaunchCodec.Target.GAMES,
            KaniLaunchIntents.shortcutTarget(KaniLaunchIntents.ACTION_OPEN_GAMES),
        )
        // `Intent.action` is caller-controlled, so anything else -- including the ordinary
        // launcher action and a plausible-looking near-miss -- falls through.
        assertNull(KaniLaunchIntents.shortcutTarget(Intent.ACTION_MAIN))
        assertNull(KaniLaunchIntents.shortcutTarget(Intent.ACTION_VIEW))
        assertNull(KaniLaunchIntents.shortcutTarget("dev.bee.kanjianki.action.OPEN_SETTINGS"))
        assertNull(KaniLaunchIntents.shortcutTarget(""))
        assertNull(KaniLaunchIntents.shortcutTarget(null))
    }

    @Test
    fun aShortcutActionAndAnExtraAreBothReported() {
        val intent = Intent(KaniLaunchIntents.ACTION_OPEN_GAMES)
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_STUDY, true)

        assertEquals(
            setOf(KaniLaunchCodec.Target.GAMES, KaniLaunchCodec.Target.STUDY),
            KaniLaunchIntents.targetsIn(intent),
        )
    }

    @Test
    fun anOrdinaryLaunchNamesNothing() {
        assertTrue(KaniLaunchIntents.targetsIn(null).isEmpty())
        assertTrue(KaniLaunchIntents.targetsIn(Intent()).isEmpty())
        assertTrue(KaniLaunchIntents.targetsIn(Intent(Intent.ACTION_MAIN)).isEmpty())
        assertNull(KaniLaunchIntents.kanjiIn(null))
        assertNull(KaniLaunchIntents.kanjiIn(Intent()))
    }

    @Test
    fun theKanjiIsReturnedUnnormalized() {
        // Unnormalized on purpose: the host normalizes, and a reader that pre-filtered would
        // make "present but unusable" indistinguishable from "absent" -- which is the
        // distinction the Home fallback depends on.
        val intent = Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL, " 水彩 ")

        assertEquals(" 水彩 ", KaniLaunchIntents.kanjiIn(intent))
    }

    @Test
    fun theHostNormalizesTheKanjiBeforeRouting() {
        // Full-width and padded, the way an extra written by an older build can be. Without
        // the host's normalize step the codec builds a Detail route for a string no card has.
        val intent = Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL, " 水 ")

        assertEquals(
            KaniDestination.Detail(kanji = "水"),
            decodeKaniLaunch(intent)?.destination,
        )
    }

    @Test
    fun anUnusableKanjiFallsBackToHomeAndStillSuppressesStudyResume() {
        // The tap was deliberate; only its argument was bad. Returning null here would mean
        // "ordinary launch", and an ordinary launch resumes the study session the user
        // abandoned yesterday -- answering a tap on a kanji widget with a study session.
        for (unusable in listOf("not-a-kanji", "", "  ", "水彩", "あ", "A")) {
            val intent = Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_KANJI_DETAIL, unusable)
            val request = decodeKaniLaunch(intent)

            assertEquals("<$unusable> routes Home", KaniDestination.Home, request?.destination)
            assertTrue("<$unusable> does not resume study", request?.suppressesStudyResume == true)
        }
    }

    @Test
    fun aKanjiExtraRidingAlongWithAnotherTargetIsNotReadAsTheArgument() {
        // Stats wins the precedence, so the kanji must not become its argument -- and the
        // stale extra must not drag the launch into the Home fallback either.
        val intent = Intent()
            .putExtra(KaniLaunchIntents.EXTRA_OPEN_STATS, true)
            .putExtra("dev.bee.kanjianki.extra.OPEN_KANJI_DETAIL_STALE", "水")

        assertEquals(KaniDestination.Stats, decodeKaniLaunch(intent)?.destination)
    }

    @Test
    fun anOrdinaryLaunchDecodesToNullSoStudyResumeIsLeftAlone() {
        // The one case that must stay null: null is what preserves resume-interrupted-study,
        // and it has to survive the Home fallback added for the invalid-glyph case.
        assertNull(decodeKaniLaunch(null))
        assertNull(decodeKaniLaunch(Intent()))
        assertNull(decodeKaniLaunch(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun studyIsTheOneTargetThatKeepsTheOrdinaryResume() {
        val study = decodeKaniLaunch(Intent().putExtra(KaniLaunchIntents.EXTRA_OPEN_STUDY, true))

        assertEquals(KaniDestination.Study, study?.destination)
        assertEquals(false, study?.suppressesStudyResume)
    }
}

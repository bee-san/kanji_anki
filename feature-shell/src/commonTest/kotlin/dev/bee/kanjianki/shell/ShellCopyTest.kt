package dev.bee.kanjianki.shell

import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The copy holder's substitution and fallback rules.
 *
 * Deliberately built from literal strings rather than resolved resources: what is
 * under test is the substitution, not the shipped wording, and a test that asserted
 * the wording would fail every time a translator improved a sentence.
 */
class ShellCopyTest {
    @Test
    fun everyTabHasItsOwnLabel() {
        val labels = KaniTab.entries.map { copy().tabLabel(it) }
        assertEquals(
            KaniTab.entries.size,
            labels.distinct().size,
            "two tabs sharing a label would be indistinguishable: $labels",
        )
        assertEquals("Home", copy().tabLabel(KaniTab.HOME))
        assertEquals("Study", copy().tabLabel(KaniTab.STUDY))
        assertEquals("Stats", copy().tabLabel(KaniTab.STATS))
        assertEquals("Settings", copy().tabLabel(KaniTab.SETTINGS))
    }

    @Test
    fun theDescriptionSubstitutesTheLabelAndDistinguishesSelection() {
        // The placeholder must actually be replaced. A description that still read
        // "%1$s tab" is the kind of bug that only a screen-reader user hits.
        assertEquals("Study tab", copy().tabDescription(KaniTab.STUDY, selected = false))
        assertEquals(
            "Study tab, selected",
            copy().tabDescription(KaniTab.STUDY, selected = true),
        )
        for (tab in KaniTab.entries) {
            for (selected in listOf(true, false)) {
                val description = copy().tabDescription(tab, selected)
                assertTrue(
                    "%" !in description,
                    "an unsubstituted placeholder survived: $description",
                )
                assertTrue(
                    copy().tabLabel(tab) in description,
                    "$description should name its tab",
                )
            }
        }
    }

    @Test
    fun aHostResolvedFailureMessageWins() {
        val failure = PresentationFailure(
            kind = PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
            message = UiText.Literal("AnkiConnect refused the connection on port 8765"),
        )
        assertEquals(
            "AnkiConnect refused the connection on port 8765",
            copy().failureMessage(failure, LiteralUiTextResolver),
            "a host that knows why should be allowed to say so",
        )
    }

    @Test
    fun aFailureWithNoUsableMessageFallsBackToItsKind() {
        // Both ways a message can come back empty: no message at all, and a message
        // the resolver cannot resolve. Neither may produce an empty error panel.
        val kindless = PresentationFailure(kind = PresentationFailure.Kind.CONFLICT)
        assertEquals(
            "failure-CONFLICT",
            copy().failureMessage(kindless, LiteralUiTextResolver),
        )

        val unresolvable = PresentationFailure(
            kind = PresentationFailure.Kind.TRANSIENT,
            message = UiText.Key("some.host.only.key"),
        )
        assertEquals(
            "failure-TRANSIENT",
            copy().failureMessage(unresolvable, LiteralUiTextResolver),
            "an unresolvable key must fall back, not render blank",
        )
    }

    @Test
    fun everyFailureKindAndCapabilityIsExplained() {
        // `getValue` throws on a missing key, so this is the test that a new enum
        // entry cannot ship without copy. The maps are built from `.entries`, so
        // this passing is what makes the exhaustive `when`s meaningful.
        for (kind in PresentationFailure.Kind.entries) {
            val message = copy().failureMessage(PresentationFailure(kind), LiteralUiTextResolver)
            assertTrue(message.isNotBlank(), "$kind has no copy")
        }
        for (capability in PlatformCapability.entries) {
            assertTrue(
                copy().capabilityExplanation(capability).isNotBlank(),
                "$capability has no explanation",
            )
        }
    }

    @Test
    fun theLiteralResolverPassesLiteralsAndBlanksWhatItCannotKnow() {
        assertEquals("plain", LiteralUiTextResolver.resolve(UiText.Literal("plain")))
        // Blank rather than a placeholder like "???": the callers treat blank as
        // "use shell copy", so a visible marker would defeat the fallback.
        assertEquals("", LiteralUiTextResolver.resolve(UiText.Key("k")))
        assertEquals("", LiteralUiTextResolver.resolve(UiText.Quantity("k", 3)))
    }

    @Test
    fun theBadgeCapsAtNinetyNinePlus() {
        // Uncapped, a four-digit due count widens the Study tab past its
        // neighbours and the bar's four columns stop being equal.
        assertEquals("0", shellBadgeLabel(0))
        assertEquals("7", shellBadgeLabel(7))
        assertEquals("99", shellBadgeLabel(99))
        assertEquals("99+", shellBadgeLabel(100))
        assertEquals("99+", shellBadgeLabel(4321))
    }

    @Test
    fun testTagsAreStableAndDistinctPerTabAndPlacement() {
        // The Android instrumentation addresses tabs by these exact strings.
        assertEquals("kani-nav-home", shellTabTestTag(KaniTab.HOME))
        assertEquals("kani-nav-home-rail", shellRailTabTestTag(KaniTab.HOME))
        val tags = KaniTab.entries.flatMap {
            listOf(shellTabTestTag(it), shellRailTabTestTag(it))
        }
        assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    }

    private fun copy(): ShellCopy = ShellCopy(
        navHome = "Home",
        navStudy = "Study",
        navStats = "Stats",
        navSettings = "Settings",
        back = "Back",
        loading = "Loading",
        retry = "Retry",
        dismiss = "Dismiss",
        navItemDescription = "%1\$s tab",
        navItemDescriptionSelected = "%1\$s tab, selected",
        failures = PresentationFailure.Kind.entries.associateWith { "failure-$it" },
        capabilities = PlatformCapability.entries.associateWith { "capability-$it" },
    )
}

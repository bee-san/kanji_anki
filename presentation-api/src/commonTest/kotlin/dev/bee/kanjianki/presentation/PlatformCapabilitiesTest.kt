package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformCapabilitiesTest {
    @Test
    fun aHostThatDeclaresNothingIsTreatedAsHavingNothing() {
        // The default is deliberately empty so a host that forgets to declare its
        // set renders visibly reduced, rather than offering everything and failing
        // at the tap.
        val none = PlatformCapabilities.NONE

        assertTrue(none.present.isEmpty())
        assertEquals(PlatformCapability.entries.toSet(), none.missing)
        for (capability in PlatformCapability.entries) {
            assertFalse(none.supports(capability), capability.name)
            assertFalse(capability in none, capability.name)
        }
    }

    @Test
    fun presentAndMissingAlwaysPartitionTheWholeCapabilitySet() {
        // A capability that is in neither set is one no screen will ever ask about.
        val capabilities = PlatformCapabilities.of(
            PlatformCapability.PROVIDER_CONNECTIVITY,
            PlatformCapability.NOTIFICATIONS,
        )

        assertEquals(
            PlatformCapability.entries.toSet(),
            capabilities.present + capabilities.missing,
        )
        assertTrue((capabilities.present intersect capabilities.missing).isEmpty())
        assertTrue(PlatformCapability.PROVIDER_CONNECTIVITY in capabilities)
        assertTrue(capabilities.supports(PlatformCapability.NOTIFICATIONS))
        assertFalse(capabilities.supports(PlatformCapability.TRAY_PRESENCE))
    }

    @Test
    fun theTwoRealHostProfilesDifferInTheWaysTheUserCanSee() {
        // Not a hypothetical: AnkiConnect never advertises FSRS_MEMORY_STATE, a
        // desktop app cannot wake itself to fire a reminder, and Android has no
        // tray. Each of those changes Settings copy, so each is a capability.
        val android = PlatformCapabilities.of(
            PlatformCapability.PROVIDER_CONNECTIVITY,
            PlatformCapability.PROVIDER_FSRS_MEMORY,
            PlatformCapability.PROVIDER_NOTE_TAG_WRITE,
            PlatformCapability.PROVIDER_MISSING_KANJI_WRITE,
            PlatformCapability.PROVIDER_BROWSER_HANDOFF,
            PlatformCapability.WRITING_RECOGNITION,
            PlatformCapability.NOTIFICATIONS,
            PlatformCapability.CLOSED_APP_SCHEDULING,
            PlatformCapability.SECRET_PERSISTENCE,
            PlatformCapability.BACKUP_RESTORE,
            PlatformCapability.UPDATE_DELIVERY,
        )
        val desktop = PlatformCapabilities.of(
            PlatformCapability.PROVIDER_CONNECTIVITY,
            PlatformCapability.PROVIDER_NOTE_TAG_WRITE,
            PlatformCapability.PROVIDER_MISSING_KANJI_WRITE,
            PlatformCapability.PROVIDER_BROWSER_HANDOFF,
            PlatformCapability.WRITING_RECOGNITION,
            PlatformCapability.TRAY_PRESENCE,
            PlatformCapability.NOTIFICATIONS,
            PlatformCapability.BACKUP_RESTORE,
            PlatformCapability.UPDATE_DELIVERY,
        )

        assertEquals(setOf(PlatformCapability.TRAY_PRESENCE), android.missing)
        assertEquals(
            setOf(
                PlatformCapability.PROVIDER_FSRS_MEMORY,
                PlatformCapability.CLOSED_APP_SCHEDULING,
                PlatformCapability.SECRET_PERSISTENCE,
            ),
            desktop.missing,
        )
    }

    @Test
    fun gatingReturnsTheActionWhenTheCapabilityIsPresent() {
        val action = KaniAction.Navigation.Open(KaniDestination.MissingKanji)

        val gate = PlatformCapabilities
            .of(PlatformCapability.PROVIDER_MISSING_KANJI_WRITE)
            .gate(PlatformCapability.PROVIDER_MISSING_KANJI_WRITE, action)

        assertSame(action, (gate as CapabilityGate.Allowed).action)
    }

    @Test
    fun gatingNamesTheMissingCapabilitySoTheUserCanBeToldWhich() {
        // Returning a bare `false` would leave the caller to re-derive which
        // capability failed in order to word the explanation.
        val gate = PlatformCapabilities.NONE.gate(
            PlatformCapability.CLOSED_APP_SCHEDULING,
            KaniAction.Retry,
        )

        assertEquals(
            CapabilityGate.Unavailable(PlatformCapability.CLOSED_APP_SCHEDULING),
            gate,
        )
    }

    @Test
    fun everyCapabilityCanBeGatedBothWays() {
        // A capability nothing can gate on is dead weight; a capability that gates
        // wrongly in one direction is a dead button or a hidden feature.
        for (capability in PlatformCapability.entries) {
            assertTrue(
                PlatformCapabilities.of(capability)
                    .gate(capability, KaniAction.Retry) is CapabilityGate.Allowed,
                capability.name,
            )
            assertEquals(
                CapabilityGate.Unavailable(capability),
                PlatformCapabilities.NONE.gate(capability, KaniAction.Retry),
                capability.name,
            )
        }
    }

    @Test
    fun aMissingCapabilityFailureIsNotAConfigurationFailure() {
        // The remedy differs: configuration is fixed in Settings, a missing
        // capability is explained. Offering "open Settings" for a tray Android does
        // not have sends the user somewhere that cannot help.
        assertFalse(PresentationFailure.Kind.CAPABILITY_MISSING.retryable)
        assertFalse(PresentationFailure.Kind.CONFIGURATION.retryable)
        val distinct = setOf(
            PresentationFailure.Kind.CAPABILITY_MISSING,
            PresentationFailure.Kind.CONFIGURATION,
        )
        assertEquals(2, distinct.size)
    }
}

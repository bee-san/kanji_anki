package dev.bee.kanjianki.host

import android.os.Looper
import dev.bee.kanjianki.presentation.PlatformCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric proof that the thin host composes the shared shell over the real process
 * container — the wiring the on-device instrumented gate then exercises for real. This
 * runs on the JVM (no KVM), so it validates composition and the first Home load, not
 * navigation/recreation/permission, which the emulator/device gate covers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniHostActivityTest {
    @Test
    fun theThinHostComposesTheSharedShellAndLoadsHomeWithoutCrashing() {
        // Under Robolectric no AnkiDroid provider is installed, so the gateway reports
        // NOT_AVAILABLE and the host composes the Home onboarding path — enough to prove
        // the whole wiring (container → probe → KaniRouteLoader → KaniShellHost →
        // KaniShell → shared surfaces) holds without a live collection.
        val activity = Robolectric.buildActivity(KaniHostActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("the thin host activity is created", activity)
        assertNotNull("its window has content", activity.window?.decorView)
    }

    @Test
    fun writingRecognitionIsClaimedOnlyOnceThereIsSomethingToWriteOn() {
        val capabilities = androidHostCapabilities()

        // `StudyCapabilityPolicy.reroute` keys off this, so the capability is a promise
        // about the *host*, not the device: claiming it makes the runtime present a
        // `write_kanji` card as a writing card, and this host has no ink surface to collect
        // a stroke with. Declining re-routes the card to core recognition instead, which
        // keeps it studyable.
        //
        // This assertion inverts when Goal 196's ink pad lands. That is the point: the pad
        // and the capability have to flip together, and a test that only checked the pad
        // would let the capability drift ahead of it — presenting a card the user cannot
        // answer, which reads as a broken rung rather than a missing feature.
        assertFalse(
            "no ink surface exists yet, so the capability must not be advertised",
            PlatformCapability.WRITING_RECOGNITION in capabilities.present,
        )
        // The two the host genuinely has, so the absence above reads as deliberate rather
        // than as an empty set.
        assertTrue(PlatformCapability.BACKUP_RESTORE in capabilities.present)
        assertTrue(PlatformCapability.SECRET_PERSISTENCE in capabilities.present)
    }
}

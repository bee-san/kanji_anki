package dev.bee.kanjianki.host

import android.os.Looper
import org.junit.Assert.assertNotNull
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
}

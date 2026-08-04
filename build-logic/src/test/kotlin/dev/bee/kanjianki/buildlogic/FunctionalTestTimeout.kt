package dev.bee.kanjianki.buildlogic

import java.util.concurrent.TimeUnit
import org.junit.rules.Timeout

/**
 * One timeout policy for every convention functional test.
 *
 * These tests run a whole nested Gradle build through `GradleRunner`, so what they
 * measure is mostly the host: cold plugin resolution, AGP configuration, an Android
 * unit-test run, coverage. A per-test literal therefore encodes the speed of whichever
 * machine the test was written on. That is how this went wrong once already — the
 * Android fixture is the heaviest of the four and had the *shortest* bound (300s, versus
 * 600s for the desktop ones), which passed everywhere except a cold Windows runner,
 * where it timed out and failed the strict cross-platform gate. The failure looked like
 * a convention-script defect and was not one.
 *
 * The timeouts are still worth having: without one, a genuinely hung nested build burns
 * the whole 45-minute job budget and reports as a killed job with no named test. With
 * one, it reports as a single failing test. So the bound stays, but it lives here — one
 * value, deliberately generous relative to real durations (tens of seconds warm), and
 * overridable per host via `-Dkani.functionalTestTimeoutSeconds=` so a slow runner can
 * be granted more time without editing Kotlin and without another hand-picked literal
 * drifting into a test file.
 *
 * `@Test(timeout = ...)` cannot express this, because an annotation argument must be a
 * compile-time constant and so can never read the property. That is why these classes
 * use a [Timeout] rule instead.
 */
object FunctionalTestTimeout {
    const val PROPERTY = "kani.functionalTestTimeoutSeconds"

    /** Chosen against the 45-minute CI job budget: one pathological test cannot eat it. */
    const val DEFAULT_SECONDS = 600L

    fun seconds(): Long =
        System.getProperty(PROPERTY)?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            ?: DEFAULT_SECONDS

    /** A fresh rule per class; JUnit rules are stateful and must not be shared. */
    fun rule(): Timeout = Timeout(seconds(), TimeUnit.SECONDS)
}

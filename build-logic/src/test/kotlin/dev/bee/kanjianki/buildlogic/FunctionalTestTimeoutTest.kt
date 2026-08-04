package dev.bee.kanjianki.buildlogic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timeout policy is only useful if every functional test actually uses it.
 *
 * A `@Test(timeout = ...)` literal reintroduced into any one class is invisible: the
 * suite still passes, the shared property silently stops applying to that test, and the
 * next slow host fails there specifically. So this reads the sources.
 */
class FunctionalTestTimeoutTest {
    @Test
    fun theDefaultIsGenerousComparedToRealDurationsAndBoundedByTheJobBudget() {
        // Warm local runs of the heaviest fixture take tens of seconds. The bound exists
        // to catch a hung nested build, not to police performance, so it sits far above
        // the real duration -- and still well under the 45-minute CI job timeout, or a
        // single hung test would consume the job instead of reporting itself.
        assertTrue(FunctionalTestTimeout.DEFAULT_SECONDS >= 300L)
        assertTrue(FunctionalTestTimeout.DEFAULT_SECONDS <= 40L * 60L)
    }

    @Test
    fun theOverrideIsReadFromTheSystemPropertyAndIgnoresUnusableValues() {
        val previous = System.getProperty(FunctionalTestTimeout.PROPERTY)
        try {
            System.setProperty(FunctionalTestTimeout.PROPERTY, "1234")
            assertEquals(1234L, FunctionalTestTimeout.seconds())

            // A malformed or non-positive value must not disable the bound: `Timeout(0)`
            // means "no timeout" in JUnit, so a typo in a CI variable would silently
            // remove the protection everywhere.
            for (unusable in listOf("", "   ", "abc", "0", "-5", "12.5")) {
                System.setProperty(FunctionalTestTimeout.PROPERTY, unusable)
                assertEquals(
                    "$unusable must fall back to the default",
                    FunctionalTestTimeout.DEFAULT_SECONDS,
                    FunctionalTestTimeout.seconds(),
                )
            }
        } finally {
            if (previous == null) {
                System.clearProperty(FunctionalTestTimeout.PROPERTY)
            } else {
                System.setProperty(FunctionalTestTimeout.PROPERTY, previous)
            }
        }
    }

    @Test
    fun everyFunctionalTestUsesTheSharedRuleAndNoInlineTimeoutRemains() {
        val sources = testSourceDirectory().listFiles { file: File ->
            file.name.endsWith("FunctionalTest.kt")
        }
        val found = requireNotNull(sources) { "cannot list the build-logic test sources" }
        // Four convention fixtures today; asserted so that a new functional test class
        // renamed out of this pattern cannot quietly escape the check.
        assertTrue("expected the functional test classes, found ${found.size}", found.size >= 4)

        for (source in found.sortedBy { it.name }) {
            val text = source.readText()
            assertTrue(
                "${source.name} still declares an inline @Test(timeout = ...) literal",
                !text.contains("@Test(timeout"),
            )
            assertTrue(
                "${source.name} does not install FunctionalTestTimeout.rule()",
                text.contains("FunctionalTestTimeout.rule()"),
            )
        }
    }

    @Test
    fun theBuildPassesTheOverrideThroughToTheTestJvm() {
        // The property is only reachable inside the test JVM if the build forwards it;
        // Gradle does not propagate arbitrary -D flags to forked test processes.
        val buildScript = File(repositoryRoot(), "build-logic/build.gradle.kts").readText()
        assertTrue(
            "build-logic/build.gradle.kts does not forward ${FunctionalTestTimeout.PROPERTY}",
            buildScript.contains(FunctionalTestTimeout.PROPERTY),
        )
    }

    private fun repositoryRoot(): File =
        File(requireNotNull(System.getProperty("kani.repositoryRoot")))

    private fun testSourceDirectory(): File =
        File(repositoryRoot(), "build-logic/src/test/kotlin/dev/bee/kanjianki/buildlogic")
}

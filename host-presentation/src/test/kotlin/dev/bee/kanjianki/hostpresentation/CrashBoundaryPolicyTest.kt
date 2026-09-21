package dev.bee.kanjianki.hostpresentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashBoundaryPolicyTest {
    private class Sabotage(message: String) : IllegalStateException(message)

    @Test
    fun containsAnOrdinaryFailureAndKeepsTheWindow() {
        var reported: CrashBoundaryPolicy.Report? = null

        val result = CrashBoundaryPolicy.guard(onFailure = { reported = it }) {
            throw Sabotage("route failed")
        }

        assertNull("a contained failure yields no value", result)
        assertEquals("Sabotage", reported?.typeName)
        assertTrue(reported?.recoverable == true)
    }

    @Test
    fun returnsTheValueWhenNothingThrows() {
        val result = CrashBoundaryPolicy.guard(onFailure = { fail("nothing threw") }) { 42 }

        assertEquals(42, result)
    }

    @Test
    fun neverLeaksTheFailureMessage() {
        // The message is where user content ends up: a home directory, a SQL fragment,
        // a kanji from the collection. This screen may be screenshotted into a bug
        // report, so it says what broke, not what the user was studying.
        val secret = "/home/someone/.kani/collection with 脱出 in it"
        val report = CrashBoundaryPolicy.report(Sabotage(secret))

        assertFalse(report.summary.contains(secret))
        assertFalse(report.summary.contains("someone"))
        assertFalse(report.summary.contains("脱出"))
        assertEquals("Kani hit an unexpected error (Sabotage).", report.summary)
    }

    @Test
    fun rethrowsAnErrorRatherThanDrawingARecoveryScreen() {
        var reportedAnything = false
        val fatal = OutOfMemoryError("heap")

        // A JVM that cannot allocate cannot be trusted to run the recovery path, and an
        // app that looks fine while unable to save anything is worse than one that dies.
        val thrown = assertThrows(OutOfMemoryError::class.java) {
            CrashBoundaryPolicy.guard(onFailure = { reportedAnything = true }) {
                throw fatal
            }
        }

        assertSame(fatal, thrown)
        assertFalse("the boundary must not try to render before rethrowing", reportedAnything)
    }

    @Test
    fun ignoresCancellationBecauseItIsNormalControlFlow() {
        var reportedAnything = false

        // A route was left or a sync abandoned. Reporting a crash here would show a
        // fault where the user simply navigated away.
        val result = CrashBoundaryPolicy.guard(
            onFailure = { reportedAnything = true },
            isCancellation = { it.message == "cancelled" },
        ) {
            throw IllegalStateException("cancelled")
        }

        assertNull(result)
        assertFalse(reportedAnything)
    }

    @Test
    fun classifiesEachFailureKind() {
        assertEquals(
            CrashBoundaryPolicy.Action.SHOW_RECOVERY,
            CrashBoundaryPolicy.decide(Sabotage("boom")),
        )
        assertEquals(
            CrashBoundaryPolicy.Action.RETHROW,
            CrashBoundaryPolicy.decide(StackOverflowError()),
        )
        assertEquals(
            CrashBoundaryPolicy.Action.RETHROW,
            CrashBoundaryPolicy.decide(NoSuchMethodError("linkage")),
        )
        assertEquals(
            CrashBoundaryPolicy.Action.IGNORE,
            CrashBoundaryPolicy.decide(Sabotage("boom"), isCancellation = { true }),
        )
        // Cancellation wins over Error: the injected predicate is the caller's own
        // answer, and second-guessing it would report a cancelled job as fatal.
        assertEquals(
            CrashBoundaryPolicy.Action.IGNORE,
            CrashBoundaryPolicy.decide(OutOfMemoryError(), isCancellation = { true }),
        )
    }

    @Test
    fun namesAnAnonymousFailureRatherThanRenderingAnEmptyParenthesis() {
        // Lambdas and anonymous objects have a blank `simpleName`. "Kani hit an
        // unexpected error ()." would look broken exactly when the user needs to trust
        // the boundary.
        val anonymous = object : RuntimeException("thrown from an anonymous class") {}
        val report = CrashBoundaryPolicy.report(anonymous)

        assertTrue("a blank simple name must fall back", report.typeName.isNotBlank())
        assertFalse(report.summary.contains("()"))
        assertTrue(report.recoverable)
    }

    @Test
    fun marksAnErrorAsUnrecoverableEvenWhenDescribed() {
        // `report` is callable independently of `decide`; a host that described an Error
        // must still learn it is not recoverable.
        val report = CrashBoundaryPolicy.report(OutOfMemoryError("heap"))

        assertEquals("OutOfMemoryError", report.typeName)
        assertFalse(report.recoverable)
    }

    @Test
    fun letsAFailingReporterPropagate() {
        // A reporting path that silently fails leaves a blank window with no trace of
        // why — the exact outcome the boundary exists to prevent.
        assertThrows(UnsupportedOperationException::class.java) {
            CrashBoundaryPolicy.guard(
                onFailure = { throw UnsupportedOperationException("no screen") },
            ) {
                throw Sabotage("route failed")
            }
        }
    }

    @Test
    fun theReportIsAValueSoTwoEqualFailuresCompareEqual() {
        assertEquals(
            CrashBoundaryPolicy.report(Sabotage("a")),
            CrashBoundaryPolicy.report(Sabotage("b")),
        )
        assertEquals(
            CrashBoundaryPolicy.report(Sabotage("a")).hashCode(),
            CrashBoundaryPolicy.report(Sabotage("b")).hashCode(),
        )
        val report = CrashBoundaryPolicy.Report(typeName = "X", recoverable = true)
        assertEquals(report, report.copy())
        assertTrue(report.toString().contains("X"))
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)
}

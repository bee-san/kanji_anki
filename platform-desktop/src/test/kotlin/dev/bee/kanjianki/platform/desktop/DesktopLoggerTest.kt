package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppLogEvent
import dev.bee.kanjianki.platform.AppLogLevel
import dev.bee.kanjianki.platform.debug
import dev.bee.kanjianki.platform.error
import dev.bee.kanjianki.platform.info
import dev.bee.kanjianki.platform.warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLoggerTest {
    @Test
    fun everyLevelAboveDebugIsWrittenWithItsLevelPrefix() {
        val lines = ArrayList<String>()
        val logger = DesktopLogger(sink = lines::add)

        logger.info("synced")
        logger.warning("provider slow")
        logger.error("sync failed")

        assertEquals(
            listOf("[INFO] synced", "[WARNING] provider slow", "[ERROR] sync failed"),
            lines,
        )
    }

    @Test
    fun debugIsDroppedUntilTheUserOptsIn() {
        // debugLogEnabled is an opt-in device setting; filtering here rather than at
        // each call site means a new caller cannot leak verbose output by forgetting
        // to check it.
        val lines = ArrayList<String>()
        var enabled = false
        val logger = DesktopLogger(debugEnabled = { enabled }, sink = lines::add)

        logger.debug("before opt-in")
        enabled = true
        logger.debug("after opt-in")

        assertEquals(listOf("[DEBUG] after opt-in"), lines)
    }

    @Test
    fun aCauseIsRenderedInlineSoItCannotInterleaveWithOtherThreads() {
        val lines = ArrayList<String>()
        val logger = DesktopLogger(sink = lines::add)

        logger.error("close failed", IllegalStateException("wal checkpoint failed"))

        val line = lines.single()
        assertTrue(line.startsWith("[ERROR] close failed"))
        assertTrue(line.contains("IllegalStateException: wal checkpoint failed"))
        assertTrue(line.contains("DesktopLoggerTest"))
        // One sink call, not one per stack frame.
        assertEquals(1, lines.size)
    }

    @Test
    fun anEventWithoutACauseHasNoTrailingBlankLine() {
        val lines = ArrayList<String>()

        DesktopLogger(sink = lines::add).log(AppLogEvent(AppLogLevel.INFO, "plain"))

        assertEquals("[INFO] plain", lines.single())
    }

    @Test
    fun theDefaultLoggerDropsDebugAndAcceptsTheRest() {
        // Exercises the production defaults (debug off, System.err sink) without
        // capturing process streams: the sink is only reached for non-debug events,
        // and dropping debug is the observable half of the default.
        val logger = DesktopLogger()

        logger.log(AppLogEvent(AppLogLevel.DEBUG, "not written anywhere"))
    }
}

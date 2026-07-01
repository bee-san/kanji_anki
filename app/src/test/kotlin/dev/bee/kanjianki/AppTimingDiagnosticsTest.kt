package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTimingDiagnosticsTest {
    @Test
    fun recordsMilestonesInOrderAndFormatsAReport() {
        var now = 1_000L
        val recorder = TimingDiagnosticsRecorder(clock = { now }, logger = {})

        recorder.markProcessStart()
        now = 1_120L
        recorder.markHomeFirstFrame()
        now = 1_205L
        recorder.markStudyCtaVisible()
        now = 1_315L
        recorder.markStudyTapReceived()
        now = 1_578L
        recorder.markStudyLoadingStarted()
        now = 1_930L
        recorder.markStudyCardUsable()
        now = 2_240L
        recorder.markStudyAnswerRevealed()
        now = 2_510L
        recorder.markDictionaryLoaded()
        recorder.markDictionaryLoaded()

        val snapshot = recorder.snapshot()

        assertEquals(
            listOf(
                "process.start",
                "home.first_frame",
                "home.study_cta.visible",
                "home.study_cta.tapped",
                "study.loading.started",
                "study.card.usable",
                "study.answer.revealed",
                "dictionary.loaded",
            ),
            snapshot.events.map { it.name },
        )
        assertEquals(
            "8 events; total 1510 ms; first=process.start; last=dictionary.loaded",
            snapshot.summaryText(),
        )
        assertTrue(snapshot.previewText(limit = 3).contains("120 ms home.first_frame"))
        assertTrue(snapshot.reportText().contains("event_count=8"))
        assertTrue(snapshot.reportText().contains("08. 1510 ms dictionary.loaded"))
    }

    @Test
    fun resetClearsTheCurrentCapture() {
        val recorder = TimingDiagnosticsRecorder(clock = { 42L }, logger = {})
        recorder.markProcessStart()
        recorder.markHomeFirstFrame()

        recorder.reset()

        val snapshot = recorder.snapshot()
        assertTrue(snapshot.events.isEmpty())
        assertEquals("0 events; total 0 ms; first=-; last=-", snapshot.summaryText())
        assertTrue(snapshot.reportText().contains("(no events yet)"))
    }
}

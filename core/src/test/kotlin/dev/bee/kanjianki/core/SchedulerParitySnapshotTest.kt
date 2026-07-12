package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SchedulerParitySnapshotTest {
    @Test
    fun paritySnapshotMatchesGoldenTimelineManifest() {
        assertEquals(expectedSnapshot(), actualSnapshot())
    }

    @Test
    fun reportDocumentsHistoricalSourcesAndV31LazyConversionBoundary() {
        val report = Files.readString(reportPath())

        assertTrue(report.contains("Snapshot date: 2026-06-12"))
        assertTrue(report.contains("Historical DB30 lab snapshot"))
        assertTrue(report.contains("DB31 lazy-conversion boundary"))
        assertTrue(report.contains("adaptive-two-core-scheduler.md"))
        assertTrue(report.contains("docs/anki-manual-parity-checklist.md"))
        assertTrue(report.contains("docs/fsrs-impact-report.md"))
        assertTrue(report.contains("SchedulerDecisionTraceTest"))
        assertTrue(report.contains("SchedulerTimelineSimulatorTest"))
        assertTrue(report.contains("SchedulerParitySnapshotTest"))
        assertTrue(report.contains("two long-term core memories"))
        assertTrue(report.contains("practice-only inline repair"))
        assertTrue(report.contains("no leech tag/suspend policy"))
        assertTrue(report.contains("same-session same-family hiding"))
        assertTrue(report.contains("not claim full Anki FSRS parity"))
    }

    private fun actualSnapshot(): String {
        return buildString {
            appendLine("scheduler parity snapshot")
            appendLine("routing contract: DB31 adaptive with DB30 lazy conversion")
            appendLine()
            appendLine("source docs:")
            appendLine("- docs/adaptive-two-core-scheduler.md")
            appendLine("- docs/anki-manual-parity-checklist.md")
            appendLine("- docs/fsrs-impact-report.md")
            appendLine()
            appendLine("lazy-conversion and compatibility timeline extracts:")
            appendLine("- newKanjiEntersKanjiMeaning => ${flattenGolden("newKanjiEntersKanjiMeaning")}")
            appendLine("- reviewPassPromotesAfterLongFsrsInterval => ${flattenGolden("reviewPassPromotesAfterLongFsrsInterval")}")
            appendLine("- promotionRequiresSecondRealDuePass => ${flattenGolden("promotionRequiresSecondRealDuePass")}")
            appendLine("- threeDueReviewAgainsDemote => ${flattenGolden("threeDueReviewAgainsDemote")}")
            appendLine("- ceilingCardDemotesOneRungWhenCold => ${flattenGolden("ceilingCardDemotesOneRungWhenCold")}")
            appendLine("- demotionWithEmptyRelearningSteps => ${flattenGolden("demotionWithEmptyRelearningSteps")}")
            appendLine("- writeKanjiExitRequiresCleanWrites => ${flattenGolden("writeKanjiExitRequiresCleanWrites")}")
            appendLine("- similarKanjiSkippedWithoutContent => ${flattenGolden("similarKanjiSkippedWithoutContent")}")
            appendLine("- relearningBeatsSameFamilyReviewSibling => ${flattenGolden("relearningBeatsSameFamilyReviewSibling")}")
            appendLine()
            appendLine("parity matrix:")
            appendLine("| area | DB31 Kani behavior | Anki parity note |")
            appendLine("| routing | two mandatory core memories; variants share memory; repair stays inline | Kani-specific skill routing |")
            appendLine("| lazy conversion | DB30 learning/relearning finishes in place; return to review converts in the same commit | compatibility boundary, not a third scheduler |")
            appendLine("| learning/relearning steps | legacy steps finish unchanged; adaptive repair snapshots delays and remains practice-only | intentionally Anki-like timing where implemented |")
            appendLine("| bury/sibling order | same-family relearning wins over review sibling; legacy mature-sibling suppression removed | session/queue suppression only, not manual unbury |")
            appendLine("| FSRS | only real-due core checks update local Kani memory/interval; repair does not call FSRS | not Anki deck-preset or optimizer parity |")
            appendLine("| lapses/leeches | a real-due core Fail records one lapse then routes repair/revalidation; no leech tag/suspend policy | leech-informed only for now |")
            appendLine()
            appendLine("boundary:")
            appendLine("- Legacy rung, task-memory, wire, and settings state remains stored for lazy conversion and downgrade compatibility")
            appendLine("- Adaptive variants share one of two core memories; repair state remains on the owning study_items row")
            appendLine("- Same-session same-family hiding is the only sibling suppression layer")
        }.trimEnd()
    }

    private fun expectedSnapshot(): String {
        val resource = javaClass.getResource("/dev/bee/kanjianki/core/scheduler-parity/scheduler-parity.snapshot.txt")
        assertNotNull("Missing scheduler parity snapshot resource", resource)
        return resource!!.readText().trimEnd()
    }

    private fun flattenGolden(name: String): String {
        return goldenText(name)
            .lineSequence()
            .map { line -> line.substringAfter("|") }
            .joinToString(" | ")
    }

    private fun goldenText(name: String): String {
        val resource = javaClass.getResource("/dev/bee/kanjianki/core/scheduler-goldens/$name.timeline.txt")
        assertNotNull("Missing scheduler golden resource $name", resource)
        return resource!!.readText().trimEnd()
    }

    private fun reportPath(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(4) {
            val candidate = current.resolve("docs/scheduler-fsrs-correctness-lab-report.md")
            if (Files.exists(candidate)) {
                return candidate
            }
            current = current.parent ?: current
        }
        return Paths.get("docs/scheduler-fsrs-correctness-lab-report.md")
    }
}

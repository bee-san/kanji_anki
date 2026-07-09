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
    fun reportDocumentsSourcesBoundariesAndIntentionalDifferences() {
        val report = Files.readString(reportPath())

        assertTrue(report.contains("Current version/date: 2026-06-12"))
        assertTrue(report.contains("docs/anki-manual-parity-checklist.md"))
        assertTrue(report.contains("docs/fsrs-impact-report.md"))
        assertTrue(report.contains("SchedulerDecisionTraceTest"))
        assertTrue(report.contains("SchedulerTimelineSimulatorTest"))
        assertTrue(report.contains("SchedulerParitySnapshotTest"))
        assertTrue(report.contains("FSRS memory/interval only"))
        assertTrue(report.contains("no leech tag/suspend policy"))
        assertTrue(report.contains("same-session same-family hiding"))
        assertTrue(report.contains("not claim full Anki FSRS parity"))
    }

    private fun actualSnapshot(): String {
        return buildString {
            appendLine("scheduler parity snapshot")
            appendLine()
            appendLine("source docs:")
            appendLine("- docs/anki-manual-parity-checklist.md")
            appendLine("- docs/fsrs-impact-report.md")
            appendLine()
            appendLine("golden timeline extracts:")
            appendLine("- newKanjiEntersKanjiMeaning => ${flattenGolden("newKanjiEntersKanjiMeaning")}")
            appendLine("- reviewPassPromotesAfterLongFsrsInterval => ${flattenGolden("reviewPassPromotesAfterLongFsrsInterval")}")
            appendLine("- promotionRequiresSecondRealDuePass => ${flattenGolden("promotionRequiresSecondRealDuePass")}")
            appendLine("- threeDueReviewAgainsDemote => ${flattenGolden("threeDueReviewAgainsDemote")}")
            appendLine("- ceilingCardDemotesOneRungWhenCold => ${flattenGolden("ceilingCardDemotesOneRungWhenCold")}")
            appendLine("- similarKanjiSkippedWithoutContent => ${flattenGolden("similarKanjiSkippedWithoutContent")}")
            appendLine("- relearningBeatsSameFamilyReviewSibling => ${flattenGolden("relearningBeatsSameFamilyReviewSibling")}")
            appendLine()
            appendLine("parity matrix:")
            appendLine("| area | current Kani behavior | Anki parity note |")
            appendLine("| learning/relearning steps | Again resets/repeats, Good advances, Hard delays/repeats, Easy graduates | intentionally Anki-like where implemented |")
            appendLine("| bury/sibling order | same-family relearning wins over review sibling; legacy mature-sibling suppression removed | session/queue suppression only, not manual unbury |")
            appendLine("| FSRS | local Kani FSRS memory/interval scheduling only | not Anki deck-preset or optimizer parity |")
            appendLine("| lapses/leeches | Kani tracks lapses and demotion streaks; no leech tag/suspend policy | leech-informed only for now |")
            appendLine()
            appendLine("boundary:")
            appendLine("- FSRS memory/interval only; Kani queue/ladder/suppression/steps/UI policy")
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

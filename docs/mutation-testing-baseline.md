# Mutation Testing Baseline

**Date:** 2026-07-15
**Compatibility re-audited:** 2026-07-21
**Tool:** PIT (pitest) — Gradle plugin `info.solidsoft.pitest`
**Status:** Plugin integration pending; the former Gradle 9 compatibility blocker is cleared

## Target files

| File | Lines | Purpose |
|------|-------|---------|
| `ReviewTransitionEngine.kt` | 843 | Legacy ladder state machine |
| `StudyQueueSeeder.kt` | 763 | Queue admission and seeding |
| `BridgeScheduler.kt` | 660 | Session scheduling bridge |
| `KanjiRepairEvidencePolicy.kt` | 557 | Repair admission/graduation |
| `ReminderEligibilityPolicy.kt` | 50 | Daily reminder filter |

## Gradle 9.4.1 compatibility update

The original attempt used plugin v1.15.0, which references
`project.reporting.baseDir`; Gradle 9 removed that property, so that version
failed at apply time with:

```
Could not get unknown property 'baseDir' for extension 'reporting'
of type org.gradle.api.reporting.ReportingExtension.
```

That result no longer applies to the current plugin. The
[`info.solidsoft.pitest` plugin portal](https://plugins.gradle.org/plugin/info.solidsoft.pitest)
lists v1.19.0 (2026-03-29) as latest, and its
[changelog](https://github.com/szpak/gradle-pitest-plugin/blob/master/CHANGELOG.md)
documents initial Gradle 9 support. On 2026-07-21, v1.19.0 applied successfully
both in an isolated Gradle 9.4.1 project and to this repository's `:core`
project, exposing the `pitest` task.

This apply-time check does not complete Goal 142. Repository integration still
needs the plugin and PIT artifacts added to dependency-verification metadata,
the target configuration reviewed against the Kotlin/JUnit suite, and a real
mutation run recorded.

## Planned configuration

Apply the current plugin to `:core`:

```kotlin
plugins {
    id("info.solidsoft.pitest") version "1.19.0"
}

pitest {
    targetClasses.set(listOf(
        "dev.bee.kanjianki.core.ReviewTransitionEngine",
        "dev.bee.kanjianki.core.StudyQueueSeeder",
        "dev.bee.kanjianki.core.BridgeScheduler",
        "dev.bee.kanjianki.core.KanjiRepairEvidencePolicy",
        "dev.bee.kanjianki.core.ReminderEligibilityPolicy",
    ))
    targetTests.set(listOf("dev.bee.kanjianki.core.*"))
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutators.set(listOf("DEFAULTS"))
}
```

## Test strength improvements (preemptive)

Before plugin integration, 5 test assertions were strengthened in this batch
based on manual code inspection of likely surviving mutants:

1. `AdaptiveReviewTransitionEngineTest.goldenHappyPath` — asserts specific rung
   after promotion (not just `!= null`).
2. `AdaptiveReviewTransitionEngineTest.goldenRecognitionLapse` — verifies exact
   lapse count and repair task type.
3. `AdaptiveReviewTransitionEngineTest.goldenReadingLapse` — verifies lapse on
   the correct memory (wordReading, not kanjiMeaning).
4. `SyncFailureClassificationTest` — asserts specific enum values (not just
   non-null).
5. `StoreResultTest` — asserts exact cause object identity (not just type).

## Next steps

- Add v1.19.0 and its artifacts to the checked dependency-verification metadata.
- Run `./gradlew :core:pitest` and record per-file mutation scores
  (killed / total) below.
- Identify top 5 surviving mutants and add targeted assertions.

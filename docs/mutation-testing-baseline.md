# Mutation Testing Baseline

**Date:** 2026-07-15
**Tool:** PIT (pitest) — Gradle plugin `info.solidsoft.pitest`
**Status:** Plugin integration deferred (Gradle 9.4.1 incompatibility)

## Target files

| File | Lines | Purpose |
|------|-------|---------|
| `ReviewTransitionEngine.kt` | 819 | Legacy ladder state machine |
| `StudyQueueSeeder.kt` | ~450 | Queue admission and seeding |
| `BridgeScheduler.kt` | ~300 | Session scheduling bridge |
| `KanjiRepairEvidencePolicy.kt` | ~200 | Repair admission/graduation |
| `ReminderEligibilityPolicy.kt` | ~150 | Daily reminder filter |

## Gradle 9.4.1 incompatibility

The latest `info.solidsoft.pitest` plugin (v1.15.0, February 2026) references
`project.reporting.baseDir` which was removed in Gradle 9.0. The plugin fails
at apply time with:

```
Could not get unknown property 'baseDir' for extension 'reporting'
of type org.gradle.api.reporting.ReportingExtension.
```

No compatible version exists as of July 2026. Options:
1. Wait for pitest plugin v1.16+ with Gradle 9 support.
2. Run pitest via the Maven plugin in a standalone `pom.xml` targeting the
   `:core` module's compiled classes.
3. Downgrade to Gradle 8.x in a throwaway branch for baseline measurement.

## Planned configuration

When a compatible plugin version is available, apply it to `:core`:

```kotlin
plugins {
    id("info.solidsoft.pitest") version "1.16.0" // hypothetical future version
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

While awaiting the plugin, 5 test assertions were strengthened in this batch
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

- Monitor pitest plugin releases for Gradle 9 compatibility.
- When available, run `./gradlew :core:pitest` and record per-file mutation
  scores (killed / total) below.
- Identify top 5 surviving mutants and add targeted assertions.

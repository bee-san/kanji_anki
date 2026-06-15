# Similar-kanji writing study experience completion evidence

Captured: 2026-06-12T20:18:14Z

## Scope

README queue item: “Build the World-Class Similar Kanji + Writing Study Experience”.

The completion gate required undo, explanations, tutor feedback, and hint progression to be covered by targeted tests and UI evidence, while keeping writing diagnosis tutor-only and keeping similar-kanji repair out of an independent scheduler queue.

## Implemented evidence on `origin/main`

| Area | Merged PR evidence | Targeted coverage |
| --- | --- | --- |
| Immediate undo for the last applied rating | [#416](https://github.com/bee-san/kanji_anki/pull/416) `study: remember immediate undo snapshot`, [#417](https://github.com/bee-san/kanji_anki/pull/417) `study: preserve undo banner across review routes`, [#423](https://github.com/bee-san/kanji_anki/pull/423) `Add undo banner UI evidence` | `StudyReviewActionsTest`, `StudyUndoStateTest`, `MainActivityShellHostUndoStateTest`, `MainActivityStudyFlashcardComposeUnitTest`, `StudyUndoBannerComposeTest` |
| Similar-kanji explanation cards | [#422](https://github.com/bee-san/kanji_anki/pull/422) `Localize similar kanji explanation copy`, [#424](https://github.com/bee-san/kanji_anki/pull/424) `Show study source words in similar kanji explanations` | `SimilarKanjiExplanationPolicyTest`, similar-choice Compose coverage, localized explanation copy tests |
| Tutor-only writing feedback categories | [#418](https://github.com/bee-san/kanji_anki/pull/418) `feat(writing): add tutor feedback categories`, [#420](https://github.com/bee-san/kanji_anki/pull/420) `feat(study): show writing prompt reasons` | `WritingFeedbackCopyTest`, `WritingAnalysisEngineTest`, `StrokeOrderEvaluatorTest`, helper/instrumented diagnosis checks |
| Adaptive hint progress and checker states | [#419](https://github.com/bee-san/kanji_anki/pull/419) `fix(writing): avoid hint regression on checker errors`, [#425](https://github.com/bee-san/kanji_anki/pull/425) `feat(writing): show current hint stage in guide copy`, [#427](https://github.com/bee-san/kanji_anki/pull/427) `feat(writing): keep checker download status contextual` | `WritingHintPolicyTest`, `WritingFeedbackCopyTest`, `WritingAnalysisEngineTest`, writing helper/instrumented tests |
| Study-flow polish | [#426](https://github.com/bee-san/kanji_anki/pull/426) `feat(study): pin similar choice actions to bottom bar`, [#427](https://github.com/bee-san/kanji_anki/pull/427) contextual checker status | similar-choice action bar Compose coverage, writing prompt/status copy coverage, session progress summary coverage |

All PRs listed above are merged. The GitHub check rollup observed on 2026-06-12 showed successful CI for each listed PR, including JVM tests and coverage, app unit tests and coverage, app lint/androidTest compile or split app lint + androidTest compile, dictionary/asset/Ralph-loop tests, dependency safety, fast confidence gate, build coverage/analyze, and SonarCloud analysis.

## Local verification on the completion branch

Targeted verification run from the completion branch on 2026-06-12:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/Users/autumnskerritt/Library/Android/sdk
export ANDROID_SDK_ROOT=/Users/autumnskerritt/Library/Android/sdk

./gradlew :writing-core:test \
  --tests dev.bee.kanjianki.core.study.WritingFeedbackCopyTest \
  --tests dev.bee.kanjianki.core.study.WritingHintPolicyTest \
  --tests dev.bee.kanjianki.core.study.WritingAnalysisEngineTest
# BUILD SUCCESSFUL in 9s

./gradlew :core:test \
  --tests dev.bee.kanjianki.core.SimilarKanjiExplanationPolicyTest \
  --tests dev.bee.kanjianki.core.StudySessionProgressTrackerTest
# BUILD SUCCESSFUL in 13s

./gradlew :app:testDebugUnitTest \
  --tests dev.bee.kanjianki.StudyUndoStateTest \
  --tests dev.bee.kanjianki.MainActivityShellHostUndoStateTest \
  --tests dev.bee.kanjianki.MainActivityStudyFlashcardComposeUnitTest \
  --tests dev.bee.kanjianki.MainActivityStudySimilarKanjiChoiceComposeUnitTest
# BUILD SUCCESSFUL in 38s
```

## Safety checks

- Similar-kanji repair remains local content/practice support, not a separate scheduler queue. The scheduler still selects `study_items`; repair rows are surfaced through study-flow helpers and progress accounting rather than replacing review scheduling.
- Writing diagnosis remains tutor feedback. The scheduler rating path still flows through the existing rating/review actions; diagnosis copy and hint state explain what happened without directly changing FSRS memory, review intervals, or ladder movement.
- Undo is immediate and bounded. It restores only the last matching applied-review boundary, clears when leaving study, and refuses stale boundaries without partial restore.
- The UI evidence includes visible undo affordances, source-word explanation details, helper/tutor feedback copy, and pinned similar-choice actions so study decisions remain reachable during the flow.

## Completion decision

The required slices have landed with tests and CI, and this evidence file ties the merged PRs to the README gate. The README item is checked in the same PR as this evidence so review and CI can validate the completion marker before it reaches `origin/main`.

# Cheap Ralph UX/copy final pass — 2026-06-10

This file records the operator sweep that closes the current Cheap Ralph queue item:

> Go through every user-facing view, dialog, empty/error state, onboarding/import path, study flow, Settings surface, and stats/history screen; analyse the whole UX/UI, not just copy.

Bee asked to finish the remaining copy-focused work in one sweep and cut a release, rather than continuing the automatic loop one tiny PR at a time. The final decision is based on the merged PR sequence, targeted regression tests, and a source inventory of the centralized copy/model surfaces.

## Completion evidence

Merged PR evidence for the final copy/UX sweep:

- #294–#301: clarified and tightened Settings import, sync, workload, automation, study-load, reference, and ladder copy.
- #302: tightened Browse screen copy and accessibility wording.
- #303–#321: clarified Settings workload, study-ahead, note type, notifications, update/install, reference data, import filters, learning-step, daily-sync, ladder, automation, and suspended-range copy.
- #322–#352: finished the Settings/home copy sweep in small reviewed slices, ending with #352 `refactor(settings): tighten section descriptions`.

Required quality evidence:

- Every PR in the final run was reviewed through the Cheap Ralph QA lane before merge.
- The final PR #352 had green GitHub checks: Dependency safety, Dictionary/asset/Ralph loop tests, JVM tests and coverage, App lint and androidTest compile, App unit tests and coverage, Fast confidence gate, Build coverage and analyze, and SonarCloud Code Analysis.
- The core copy regression suite covers the centralized copy sources (`HomeTextCopyTest`, `Settings*TextCopyTest`, `StatsTextCopyTest`, `Study*CopyTest`, `KanjiGameCopyTest`, `SyncProgressCopyTest`, `TimelineCopyTest`, and related tests).
- App model tests (`ComposeScreenModelsTest`, Settings category navigation tests) covered the Settings/home model expectations touched by the final slices.

## View-by-view final inventory

- Home and first-run/import entry: onboarding and empty-state prompts now lead with the next action, not long explanation. Evidence: #331, #350, `HomeTextCopyTest`.
- Browse/detail and local evidence surfaces: copy/accessibility was tightened without changing data behavior. Evidence: #302 and central copy tests.
- Study flow, done/empty state, review buttons, and learning repeats: labels stay short while preserving scheduler semantics (learning/relearning practice stays separate from due reviews). Evidence: #333, study copy tests, scheduler notes in `AGENTS.md`.
- Settings overview and section cards: section bodies now use direct action phrases; status pills and category toggle descriptions remain accessible. Evidence: #332, #347, #348, #352, `SettingsSectionTextCopyTest`, `ComposeScreenModelsTest`.
- Settings import/source filters: source choices, browser query, thresholds, preset save, and validation toasts now tell users what to do next. Evidence: #294, #301, #310, #323, #343, `SettingsImportFiltersTextCopyTest`.
- Settings note type and field mapping: copy is shorter but still tells the user to choose Kiku or map Anki fields. Evidence: #308, #329, #342, `SettingsNoteTypeTextCopyTest`.
- Settings reference data/rank range/licenses: rank copy and offline-data credits remain explicit and concise. Evidence: #307, #316, #337, `SettingsReferenceDataTextCopyTest`.
- Settings study plan/workload/new-card sort/retention: descriptions make clear that Kani can adjust daily work while Anki due dates stay fixed. Evidence: #324, #326, #327, #334, #338, #349, #351, `SettingsStudyPlanTextCopyTest`.
- Settings learning steps, study-ahead, and ladder movement: copy now distinguishes due-review movement from practice repeats and keeps validation messages short. Evidence: #313, #315, #320, #336, #341, #344, #345, #346, `SettingsLearningTextCopyTest`, `SettingsStudyAheadTextCopyTest`, `SettingsLadderThresholdTextCopyTest`.
- Settings automation, reminders, daily sync, and app updates: copy preserves safety/permission warnings while removing filler. Evidence: #309, #312, #317, #319, #330, #339, #340, `SettingsAutomationTextCopyTest`.
- Stats/history and progress surfaces: the Stats queue item is already checked off and the centralized stats/timeline strings remain concise. Evidence: README queue state, `StatsTextCopyTest`, `TimelineCopyTest`.
- Games and other lightweight surfaces: centralized game copy remains short and test-covered; no behavior or layout change was needed for this release. Evidence: `KanjiGameCopyTest`.

## Final decision

The copy/UX item is accepted as complete for this Cheap Ralph cycle. Remaining product work should continue from the next README queue items (Japanese translation, then the long-term Scheduler / Evidence / Habit / Similar-writing / Screenshot-loop plans) rather than opening more tiny Settings-copy PRs.

No live AnkiDroid/provider release gate is required for the copy/docs-only finalization itself, but the release workflow must still publish through the normal Android Release gate before a release is considered cut.

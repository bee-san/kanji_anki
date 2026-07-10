# Deep Review: Study/Flashcard Screen UI/UX Declutter

Date: 2026-07-10. Reviewed at current `main` working tree. All file:line
references verified against source during this review. Decisions below were
confirmed with the product owner (see "Locked decisions").

## The complaint

"The actual flashcard screen feels like a lot is going on."

That instinct is measurable. This document inventories what one review
actually renders, names the clutter drivers, and defines a four-phase plan.
Each phase is independently shippable (note: every merge to `main` auto-cuts
a release, so phases land as their own releases).

## Locked decisions

1. **Hide the bottom nav during an active study session.** Top-bar Close is
   the exit.
2. **Answer details collapse into a single "More about 〇" disclosure**,
   collapsed by default; empty sections never render.
3. **Scope is all study renderers** (flashcard, typing, choice, writing), not
   just the flashcard rungs.
4. **Fail/Pass buttons adopt coral/teal semantic colors**, matching the
   existing swipe-tint semantics.

## What one `kanji_meaning` review renders today (verified)

Front (before reveal), top to bottom:

| # | Element | Source |
|---|---------|--------|
| 1 | Close pill (56dp) · "3 / 20" (18sp bold) · 7dp progress bar · Settings pill (56dp) | `StudyTopBarCompose.kt:66-110` |
| 2 | Eye-icon pill "Recognise" (44dp, 18sp bold) | `MainActivityStudyFlashcardContentCompose.kt:196-223` |
| 3 | "Name this kanji" (21sp bold) | `...FlashcardContentCompose.kt:166-172` |
| 4 | "What does this kanji mean?" (27sp bold) | `...FlashcardContentCompose.kt:175-181` |
| 5 | "Answer hidden until reveal" (14sp muted) | `...FlashcardContentCompose.kt:182-191` |
| 6 | Kanji hero panel (bordered, min 210dp, 116sp glyph) | `...FlashcardContentCompose.kt:253-286` |
| 7 | Pinned "Reveal" button (62dp) | `MainActivityStudyFlashcardCompose.kt:117-123` |
| 8 | Bottom nav Home/Study/Stats/Settings (~76dp) | `MainActivityShell.kt`, `KaniBottomNavCompose.kt` |

Back (after reveal): the header rows 2–4 **stay** (the question remains after
it has been answered), the 116sp hero **unmounts**, and the kanji re-appears
at 76sp left-aligned inside an "Answer" panel
(`MainActivityStudyFlashcardContentCompose.kt:89-111`,
`MainActivityStudyAnswerCompose.kt:121-206`), followed by **five
always-rendered accordion cards** (Details / Breakdown / Stroke order /
Used in Anki / Why this card — rendered even when their content state is
EMPTY/UNAVAILABLE, `MainActivityStudyAnswerDetailsCompose.kt:149-221`), then a
"View kanji details" caption + "Open in Browse" button
(`MainActivityStudyAnswerCompose.kt:189-203`), then pinned Fail/Pass.

## Core problems

1. **The task is restated 3–4× on every card.** Pill "Recognise" + "Name this
   kanji" + "What does this kanji mean?" + "Answer hidden until reveal" all
   say one thing (`MainActivityStudyFlashcardContentCompose.kt:163-191`).
   Worst offenders:
   - Typing card says "meaning" **4×**: title, question, field label, field
     placeholder (`MainActivityStudyTypingAnswerCompose.kt`).
   - Writing card restates 4×: title 30sp, task label 16sp, "Writing" section
     header 22sp, instruction line
     (`MainActivityStudyWritingPromptCompose.kt`,
     `MainActivityStudyWritingChromeCompose.kt`).
   - Choice cards restate 3×: title 30sp + body + question
     (`MainActivityStudyChoiceCompose.kt`).
2. **The reveal breaks continuity and buries the answer.** The kanji jumps
   position/size (116sp centered → 76sp left-aligned), the stale question
   stays on top, and ~220dp of accordion chrome renders below every answer —
   including empty placeholders like "Stroke data is not available for this
   kanji yet". The thing the learner wants after reveal — meaning/reading —
   competes with 7 secondary blocks.
3. **Visual-system noise.** 18 distinct text sizes in use across study
   screens (13/14/15/16/17/18/19/21/22/27/30/34/40/44/64/72/76/116 sp),
   11 corner radii (12–32 + 999), four elevations (0/3/5/8dp), 1dp borders on
   nearly every surface (choice screens nest **4 bordered cards** deep:
   `MainActivityStudyChoiceCompose.kt:250-303` outer card → inset panel →
   answer panel → accordions), and two different mode-pill designs for the
   same semantic element (flashcard `RecognitionPill` 44dp/18sp/borderless vs
   choice/writing bordered 13sp pill).
4. **Three chrome bars during a focus flow.** Top bar + pinned action bar +
   bottom nav. The bottom nav duplicates escape hatches the top bar already
   has (Close, Settings).
5. **Inconsistencies.**
   - Choice rungs render **no top bar at all** — no progress/close/settings
     during choice cards (`MainActivityStudyChoiceSessions.kt:138-149`).
   - The choice result's advance button is labeled "Pass"/"Fail" instead of
     "Continue" (`MainActivityStudyChoiceSessions.kt:124`).
   - Swipe feedback is teal/coral but the Pass/Fail buttons are
     pink-primary/outlined — mismatched semantics
     (`MainActivityStudyFlashcardCompose.kt:214-238`,
     `...FlashcardContentCompose.kt:121-147`).
   - The undo banner injects above Fail/Pass and shifts the buttons right as
     the next card appears — mis-tap risk
     (`MainActivityStudyFlashcardCompose.kt:111-116`).
   - Hardcoded English in a bilingual app: "Answer", "Reference", "Trace it
     below, then check." (`MainActivityStudyAnswerCompose.kt:43,53,69`),
     "Show all/Show fewer", "Current", and all empty-state copy
     (`MainActivityStudyAnswerKanjiDetailsModel.kt:15-24`), plus the
     `"Kani shell ${route}"` a11y description (`MainActivityShell.kt:93`).
   - Dead code: `FlashcardPromptHeaderModel.reasonLine` is carried but never
     rendered (`StudyTextCopy.studyReasonLine` always returns "",
     `core/.../StudyTextCopy.kt:524-533`); `SimilarChoiceActionBar` is defined
     but never called (`MainActivityStudyChoiceCompose.kt:131`).

---

## Phase 1 — Kill the redundant prompt scaffolding (all renderers)

One header pattern everywhere: **small mode chip + one question line**.
Nothing else above the hero.

| File | Change |
|---|---|
| `MainActivityStudyFlashcardContentCompose.kt` | `FlashcardPromptHeader`: render chip + question only; delete the 21sp title and the "Answer hidden until reveal" hint renders |
| `MainActivityStudyFlashcardContentModel.kt` | Drop `title`, `hiddenHint`, `reasonLine` from `FlashcardPromptHeaderModel` (`reasonLine` is already dead) |
| `MainActivityStudyFlashcard.kt` | Stop building the removed fields |
| `MainActivityStudyTypingAnswerCompose.kt` | Delete the "Meaning" field label; keep the placeholder (kills the 4× restatement) |
| `MainActivityStudyChoiceCompose.kt` | `MeaningChoiceSessionCard` / `SimilarChoiceSessionCard`: delete the 30sp title + body line; chip + in-panel question only |
| `MainActivityStudyWritingPromptCompose.kt`, `MainActivityStudyWritingChromeCompose.kt` | Delete the 16sp task label and the 22sp "Writing" section header; keep title + instruction |
| core copy objects + tests | Retire `StudyTextCopy.answerHiddenHint()`, choice title/body helpers no longer rendered; update copy tests |

Net effect: ~90–140dp returned to the kanji on every card.

## Phase 2 — Fix the reveal

- **Hero continuity** (`FlashcardCard`,
  `MainActivityStudyFlashcardContentCompose.kt:49-114`): the kanji hero stays
  mounted through reveal, animating 116sp → ~80sp in place; the question line
  hides once answered. Answer meaning/reading lines render directly beneath
  the hero.
- **Answer panel** (`MainActivityStudyAnswerCompose.kt`): the flashcard path
  drops the "Answer" title and the duplicate 76sp glyph row. The writing rung
  keeps its "Reference" glyph — that glyph is the trace target, not
  duplication.
- **Details disclosure** (`MainActivityStudyAnswerDetailsCompose.kt`,
  `MainActivityStudyAnswerKanjiDetailsModel.kt`): one collapsed
  **"More about 〇"** disclosure; only content-bearing sections render inside
  (EMPTY/UNAVAILABLE never render); "Open in Browse" moves inside it; delete
  the "View kanji details" caption. Preserve existing test tags
  (`studyAnswerAccordionHeaderTestTag`, used-in-anki row/toggle tags) for
  instrumentation tests.

## Phase 3 — Visual system diet

- `MainActivityUiTokens.kt`: study type scale (~6 sizes replacing 18), radius
  scale (12/20/28), one elevation (0dp — choice 5dp and writing 8dp shadows
  removed).
- One shared `StudyModeChip` composable replacing the two pill designs
  (`RecognitionPill` and the choice/writing bordered pill).
- Border diet: outer card + interactive elements only; accordions, chips, and
  metric cards switch to fill-contrast (`panelSoft`/`panelFill`); choice
  nesting goes from 4 bordered levels to 2.
- **Fail = coral fill, Pass = teal fill** (`StudyAgainButton` /
  `StudyGoodButton` in `MainActivityStudyFlashcardCompose.kt`, writing primary
  actions in `MainActivityStudyWritingPrimaryActionsCompose.kt`, choice result
  bar in `MainActivityStudyChoiceResultCompose.kt`), matching the existing
  swipe-tint semantics. Verify text contrast per theme palette
  (`KaniTheme.kt`, `KaniMoreThemePalettes.kt`).

## Phase 4 — Chrome & flow

- **Hide the bottom nav during an active study session**
  (`MainActivityShellHost.kt` / `MainActivityShell.kt`), extending the
  existing `studyCardKeyboardResident` hide path used by unrevealed typing
  cards.
- Prerequisite: **add `StudyTopBar` to the choice routes**
  (`MainActivityStudyChoiceSessions.kt:138-149` and the similar-kanji route)
  so progress/Close/Settings exist on every study render before the nav
  disappears.
- Choice result advance button → `StudyTextCopy.continueLabel()` instead of
  "Pass"/"Fail".
- Undo gets a fixed-height slot in the action-bar area so Fail/Pass never
  shift position between cards.
- Localize the hardcoded strings ("Answer", "Reference", "Trace it below,
  then check.", "Show all/Show fewer", "Current", empty-state copy) into core
  copy objects with the standard `localizedText` EN/JA pattern.

## Verification & risks

- Per phase: `./gradlew ciFast`; update Robolectric/copy/model tests; keep
  test tags stable for instrumentation tests.
- Visuals: GitHub Actions screenshot workflow (ralph loop,
  `docs/ralph-ui-loop/runbook.md`) for before/after evidence. Note the theme
  screenshot harness currently captures the study route in its empty state;
  card-state visual checks go through the ralph loop or a manual emulator run.
- No provider/sync code is touched, so the live AnkiDroid gate is **not**
  required (per AGENTS.md release rules).
- **Preserve**: the typing-card KB1 compact/IME behavior
  (`studyCardImeCompact`), swipe gestures and haptics, a11y content
  descriptions and progress semantics, single-expand accordion state keyed by
  `panelStateKey`.
- Every merge to `main` auto-cuts a release; phases are ordered to be safe
  standalone.
